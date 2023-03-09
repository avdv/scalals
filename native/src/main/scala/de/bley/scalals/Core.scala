package de.bley.scalals

import java.time.Instant

import scala.collection.mutable
import java.io.IOException

import scalanative.libc.errno
import scalanative.unsafe.*
import scalanative.unsigned.*
import scalanative.posix.errno.*
import scalanative.posix.sys.stat
import java.nio.file.Path

object FileInfo:
  // FIXME: crashes with a scala.scalanative.runtime.UndefinedBehaviorError
  // val lookupService = FileSystems.getDefault.getUserPrincipalLookupService

  def apply(path: Path, dereference: Boolean)(implicit z: Env) =
    val info =
      val buf = alloc[stat.stat]()
      val err =
        if dereference then stat.stat(toCString(path.toString), buf)
        else stat.lstat(toCString(path.toString), buf)

      if err == 0 then buf
      else
        errno.errno match
          case e if e == ENOENT => throw new IOException("No such file or directory")
          case e if e == EACCES => throw new IOException("Permission denied")
          case _                => throw new IOException("I/O error")
      end if
    end info
    new FileInfo(path, toCString(path.getFileName.toString), info)
  end apply
end FileInfo

final class FileInfo private (val path: Path, val cstr: CString, private val info: Ptr[stat.stat])
    extends generic.FileInfo:
  import scalanative.posix.{ grp, pwd }
  // FIXME: reimplement stat with better resolution
  // import scalanative.posix.timeOps._
  import scalanative.libc.errno

  val name = fromCString(cstr)

  @inline def isDirectory: Boolean = stat.S_ISDIR(info._13) != 0
  @inline def isRegularFile: Boolean = stat.S_ISREG(info._13) != 0
  @inline def isSymlink: Boolean = stat.S_ISLNK(info._13) != 0
  @inline def isPipe: Boolean = stat.S_ISFIFO(info._13) != 0
  @inline def isSocket: Boolean = stat.S_ISSOCK(info._13) != 0
  @inline def isCharDev: Boolean = stat.S_ISCHR(info._13) != 0
  @inline def isBlockDev: Boolean = stat.S_ISBLK(info._13) != 0
  @inline def group: String =
    val buf = stackalloc[grp.group]()
    errno.errno = 0
    val err = grp.getgrgid(info._5, buf)
    if err == 0 then fromCString(buf._1)
    else if errno.errno == 0 then info._5.toString
    else throw new IOException(s"$path: ${errno.errno}")
  end group
  @inline def owner: String =
    val buf = stackalloc[pwd.passwd]()
    errno.errno = 0
    val err = pwd.getpwuid(info._4, buf)
    if err == 0 then fromCString(buf._1)
    else if errno.errno == 0 then info._4.toString
    else throw new IOException(s"$path: ${errno.errno}")
  end owner
  @inline def permissions: Int = info._13.toInt
  @inline def size: Long = info._6
  // Still not fixed in scala-native: timestamps only have seconds resolution
  @inline def lastModifiedTime: Instant = Instant.ofEpochSecond(info._8)
  @inline def lastAccessTime: Instant = Instant.ofEpochSecond(info._7)
  @inline def creationTime: Instant = Instant.ofEpochSecond(info._9)
  @inline def isExecutable =
    import scala.scalanative.unsigned.*
    (info._13 & (stat.S_IXGRP | stat.S_IXOTH | stat.S_IXUSR)) != 0.toUInt
end FileInfo

object Core extends generic.Core:
  import scala.scalanative.posix.sys.stat.*
  import scalanative.unsafe.*

  locale.setlocale(locale.LC_ALL, c"")

  private val sb = new StringBuilder(3 * 3)

  protected val orderByName = Ordering
    .fromLessThan[FileInfo]((a, b) => locale.strcoll(a.cstr, b.cstr) < 0)
    .asInstanceOf[Ordering[generic.FileInfo]]

  final override def permissionString(imode: Int): String =
    import scala.scalanative.unsigned.*
    val mode = imode.toUInt

    sb.clear()
    format((mode & S_IRUSR).toInt, (mode & S_IWUSR).toInt, (mode & S_IXUSR).toInt, (mode & S_ISUID).toInt != 0, 's', sb)
    format((mode & S_IRGRP).toInt, (mode & S_IWGRP).toInt, (mode & S_IXGRP).toInt, (mode & S_ISGID).toInt != 0, 's', sb)
    format((mode & S_IROTH).toInt, (mode & S_IWOTH).toInt, (mode & S_IXOTH).toInt, (mode & S_ISVTX).toInt != 0, 't', sb)
    sb.toString()
  end permissionString

  @inline def timing[T](marker: String)(body: => T): T =
    // val start = System.nanoTime
    val r = body
    // val end = System.nanoTime
    // Console.err.println(marker + " " + (end - start).toString)
    r
  end timing

  val date = new Decorator:
    private val cache = mutable.LongMap.empty[String]

    override def decorate(file: generic.FileInfo, builder: StringBuilder): Int =
      val instant = file.lastModifiedTime.toEpochMilli()

      val date = cache.getOrElseUpdate(
        instant / 1000, {
          import scalanative.unsafe.*
          import scalanative.posix.time

          Zone { implicit z =>
            val format = if instant > recentLimit then c"%b %e %R" else c"%b %e  %Y"
            val str = alloc[CChar](70)
            val time_t = stackalloc[time.time_t]()

            !time_t = file.lastModifiedTime.toEpochMilli() / 1000

            val tm = time.localtime(time_t)
            if 0.toULong == time.strftime(str, 70.toULong, format, tm) then "n/a" // buffer to small
            else fromCString(str)
          }
        },
      )
      builder.append(date)
      date.length
    end decorate
end Core
