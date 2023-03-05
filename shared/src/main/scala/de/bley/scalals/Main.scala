package de.bley.scalals

import java.nio.file.{ Path, Paths }

//import scala.io.AnsiColor

enum SortMode:
  // TODO: version (-v)
  case name, none, size, time, extension

enum ColorMode:
  case always, auto, never

enum IndicatorStyle:
  case none, slash, classify, `file-type`

given scopt.Read[Path] = scopt.Read.reads(Paths.get(_: String))

given scopt.Read[SortMode] = scopt.Read.reads(SortMode valueOf _)

given scopt.Read[ColorMode] = scopt.Read.reads[ColorMode](ColorMode valueOf _)

given scopt.Read[IndicatorStyle] = scopt.Read.reads(IndicatorStyle valueOf _)

final case class Config(
    blockSize: Long = 1,
    sort: SortMode = SortMode.name,
    showAll: Boolean = false,
    listDirectories: Boolean = true,
    groupDirectoriesFirst: Boolean = false,
    hyperlink: Boolean = false,
    dereference: Boolean = false,
    dereferenceArgs: Boolean = false,
    dereferenceArgsToDirectory: Boolean = false,
    indicatorStyle: IndicatorStyle = IndicatorStyle.none,
    long: Boolean = false,
    oneLine: Boolean = false,
    showGitStatus: Boolean = false,
    tree: Boolean = false,
    maxDepth: Option[Int] = None,
    printSize: Boolean = false,
    paths: List[Path] = List.empty,
    colorMode: ColorMode = ColorMode.auto,
    reverse: Boolean = false
)

object Main {
  import scopt.OParser
  val builder = OParser.builder[Config]
  val parser = {
    import builder.*
    OParser.sequence(
      programName("scalals"),
      head("scalals", BuildInfo.version),
      opt[SortMode]("sort")
        .unbounded()
        .text("sort by WORD instead of name: none (-U), size (-S), time (-t), extension (-X)")
        .valueName("WORD")
        .action((m, c) => c.copy(sort = m)),
      opt[Unit]('F', "classify")
        .unbounded()
        .text("append indicator (one of */=>@|) to entries")
        .action((_, c) => c.copy(indicatorStyle = IndicatorStyle.classify)),
      opt[Unit]('H', "dereference-command-line")
        .unbounded()
        .text("follow symbolic links listed on the command line")
        .action((_, c) => c.copy(dereferenceArgs = true)),
      opt[Unit]("dereference-command-line-symlink-to-dir")
        .unbounded()
        .text("follow each command line symbolic link that points to a directory")
        .action((_, c) => c.copy(dereferenceArgsToDirectory = true)),
      opt[Unit]("file-type")
        .unbounded()
        .text("likewise, except do not append '*'")
        .action((_, c) => c.copy(indicatorStyle = IndicatorStyle.`file-type`)),
      opt[IndicatorStyle]("indicator-style")
        .unbounded()
        .text(
          "append indicator with style WORD to entry names: none (default), slash (-p), file-type (--file-type), classify (-F)"
        )
        .valueName("STYLE")
        .action((s, c) => c.copy(indicatorStyle = s)),
      opt[Unit]('p', "indicator-style=slash")
        .unbounded()
        .text("append / indicator to directories")
        .action((_, c) => c.copy(indicatorStyle = IndicatorStyle.slash)),
      opt[Unit]('l', "long")
        .unbounded()
        .text("use a long listing format")
        .action((_, c) => c.copy(long = true)),
      opt[Long]("block-size") // TODO: parse unit
        .unbounded()
        .text("scale sizes by SIZE when printing them")
        .action((factor, c) => c.copy(blockSize = factor)),
      opt[Unit]('L', "dereference")
        .unbounded()
        .text("deference symbolic links")
        .action((_, c) => c.copy(dereference = true)),
      opt[Unit]('r', "reverse")
        .unbounded()
        .text("reverse order while sorting")
        .action((_, c) => c.copy(reverse = true)),
      opt[Option[Int]]("tree")
        .unbounded()
        .text("show tree")
        .action((depth, c) => c.copy(tree = true, maxDepth = depth)),
      opt[Unit]("hyperlink")
        .unbounded()
        .text("hyperlink file names")
        .action((_, c) => c.copy(hyperlink = true)),
      opt[Unit]('d', "directory")
        .unbounded()
        .text("list directories themselves, not their contents")
        .action((_, c) => c.copy(listDirectories = false)),
      opt[Unit]('a', "all")
        .unbounded()
        .text("do not ignore hidden files")
        .action((_, c) => c.copy(showAll = true)),
      opt[Unit]('A', "almost-all")
        .unbounded()
        .text("do not list . and ..")
        .action((_, c) => c),
      opt[ColorMode]("color")
        .unbounded()
        .text("colorize the output")
        .action((mode, c) => c.copy(colorMode = mode)),
      opt[Unit]("git-status")
        .unbounded()
        .text("show git status for each file")
        .action((_, c) => c.copy(showGitStatus = true)),
      opt[Unit]("group-directories-first")
        .unbounded()
        .text("group directories before files")
        .action((_, c) => c.copy(groupDirectoriesFirst = true)),
      opt[Unit]('h', "human-readable")
        .unbounded()
        .text("print sizes in human readable format"),
      opt[Unit]('s', "size")
        .unbounded()
        .text("print size of each file, in blocks")
        .action((_, c) => c.copy(printSize = true)),
      opt[Unit]('S', "sort-by-size")
        .unbounded()
        .text("sort by file size")
        .action((_, c) => c.copy(sort = SortMode.size)),
      opt[Unit]('t', "sort-by-time")
        .unbounded()
        .text("sort by last modification time")
        .action((_, c) => c.copy(sort = SortMode.time)),
      opt[Unit]('U', "do-not-sort")
        .unbounded()
        .text("do not sort; display files in directory order")
        .action((_, c) => c.copy(sort = SortMode.none)),
      opt[Unit]('X', "sort-by-extension")
        .unbounded()
        .text("sort alphabetically by entry extension")
        .action((_, c) => c.copy(sort = SortMode.extension)),
      opt[Unit]('1', "one-line")
        .unbounded()
        .text("list one file per line")
        .action((_, c) => c.copy(oneLine = true)),
      help("help").text("show this help and exit"),
      version("version").text("show version information"),
      note("""|
              |Exit status:
              | 0  if OK,
              | 1  if minor problems (e.g., cannot access subdirectory),
              | 2  if serious trouble (e.g., parsing command line options).
              |""".stripMargin),
      arg[Path]("...")
        .hidden()
        .unbounded()
        .optional()
        .action { (x, c) =>
          c.copy(paths = c.paths :+ x)
        }
    )
  }

  def main(args: Array[String]): Unit =
    OParser.parse(parser, args, Config()).fold(sys.exit(2)) {
      Core.ls
    }
}
