package de.bley.scalals
package generic

import java.nio.file.Files
import java.io.IOException

trait Core {
  protected def format(r: Int, w: Int, x: Int, special: Boolean, ch: Char, builder: StringBuilder): Unit = {
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

  def permissionString(imode: Int): String

  def ls(config: Config): Unit

  protected def groupDirsFirst(underlying: Ordering[generic.FileInfo]): Ordering[generic.FileInfo] =
    new Ordering[FileInfo] {
      override def compare(a: generic.FileInfo, b: generic.FileInfo): Int = {
        if (a.isDirectory == b.isDirectory) {
          underlying.compare(a, b)
        } else {
          if (a.isDirectory) -1 else 1
        }
      }
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
      val output =
        for {
          fileInfo <- listingBuffer.toVector
          decorator <- decorators
          builder = new StringBuilder()
        } yield {
          decorator.decorate(fileInfo, builder) -> builder
        }

      val sizes = output.map(_._1)
      //val minlen = sizes.min
      val columns =
        if (config.long || config.oneLine) decorators.size
        else {
          val terminalMax = Terminal.width
          (terminalMax / (sizes.max + 1)) max 1
        }
      val maxColSize = {
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
      for {
        record <- output.grouped(columns)
      } {
        var i = 0
        println(
          record
            .reduceLeft[(Int, StringBuilder)] { case ((width, builder), (width2, builder2)) =>
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

  //lazy val dateFormat = DateTimeFormatter.ofPattern("MMM ppd  yyyy", Locale.getDefault())
  //val dateFormat = new SimpleDateFormat() //.ofPattern("MMM ppd  yyyy", Locale.getDefault())
  //lazy val recentFormat = DateTimeFormatter.ofPattern("MMM ppd HH:mm", Locale.getDefault())
  //lazy val currentZone = ZoneId.systemDefault()
  val halfayear: Long = ((31556952L / 2) * 1000)
  val recentLimit = System.currentTimeMillis - halfayear
  //Instant.now.minusSeconds(31556952 / 2).toEpochMilli()

  def date: Decorator

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
          val owner =
            try {
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
          val group =
            try {
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
