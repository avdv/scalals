package de.bley.scalals

import java.nio.file.{ Files, NoSuchFileException, Path, Paths }
import java.time.Instant

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import java.io.IOException

import scalanative.libc.errno
import scalanative.unsafe._
import scalanative.unsigned._
import scalanative.posix.errno._
import scalanative.posix.sys.stat
import java.nio.file.LinkOption

object FileInfo {
  // FIXME: crashes with a scala.scalanative.runtime.UndefinedBehaviorError
  //val lookupService = FileSystems.getDefault.getUserPrincipalLookupService

  def apply(path: Path, dereference: Boolean)(implicit z: Zone) = {
    val info = {
      val buf = alloc[stat.stat]
      val err =
        if (dereference)
          stat.stat(toCString(path.toString), buf)
        else
          stat.lstat(toCString(path.toString), buf)

      if (err == 0) buf
      else {
        errno.errno match {
          case e if e == ENOENT => throw new IOException("No such file or directory")
          case e if e == EACCES => throw new IOException("Permission denied")
          case _                => throw new IOException("I/O error")
        }
      }
    }
    new FileInfo(path, toCString(path.getFileName.toString), info)
  }
}

final class FileInfo private (val path: Path, val cstr: CString, private val info: Ptr[stat.stat])
    extends generic.FileInfo {
  import scalanative.posix.{ grp, pwd }
  // FIXME: reimplement stat with better resolution
  //import scalanative.posix.timeOps._
  import scalanative.libc.errno

  val name = fromCString(cstr)

  @inline def isDirectory: Boolean = stat.S_ISDIR(info._13) != 0
  @inline def isRegularFile: Boolean = stat.S_ISREG(info._13) != 0
  @inline def isSymlink: Boolean = stat.S_ISLNK(info._13) != 0
  @inline def isPipe: Boolean = stat.S_ISFIFO(info._13) != 0
  @inline def isSocket: Boolean = stat.S_ISSOCK(info._13) != 0
  @inline def isCharDev: Boolean = stat.S_ISCHR(info._13) != 0
  @inline def isBlockDev: Boolean = stat.S_ISBLK(info._13) != 0
  @inline def group: String = {
    val buf = stackalloc[grp.group]
    errno.errno = 0
    val err = grp.getgrgid(info._5, buf)
    if (err == 0) {
      fromCString(buf._1)
    } else if (errno.errno == 0) {
      info._5.toString
    } else {
      throw new IOException(s"$path: ${errno.errno}")
    }
  }
  @inline def owner: String = {
    val buf = stackalloc[pwd.passwd]
    errno.errno = 0
    val err = pwd.getpwuid(info._4, buf)
    if (err == 0) {
      fromCString(buf._1)
    } else if (errno.errno == 0) {
      info._4.toString
    } else {
      throw new IOException(s"$path: ${errno.errno}")
    }
  }
  @inline def permissions: Int = info._13.toInt
  @inline def size: Long = info._6
  // Still not fixed in scala-native: timestamps only have seconds resolution
  @inline def lastModifiedTime: Instant = Instant.ofEpochSecond(info._8)
  @inline def lastAccessTime: Instant = Instant.ofEpochSecond(info._7)
  @inline def creationTime: Instant = Instant.ofEpochSecond(info._9)
  @inline def isExecutable = {
    import scala.scalanative.unsigned._
    (info._13 & (stat.S_IXGRP | stat.S_IXOTH | stat.S_IXUSR)) != 0.toUInt
  }
}

object Core extends generic.Core {
  import scala.scalanative.posix.sys.stat._
  import scalanative.unsafe._

  locale.setlocale(locale.LC_ALL, c"")

  private val sb = new StringBuilder(3 * 3)

