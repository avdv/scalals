package de.bley.scalals
package generic

import java.nio.file.Files
import java.io.IOException
import java.nio.file.{ Path, Paths }
import java.nio.file.LinkOption
import java.nio.file.{ AccessDeniedException, NoSuchFileException }
import java.time.{ Instant, ZoneId }
import java.time.format.DateTimeFormatter

import scala.annotation.unused
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

trait Core:
  protected def format(r: Int, w: Int, x: Int, special: Boolean, ch: Char, builder: StringBuilder): Unit =
    val _ = builder
      .append(if r == 0 then '-' else 'r')
      .append(if w == 0 then '-' else 'w')
      .append(
        if special then if x == 0 then ch.toUpper else ch
        else if x == 0 then '-'
        else 'x'
      )
  end format

  protected def orderByName: Ordering[FileInfo]

  def permissionString(imode: Int): String

  def ls(config: Config) = Env { implicit z =>
    val items = if config.paths.isEmpty then List(Paths.get(".")) else config.paths

    if config.tree then tree(config, items)
    else lsNormal(config, items)
  }

  private def lsNormal(config: Config, items: List[Path]) = Env { implicit z =>
    val decorators = layout(config)

    if config.listDirectories then
      val linkOptions =
        Option.unless(config.dereference || config.dereferenceArgs || config.dereferenceArgsToDirectory)(
          LinkOption.NOFOLLOW_LINKS
        )
      val (dirPaths, filePaths) = items.partition(Files.isDirectory(_, linkOptions.toSeq*))

      listAll(list(filePaths, config), config, decorators)

      val showPrefix = dirPaths.lengthCompare(1) > 0 || filePaths.nonEmpty

      for path <- dirPaths
      do
        if showPrefix then println(s"\uf115  $path:")

        Using(Files.newDirectoryStream(path)) { dirstream =>
          val entries = for path <- dirstream.asScala if config.showAll || !Files.isHidden(path)
          yield path

          listAll(list(entries, config), config, decorators)
        } recover:
          case e: NoSuchFileException =>
            Console.err.println(s"scalals: no such file or directory: '${e.getMessage}'")
          case e: AccessDeniedException =>
            Console.err.println(s"scalals: access denied: '${e.getMessage()}'")
          case e =>
            Console.err.println(s"scalals: error $e")
      end for
    else listAll(list(items, config), config, decorators)
    end if
  }

  protected def traverse(
      fileInfo: FileInfo,
      config: Config,
      prefix: String = "",
      subdirPrefix: String = "",
      depth: Int = 0,
  )(using @unused z: Env): Unit =
    val keepGoing = !config.maxDepth.exists(depth > _)

    if keepGoing then
      val decorators = layout(config)
      val builder = StringBuilder(prefix)
      val columns = decorators.size

      for (decorator, idx) <- decorators.zipWithIndex
      do
        val _ = decorator.decorate(fileInfo, builder)
        if idx < columns - 1 then builder += ' '

      println(builder)

      if fileInfo.isDirectory then
        Using(Files.newDirectoryStream(fileInfo.path)) { dirstream =>
          val entries = for path <- dirstream.asScala if config.showAll || !Files.isHidden(path)
          yield path

          val items = list(entries, config)

          if items.nonEmpty then
            for fileInfo <- items.init
            do traverse(fileInfo, config, subdirPrefix + " ├── ", subdirPrefix + " │   ", depth + 1)

            traverse(items.last, config, subdirPrefix + " └── ", subdirPrefix + "     ", depth + 1)
          end if
        }.failed.foreach:
          case e: NoSuchFileException =>
            Console.err.println(s"scalals: no such file or directory: '${e.getMessage}'")
          case e: AccessDeniedException =>
            Console.err.println(s"scalals: access denied: '${e.getMessage()}'")
          case e =>
            Console.err.println(s"scalals: ${fileInfo.path}: error $e - ${e.getCause}")
      end if
    end if
  end traverse

  protected def tree(config: Config, items: List[Path])(using @unused z: Env) =
    val dereference = config.dereference || config.dereferenceArgs
    for path <- items
    do
      try
        val dereferenceItem = dereference || (config.dereferenceArgsToDirectory && Files.isDirectory(path))
        traverse(FileInfo(path, dereferenceItem), config)
      catch case e: IOException => Console.err.println(s"scalals: cannot access '$path': ${e.getMessage}")
  end tree

  protected def orderingFor(config: Config) =
    val orderBy = config.sort match
      case SortMode.size => Ordering.by((f: generic.FileInfo) => (-f.size, f.name))
      case SortMode.time => Ordering.by((f: generic.FileInfo) => (-f.lastModifiedTime.toEpochMilli(), f.name))
      case SortMode.extension =>
        Ordering.by { (f: generic.FileInfo) =>
          val e = f.name.dropWhile(_ == '.')
          val dot = e.lastIndexOf('.')
          if dot > 0 then e.splitAt(dot).swap
          else ("", f.name)
        }
      case _ => orderByName

    val orderDirection = if config.reverse then orderBy.reverse else orderBy

    if config.groupDirectoriesFirst then groupDirsFirst(orderDirection) else orderDirection
  end orderingFor

  protected def list(items: IterableOnce[Path], config: Config)(using @unused z: Env) =
    given Ordering[FileInfo] = orderingFor(config)

    val listingBuffer = scala.collection.mutable.TreeSet.empty[generic.FileInfo]

    for path <- items.iterator
    do
      try listingBuffer += FileInfo(path, config.dereference)
      catch case e: IOException => Console.err.println(s"scalals: cannot access '$path': ${e.getMessage}")

    listingBuffer
  end list

  protected def groupDirsFirst(underlying: Ordering[generic.FileInfo]): Ordering[generic.FileInfo] =
    new Ordering[FileInfo]:
      override def compare(a: generic.FileInfo, b: generic.FileInfo): Int =
        if a.isDirectory == b.isDirectory then underlying.compare(a, b)
        else if a.isDirectory then -1
        else 1

  def listAll(
      listingBuffer: scala.collection.mutable.Set[FileInfo],
      config: Config,
      decorators: Vector[Decorator],
  ): Unit =
    // import java.util.{ List => JList }
    // import java.util.function.Supplier
    // scala.collection.mutable.ArrayBuffer.empty[FileInfo]

    // val supplier: Supplier[JList[FileInfo]] = () => listingBuffer.asJava
    // new ArrayList[FileInfo](100)
