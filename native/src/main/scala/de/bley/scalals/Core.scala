package de.bley.scalals

import java.nio.file.{ FileSystems, Files, NoSuchFileException, Path, Paths }
import java.time.Instant

import scala.collection.mutable
import java.io.IOException

import scalanative.unsafe._
import scalanative.posix.sys.stat

object FileInfo {
  val lookupService = FileSystems.getDefault.getUserPrincipalLookupService

  def apply(path: Path, dereference: Boolean)(implicit z: Zone) = {
    val info = {
      val buf = alloc[stat.stat]
      val err =
        if (dereference)
          stat.stat(toCString(path.toString), buf)
        else
          stat.lstat(toCString(path.toString), buf)

      if (err == 0) buf
      else throw new IOException(s"could not stat $path") // TODO use errno
    }
    new FileInfo(path, toCString(path.getFileName.toString), info)
  }
}

final class FileInfo private (val path: Path, val cstr: CString, private val info: Ptr[stat.stat])
    extends generic.FileInfo {
  import scalanative.posix.{ grp, pwd }
  import scalanative.posix.sys.statOps._
  import scalanative.posix.timeOps._
  import scalanative.libc.errno

  val name = fromCString(cstr)

  @inline def isDirectory: Boolean = stat.S_ISDIR(info.st_mode) != 0
  @inline def isRegularFile: Boolean = stat.S_ISREG(info.st_mode) != 0
  @inline def isSymlink: Boolean = stat.S_ISLNK(info.st_mode) != 0
  @inline def isPipe: Boolean = stat.S_ISFIFO(info.st_mode) != 0
  @inline def isSocket: Boolean = stat.S_ISSOCK(info.st_mode) != 0
  @inline def isCharDev: Boolean = stat.S_ISCHR(info.st_mode) != 0
  @inline def isBlockDev: Boolean = stat.S_ISBLK(info.st_mode) != 0
  @inline def group: String = {
    val buf = stackalloc[grp.group]
    errno.errno = 0
    val err = grp.getgrgid(info.st_gid, buf)
    if (err == 0) {
      fromCString(buf._1)
    } else if (errno.errno == 0) {
      info.st_gid.toString
    } else {
      throw new IOException(s"$path: ${errno.errno}")
    }
  }
  @inline def owner: String = {
    val buf = stackalloc[pwd.passwd]
    errno.errno = 0
    val err = pwd.getpwuid(info.st_uid, buf)
    if (err == 0) {
      fromCString(buf._1)
    } else if (errno.errno == 0) {
      info.st_uid.toString
    } else {
      throw new IOException(s"$path: ${errno.errno}")
    }
  }
  @inline def permissions: Int = info.st_mode.toInt
  @inline def size: Long = info._6
  @inline def lastModifiedTime: Instant = Instant.ofEpochSecond(info.st_mtim.tv_sec, info.st_mtim.tv_nsec)
  @inline def lastAccessTime: Instant = Instant.ofEpochSecond(info.st_atim.tv_sec, info.st_atim.tv_nsec)
  @inline def creationTime: Instant = Instant.ofEpochSecond(info.st_ctim.tv_sec, info.st_ctim.tv_nsec)
  @inline def isExecutable = {
    import scala.scalanative.unsigned._
    (info._13 & (stat.S_IXGRP | stat.S_IXOTH | stat.S_IXUSR)) != 0.toUInt
  }
}

object Core {
  import scala.scalanative.posix.sys.stat._
  import scalanative.unsafe._

  locale.setlocale(locale.LC_ALL, c"")

  private val sb = new StringBuilder(3 * 3)

  private def format(r: Int, w: Int, x: Int, special: Boolean, ch: Char, builder: StringBuilder): Unit = {
    val _ = builder
      .append(if (r == 0) '-' else 'r')
      .append(if (w == 0) '-' else 'w')
      .append(
        if (special) {
          if (x == 0) ch.toUpper else ch
        } else {
          if (x == 0) '-' else 'x'
        }
      )
  }