  final override def permissionString(imode: Int): String = {
    import scala.scalanative.unsigned._
    val mode = imode.toUInt

    sb.clear()
    format((mode & S_IRUSR).toInt, (mode & S_IWUSR).toInt, (mode & S_IXUSR).toInt, (mode & S_ISUID).toInt != 0, 's', sb)
    format((mode & S_IRGRP).toInt, (mode & S_IWGRP).toInt, (mode & S_IXGRP).toInt, (mode & S_ISGID).toInt != 0, 's', sb)
    format((mode & S_IROTH).toInt, (mode & S_IWOTH).toInt, (mode & S_IXOTH).toInt, (mode & S_ISVTX).toInt != 0, 't', sb)
    sb.toString()
  }

  @inline def timing[T](marker: String)(body: => T): T = {
    //val start = System.nanoTime
    val r = body
    //val end = System.nanoTime
    //Console.err.println(marker + " " + (end - start).toString)
    r
  }

  def ls(config: Config) = Zone { implicit z =>
    val items = if (config.paths.isEmpty) List(Paths.get(".")) else config.paths
    val (dirPaths, filePaths) = items.partition(Files.isDirectory(_, LinkOption.NOFOLLOW_LINKS))
    val showPrefix = dirPaths.lengthCompare(1) > 0 || filePaths.nonEmpty
    val decorators = layout(config)

    listAll(list(filePaths.toArray, config), config, decorators)

    for {
      path <- dirPaths
    } {
      if (config.listDirectories && showPrefix) println(s"\uf115 $path:")
      try {
        val entries = for {
          path <- Files.newDirectoryStream(path).asScala if config.showAll || !Files.isHidden(path)
        } yield path

        listAll(list(entries.toArray, config), config, decorators)
      } catch {
        case e: NoSuchFileException =>
          Console.err.println(s"scalals: no such file or directory: '${e.getMessage}'")
      }
    }
  }

  private def list(items: Array[Path], config: Config)(implicit z: Zone) = {
    implicit val ordering: Ordering[generic.FileInfo] = {
      val orderBy = config.sort match {
        case SortMode.size => Ordering.by((f: generic.FileInfo) => (-f.size, f.name))
        case SortMode.time => Ordering.by((f: generic.FileInfo) => (-f.lastModifiedTime.toEpochMilli(), f.name))
        case SortMode.extension =>
          Ordering.by { (f: generic.FileInfo) =>
            val e = f.name.dropWhile(_ == '.')
            val dot = e.lastIndexOf('.')
            if (dot > 0)
              e.splitAt(dot).swap
            else ("", f.name)
          }
        case _ =>
          Ordering
            .fromLessThan((a: FileInfo, b: FileInfo) => locale.strcoll(a.cstr, b.cstr) < 0)
            .asInstanceOf[Ordering[generic.FileInfo]]
      }

      val orderDirection = if (config.reverse) orderBy.reverse else orderBy

      if (config.groupDirectoriesFirst) groupDirsFirst(orderDirection) else orderDirection
    }

    val listingBuffer = scala.collection.mutable.TreeSet.empty[generic.FileInfo]
    for {
      path <- items
    } try {
      listingBuffer += FileInfo(path, config.dereference)
    } catch {
      case e: IOException => Console.err.println(s"scalals: cannot access '$path': ${e.getMessage}")
    }
    listingBuffer
  }

  val date = new Decorator {
    private val cache = mutable.LongMap.empty[String]

    override def decorate(file: generic.FileInfo, builder: StringBuilder): Int = {
      val instant = file.lastModifiedTime.toEpochMilli()

      val date = cache.getOrElseUpdate(
        instant / 1000, {
          import scalanative.unsafe._
          import scalanative.posix.time

          Zone { implicit z =>
            val format = if (instant > recentLimit) c"%b %e %R" else c"%b %e  %Y"
            val str = alloc[CChar](70)
            val time_t = stackalloc[time.time_t]

            !time_t = file.lastModifiedTime.toEpochMilli() / 1000

            val tm = time.localtime(time_t)
            if (0.toULong == time.strftime(str, 70.toULong, format, tm))
              "n/a" // buffer to small
            else
              fromCString(str)
          }
        }
      )
      builder.append(date)
      date.length
    }
  }

}