//    timing("collect"){

    // .collect(Collectors.toCollection[FileInfo, JList[FileInfo]](supplier))
    // Collectors.toList())

//    timing("sort")(listing.sort(if (config.reverse) comparator.reversed() else comparator))
//    val sorted = timing("sort")(listingBuffer.sortWith(if (config.reverse) { (a: FileInfo, b: FileInfo) => !comparator(a, b) } else comparator))

    if listingBuffer.nonEmpty then
      val output =
        for
          fileInfo <- listingBuffer.toVector
          decorator <- decorators
          builder = StringBuilder()
        yield decorator.decorate(fileInfo, builder) -> builder

      val sizes = output.map(_._1.abs)
      // val minlen = sizes.min
      val columns =
        if config.long || config.longWithoutGroup || config.oneLine then decorators.size
        else
          val terminalMax = Terminal.width
          (terminalMax / (sizes.max + 1)) max 1
      val maxColSize =
        val g = sizes.grouped(columns).toList
        val h =
          if sizes.size > columns then g.init :+ (g.last ++ List.fill(columns - g.last.size)(0))
          else g
        h.transpose.map(_.max)

      // Console.err.println(s"$columns")
      // Console.err.println(maxColSize.mkString(", "))
      for record <- output.grouped(columns) do
        for
          ((width, builder), i) <- record.zipWithIndex
          colSize = maxColSize(i)
          padding = " " * (colSize - width.abs + 1)
        do
          if width < 0 then print(padding)

          print(builder)

          if i < record.size - 1 then print(if width >= 0 then padding else " ")
          else println()
        end for
      end for

  final def date: Decorator = new Decorator:
    private val halfayear: Long = 31556952L / 2
    private val recentLimit = Instant.now.minusSeconds(halfayear).toEpochMilli()
    private val cache = mutable.LongMap.empty[String]
    private val recentFormat = DateTimeFormatter.ofPattern("MMM ppd hh:mm").withZone(ZoneId.systemDefault())
    private val dateFormat = DateTimeFormatter.ofPattern("MMM ppd  yyyy").withZone(ZoneId.systemDefault())

    override def decorate(file: generic.FileInfo, builder: StringBuilder): Int =
      val instant = file.lastModifiedTime.toEpochMilli()

      val date = cache.getOrElseUpdate(
        instant / 1000, {
          val format = if instant > recentLimit then recentFormat else dateFormat

          format.format(file.lastModifiedTime)
        },
      )
      builder.append(date)
      date.length
    end decorate
  end date

  def layout(config: Config): Vector[Decorator] =