  private def permissionString(imode: Int): String = {
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
    val (dirPaths, filePaths) = items.partition(Files.isDirectory(_))
    val showPrefix = dirPaths.lengthCompare(1) > 0 || filePaths.nonEmpty
    val decorators = layout(config)

    listAll(list(filePaths.toArray, config), config, decorators)

    for {
      path <- dirPaths
    } {
      if (config.listDirectories && showPrefix) println(s"\uf115 $path:")
      try {
        val entries = for {
          file <- path.toFile.listFiles()
          if config.showAll || !file.isHidden()
        } yield file.toPath

        listAll(list(entries, config), config, decorators)
      } catch {
        case e: NoSuchFileException =>
          Console.err.println(s"scalals: no such file or directory: '${e.getMessage}'")
      }
    }
  }

  def groupDirsFirst(underlying: Ordering[FileInfo]): Ordering[FileInfo] = new Ordering[FileInfo] {
    override def compare(a: FileInfo, b: FileInfo): Int = {
      if (a.isDirectory == b.isDirectory) {
        underlying.compare(a, b)
      } else {
        if (a.isDirectory) -1 else 1
      }
    }
  }

  private def list(items: Array[Path], config: Config)(implicit z: Zone) = {
    implicit val ordering: Ordering[FileInfo] = {
      val orderBy = config.sort match {
        case SortMode.size => Ordering.by((f: FileInfo) => (-f.size, f.name))
        case SortMode.time => Ordering.by((f: FileInfo) => (-f.lastModifiedTime.toEpochMilli(), f.name))
        case SortMode.extension =>
          Ordering.by { (f: FileInfo) =>
            val e = f.name.dropWhile(_ == '.')
            val dot = e.lastIndexOf('.')
            if (dot > 0)
              e.splitAt(dot).swap
            else ("", f.name)
          }
        case _ => Ordering.fromLessThan((a: FileInfo, b: FileInfo) => locale.strcoll(a.cstr, b.cstr) < 0)
      }

      val orderDirection = if (config.reverse) orderBy.reverse else orderBy

      if (config.groupDirectoriesFirst) groupDirsFirst(orderDirection) else orderDirection
    }

    val listingBuffer = scala.collection.mutable.TreeSet.empty[FileInfo]
    for {
      path <- items
    } {
      listingBuffer += FileInfo(path, config.dereference)
    }
    listingBuffer
  }

  def listAll(
      listingBuffer: scala.collection.mutable.Set[FileInfo],
      config: Config,
      decorators: Vector[Decorator]
  ): Unit = {
    //import java.util.{ List => JList }
    //import java.util.function.Supplier
    //scala.collection.mutable.ArrayBuffer.empty[FileInfo]

    //val supplier: Supplier[JList[FileInfo]] = () => listingBuffer.asJava
    //new ArrayList[FileInfo](100)
//    timing("collect"){

    //.collect(Collectors.toCollection[FileInfo, JList[FileInfo]](supplier))
    //Collectors.toList())

//    timing("sort")(listing.sort(if (config.reverse) comparator.reversed() else comparator))
//    val sorted = timing("sort")(listingBuffer.sortWith(if (config.reverse) { (a: FileInfo, b: FileInfo) => !comparator(a, b) } else comparator))

    if (listingBuffer.nonEmpty) {
      val output = timing("decorate") {
        for {
          fileInfo <- listingBuffer.toVector
          decorator <- decorators
          builder = new StringBuilder()
        } yield {
          decorator.decorate(fileInfo, builder) -> builder
        }
      }

      val sizes = output.map(_._1)
      val minlen = sizes.min
      val columns =
        if (config.long || config.oneLine) decorators.size
        else {
          val terminalMax = Terminal.width
          (terminalMax / (sizes.max + 1)) max 1
        }
      val maxColSize = timing("maxColSize") {
        val g = sizes.grouped(columns).toList
        val h = if (sizes.size > columns) {
          g.init :+ (g.last ++ List.fill(columns - g.last.size)(0))
        } else {
          g
        }
        h.transpose.map(_.max)
      }

      //Console.err.println(s"$columns")
      //Console.err.println(maxColSize.mkString(", "))
      timing("output") {
        for {
          record <- output.grouped(columns)
        } {
          var i = 0
          println(
            record
              .reduceLeft[(Int, StringBuilder)] {
                case ((width, builder), (width2, builder2)) =>
                  val colSize = maxColSize(i)
                  i += 1
                  width2 -> builder
                    .append(" " * (colSize - width + 1))
                    .append(builder2)
              }
              ._2
          )
        }
      }
    }
  }

