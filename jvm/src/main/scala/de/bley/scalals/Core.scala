package de.bley.scalals

import java.nio.file.attribute._
import java.nio.file.{ Files, LinkOption, Path }
import java.time.{ Instant, ZoneId }
import java.time.format.DateTimeFormatter
import java.text.Collator

import scala.jdk.CollectionConverters._
import scala.util.chaining._
import scala.util.Try
import scala.collection.mutable

object Core extends generic.Core {
  def date: de.bley.scalals.Decorator = new Decorator {
    private val cache = mutable.LongMap.empty[String]
    private val recentFormat = DateTimeFormatter.ofPattern("MMM ppd hh:mm").withZone(ZoneId.systemDefault())
    private val dateFormat = DateTimeFormatter.ofPattern("MMM ppd  yyyy").withZone(ZoneId.systemDefault())

    override def decorate(file: generic.FileInfo, builder: StringBuilder): Int = {
      val instant = file.lastModifiedTime.toEpochMilli()

      val date = cache.getOrElseUpdate(
        instant / 1000, {
          val format = if (instant > recentLimit) recentFormat else dateFormat

          format.format(file.lastModifiedTime)
        }
      )
      builder.append(date)
      date.length
    }
  }

  private val collator = Collator.getInstance

  protected val orderByName = Ordering
    .fromLessThan((a: FileInfo, b: FileInfo) => collator.compare(a.name, b.name) < 0)
    .asInstanceOf[Ordering[generic.FileInfo]]

  private val sb = new StringBuilder(3 * 3)

  def permissionString(mode: Int): String = {
    import UnixConstants._

    sb.clear()
    format((mode & S_IRUSR).toInt, (mode & S_IWUSR).toInt, (mode & S_IXUSR).toInt, (mode & S_ISUID).toInt != 0, 's', sb)
    format((mode & S_IRGRP).toInt, (mode & S_IWGRP).toInt, (mode & S_IXGRP).toInt, (mode & S_ISGID).toInt != 0, 's', sb)
    format((mode & S_IROTH).toInt, (mode & S_IWOTH).toInt, (mode & S_IXOTH).toInt, (mode & S_ISVTX).toInt != 0, 't', sb)
    sb.toString()
  }
}

object Terminal {
  import sys.process._

  val isTTYOutput: Boolean = System.console != null
  val width: Int = {
    sys.env.get("COLUMNS").orElse(Try("tput cols".!!).toOption).flatMap(_.toIntOption).getOrElse(80)
  }
}

object FileInfo {

  private val executableBits =
    Set(PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OTHERS_EXECUTE)

  private val modeField =
    Class.forName("sun.nio.fs.UnixFileAttributes").getDeclaredField("st_mode").tap(_.setAccessible(true))

  private def mode(attr: PosixFileAttributes) = modeField.getInt(attr)

  def apply(path: Path, dereference: Boolean): FileInfo = new FileInfo(path, dereference)
}

final class FileInfo private (val path: Path, dereference: Boolean) extends generic.FileInfo {
  import UnixConstants._

  private val attributes =
    if (dereference)
      Files.readAttributes(path, classOf[PosixFileAttributes])
    else
      Files.readAttributes(path, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

  val name = path.getFileName.toString

  @inline def isDirectory: Boolean = attributes.isDirectory
  @inline def isRegularFile: Boolean = attributes.isRegularFile
  @inline def isSymlink: Boolean = attributes.isSymbolicLink
  @inline def group: String = attributes.group().getName
  @inline def owner: String = attributes.owner().getName
  @inline def permissions: Int = FileInfo.mode(attributes)
  @inline def size: Long = attributes.size()
  @inline def lastModifiedTime: Instant = attributes.lastModifiedTime().toInstant
  @inline def lastAccessTime: Instant = attributes.lastAccessTime().toInstant
  @inline def creationTime: Instant = attributes.creationTime().toInstant
  @inline def isExecutable = attributes.permissions().asScala.exists(FileInfo.executableBits)
  @inline def isBlockDev: Boolean = (permissions & S_IFMT) == S_IFBLK
  @inline def isCharDev: Boolean = (permissions & S_IFMT) == S_IFCHR
  @inline def isPipe: Boolean = (permissions & S_IFMT) == S_IFIFO
  @inline def isSocket: Boolean = (permissions & S_IFMT) == S_IFSOCK
}