//    val ext = file.name.dropWhile(_ == '.').replaceFirst(".*[.]", "").toLowerCase(Locale.ENGLISH)
//
//    val key = if (files.contains(ext)) ext else aliases.getOrElse(ext, "file")v1
//    val symbol = files.getOrElse(key, ' ')

//    val code = Colors.colorFor(file)
//
//    import Console.RESET

    // val output = Seq(IconDecorator & ColorDecorator, IndicatorDecorator).foldLeft(Decorated(0, "")){
    //  case (d, action) ⇒ d |+| action(file)
    // }

//    val decorated = if (config.hyperlink) hyperlink(file.path.toUri.toURL.toString, file.name) else file.name
    val decorator: Decorator =
      val d = Decorator(
        IconDecorator,
        config.hyperlink match
          case When.never                         => Decorator.name
          case When.auto if !Terminal.isTTYOutput => Decorator.name
          case _                                  => HyperlinkDecorator(Decorator.name),
      ).colored(config.colorMode)
        .cond(config.indicatorStyle `ne` IndicatorStyle.none)(
          IndicatorDecorator(config.indicatorStyle)
        )

      if config.showGitStatus then GitDecorator + d else d
    end decorator

    if config.long || config.longWithoutGroup then
      val perms = new Decorator:
        override def decorate(file: FileInfo, builder: StringBuilder): Int =
          val firstChar =
            if file.isBlockDev then 'b'
            else if file.isCharDev then 'c'
            else if file.isDirectory then 'd'
            else if file.isSymlink then 'l'
            else if file.isPipe then 'p'
            else if file.isSocket then 's'
            else '-'

          val _ = builder.append(firstChar).append(permissionString(file.permissions))

          3 * 3 + 1
        end decorate

      val fileAndLink = new Decorator:
        override def decorate(file: FileInfo, builder: StringBuilder): Int =
          val n = decorator.decorate(file, builder)

          if file.isSymlink then
            val t = Files.readSymbolicLink(file.path)
            val target = s" → $t"

            builder.append(target)

            n + target.length()
          else n
          end if
        end decorate

      val user = new Decorator:
        override def decorate(file: FileInfo, builder: StringBuilder): Int =
          val owner =
            try file.owner
            catch case e: IOException => "-"

          builder.append(owner)
          owner.length()
        end decorate

      val group: Decorator = (file: FileInfo, builder: StringBuilder) =>
        val group =
          try
            // val group = file.group
            // principalCache.getOrElseUpdate(group, group.getName)
            file.group
          catch case e: IOException => "-"
        builder.append(group)
        group.length()

      val sizeDecorator = config.humanReadable.fold(SizeDecorator(config.blockSize))(HumanSizeDecorator(_))

      Vector(perms, user) ++ (if config.long then Vector(group) else Vector.empty) ++ Vector(
        ToggleAlignment(sizeDecorator),
        date,
        fileAndLink,
      )
    else if config.printSize then
      val sizeDecorator = config.humanReadable.fold(SizeDecorator(config.blockSize))(HumanSizeDecorator(_))
      Vector(sizeDecorator + decorator)
    else Vector(decorator)
    end if
  end layout
end Core