  //lazy val dateFormat = DateTimeFormatter.ofPattern("MMM ppd  yyyy", Locale.getDefault())
  //val dateFormat = new SimpleDateFormat() //.ofPattern("MMM ppd  yyyy", Locale.getDefault())
  //lazy val recentFormat = DateTimeFormatter.ofPattern("MMM ppd HH:mm", Locale.getDefault())
  //lazy val currentZone = ZoneId.systemDefault()
  val halfayear: Long = ((31556952L / 2) * 1000)
  val recentLimit = System.currentTimeMillis - halfayear
  //Instant.now.minusSeconds(31556952 / 2).toEpochMilli()

  def layout(config: Config): Vector[Decorator] = {
//    val ext = file.name.dropWhile(_ == '.').replaceFirst(".*[.]", "").toLowerCase(Locale.ENGLISH)
//
//    val key = if (files.contains(ext)) ext else aliases.getOrElse(ext, "file")v1
//    val symbol = files.getOrElse(key, ' ')

//    val code = Colors.colorFor(file)
//
//    import Console.RESET

    //val output = Seq(IconDecorator & ColorDecorator, IndicatorDecorator).foldLeft(Decorated(0, "")){
    //  case (d, action) ⇒ d |+| action(file)
    //}

//    val decorated = if (config.hyperlink) hyperlink(file.path.toUri.toURL.toString, file.name) else file.name
    val decorator: Decorator = {
      val d = Decorator(
        IconDecorator,
        if (config.hyperlink)
          HyperlinkDecorator(Decorator.name)
        else Decorator.name
      ).colored(config.colorMode)
        .cond(config.indicatorStyle `ne` IndicatorStyle.none)(
          IndicatorDecorator(config.indicatorStyle)
        )

      if (config.showGitStatus) GitDecorator + d else d
    }

    if (config.long) {
      val perms = new Decorator {
        override def decorate(file: FileInfo, builder: StringBuilder): Int = {
          val firstChar =
            if (file.isBlockDev) 'b'
            else if (file.isCharDev) 'c'
            else if (file.isDirectory) 'd'
            else if (file.isSymlink) 'l'
            else if (file.isPipe) 'p'
            else if (file.isSocket) 's'
            else '-'

          builder.append(firstChar).append(permissionString(file.permissions))

          3 * 3 + 1
        }
      }

      val date = new Decorator {
        private val cache = mutable.LongMap.empty[String]

        override def decorate(file: FileInfo, builder: StringBuilder): Int = {
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
                if (0 == time.strftime(str, 70, format, tm))
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

      val fileAndLink = new Decorator {
        override def decorate(file: FileInfo, builder: StringBuilder): Int = {
          val n = decorator.decorate(file, builder)

          if (file.isSymlink) {
            val t = Files.readSymbolicLink(file.path)
            val target = s" → $t"

            builder.append(target)

            n + target.length()
          } else n
        }
      }

      val user = new Decorator {
        override def decorate(file: FileInfo, builder: StringBuilder): Int = {
          val owner = try {
            file.owner
          } catch {
            case e: IOException => "-"
          }

          builder.append(owner)
          owner.length()
        }
      }

      val group = new Decorator {
        override def decorate(file: FileInfo, builder: StringBuilder): Int = {
          val group = try {
            //val group = file.group
            //principalCache.getOrElseUpdate(group, group.getName)
            file.group
          } catch {
            case e: IOException => "-"
          }
          builder.append(group)
          group.length()
        }
      }

      Vector(perms, user, group, new SizeDecorator(config.blockSize), date, fileAndLink)
    } else {
      if (config.printSize) {
        Vector(SizeDecorator(config.blockSize) + decorator)
      } else {
        Vector(decorator)
      }
    }
  }
}
