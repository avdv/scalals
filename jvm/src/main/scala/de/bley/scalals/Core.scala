package de.bley.scalals

import java.nio.file.attribute._
import java.nio.file.{ Files, LinkOption, Path }
import java.time.{ Instant, ZoneId }
import java.time.format.DateTimeFormatter
import java.nio.file.{ NoSuchFileException, Paths }
import java.io.IOException
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

  def ls(config: de.bley.scalals.Config): Unit = {
    val items = if (config.paths.isEmpty) List(Paths.get(".")) else config.paths
    val (dirPaths, filePaths) = items.partition(Files.isDirectory(_))
    val showPrefix = dirPaths.lengthCompare(1) > 0 || filePaths.nonEmpty
    val decorators = layout(config)

    listAll(list(filePaths.toArray, config), config, decorators)

    for {
      path <- dirPaths
    } {
      if (config.listDirectories && showPrefix) println(s"\uf115 $path:")
      try {
        val entries = new mutable.ArrayBuffer[Path](64)
        Files
          .newDirectoryStream(
            path,
            { p => config.showAll || !Files.isHidden(p) }
          )
          .forEach(entries.addOne)

        listAll(list(entries.toArray, config), config, decorators)
      } catch {
        case e: NoSuchFileException =>
          Console.err.println(s"scalals: no such file or directory: '${e.getMessage}'")
      }
    }
  }

  private val collator = Collator.getInstance

  private def list(items: Array[Path], config: Config) = {
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
            .fromLessThan((a: FileInfo, b: FileInfo) => collator.compare(a.name, b.name) < 0)
            .asInstanceOf[Ordering[generic.FileInfo]]
      }

      val orderDirection = if (config.reverse) orderBy.reverse else orderBy

      if (config.groupDirectoriesFirst) groupDirsFirst(orderDirection) else orderDirection
    }

    val listingBuffer = scala.collection.mutable.TreeSet.empty[generic.FileInfo]
    for {
      path <- items
    } try {
      listingBuffer += FileInfo(path)(config.dereference)
    } catch {
      case e: IOException => Console.err.println(s"scalals: cannot access '$path': ${e.getMessage}")
    }
    listingBuffer
  }

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
}

final case class FileInfo(path: Path)(dereference: Boolean) extends generic.FileInfo {
  import UnixConstants._

  private val attributes = if (dereference)
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
