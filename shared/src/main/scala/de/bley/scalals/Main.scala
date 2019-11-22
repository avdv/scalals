package de.bley.scalals

import java.nio.file.{ Path, Paths }
import squants.information.Information
import squants.information.Bytes

//import scala.io.AnsiColor

object SortMode extends Enumeration {
  type SortMode = Value
  // TODO: version (-v)
  val name, none, size, time, extension = Value
}

object ColorMode extends Enumeration {
  type ColorMode = Value
  val always, auto, never = Value
}

object IndicatorStyle extends Enumeration {
  type IndicatorStyle = Value

  val none, slash, classify = Value
  val fileType = Value("file-type")
}

object `package` {
  implicit val pathRead = scopt.Read.reads(Paths.get(_: String))
  implicit val sortRead = scopt.Read.reads(SortMode withName _)

  implicit val colorModeRead: scopt.Read[ColorMode.ColorMode] =
    scopt.Read.reads[ColorMode.ColorMode](ColorMode withName _)

  implicit val indicatorStyleRead: scopt.Read[IndicatorStyle.IndicatorStyle] =
    scopt.Read.reads(IndicatorStyle withName _)
}

final case class Config(
    classify: Boolean = false,
    blockSize: Information = Bytes(1),
    sort: SortMode.SortMode = SortMode.name,
    showAll: Boolean = false,
    listDirectories: Boolean = true,
    groupDirectoriesFirst: Boolean = false,
    hyperlink: Boolean = false,
    dereference: Boolean = false,
    indicatorStyle: IndicatorStyle.IndicatorStyle = IndicatorStyle.none,
    long: Boolean = false,
    oneLine: Boolean = false,
    showGitStatus: Boolean = false,
    printSize: Boolean = false,
    paths: List[Path] = List.empty,
    colorMode: ColorMode.ColorMode = ColorMode.auto,
    reverse: Boolean = false
)

object Main {
  implicit val informationReads: scopt.Read[Information] = scopt.Read.reads[Information] { str =>
    Information(str).get
  }
  val parser = new scopt.OptionParser[Config]("scalals") {
    head("scalals", BuildInfo.version)

    opt[SortMode.Value]("sort")
      .text("sort by WORD instead of name: none (-U), size (-S), time (-t), extension (-X)")
      .valueName("WORD")
      .action((m, c) ⇒ c.copy(sort = m))
    opt[Unit]('F', "classify")
      .text("append indicator (one of */=>@|) to entries")
      .action((_, c) ⇒ c.copy(indicatorStyle = IndicatorStyle.classify))
    opt[Unit]("file-type")
      .text("likewise, except do not append '*'")
      .action((_, c) ⇒ c.copy(indicatorStyle = IndicatorStyle.fileType))
    opt[IndicatorStyle.Value]("indicator-style")
      .text(
        "append indicator with style WORD to entry names: none (default), slash (-p), file-type (--file-type), classify (-F)"
      )
      .valueName("STYLE")
      .action((_, c) ⇒ c.copy(indicatorStyle = IndicatorStyle.fileType))
    opt[Unit]('p', "indicator-style=slash")
      .text("append / indicator to directories")
      .action((_, c) ⇒ c.copy(indicatorStyle = IndicatorStyle.slash))
    opt[Unit]('l', "long")
      .text("use a long listing format")
      .action((_, c) ⇒ c.copy(long = true))
    opt[Information]("block-size") // TODO: parse unit
      .text("scale sizes by SIZE when printing them")
      .action((factor, c) ⇒ c.copy(blockSize = factor))
    opt[Unit]('L', "dereference")
      .text("deference symbolic links")
      .action((_, c) => c.copy(dereference = true))
    opt[Unit]('r', "reverse")
      .text("reverse order while sorting")
      .action((_, c) ⇒ c.copy(reverse = true))
    opt[Unit]("hyperlink")
      .text("hyperlink file names")
      .action((_, c) ⇒ c.copy(hyperlink = true))
    opt[Unit]('d', "directory")
      .text("list directories themselves, not their contents")
      .action((_, c) ⇒ c.copy(listDirectories = false))
    opt[Unit]('a', "all")
      .text("do not ignore hidden files")
      .action((_, c) ⇒ c.copy(showAll = true))
    opt[Unit]('A', "almost-all")
      .text("do not list . and ..")
      .action((_, c) ⇒ c)
    opt[ColorMode.ColorMode]("color")
      .text("colorize the output")
      .action((mode, c) => c.copy(colorMode = mode))
    opt[Unit]("git-status")
      .text("show git status for each file")
      .action((_, c) ⇒ c.copy(showGitStatus = true))
    opt[Unit]("group-directories-first")
      .text("group directories before files")
      .action((_, c) ⇒ c.copy(groupDirectoriesFirst = true))
    opt[Unit]('h', "human-readable")
      .text("print sizes in human readable format")
    opt[Unit]('s', "size")
      .text("print size of each file, in blocks")
      .action((_, c) => c.copy(printSize = true))
    opt[Unit]('S', "sort-by-size")
      .text("sort by file size")
      .action((_, c) ⇒ c.copy(sort = SortMode.size))
    opt[Unit]('t', "sort-by-time")
      .text("sort by last modification time")
      .action((_, c) ⇒ c.copy(sort = SortMode.time))
    opt[Unit]('U', "do-not-sort")
      .text("do not sort; display files in directory order")
      .action((_, c) => c.copy(sort = SortMode.none))
    opt[Unit]('X', "sort-by-extension")
      .text("sort alphabetically by entry extension")
      .action((_, c) ⇒ c.copy(sort = SortMode.extension))
    opt[Unit]('1', "one-line")
      .text("list one file per line")
      .action((_, c) ⇒ c.copy(oneLine = true))
    opt[Unit]("version")
      .text("output version information and exit")
      .action { (_, c) ⇒
        println(s"scalals ${BuildInfo.version}")
        sys.exit(0)
      }
    arg[Path]("...")
      .unbounded()
      .optional()
      //.withFallback(() => "someFallback")
      .action { (x, c) =>
        c.copy(paths = c.paths :+ x)
      }
      .text("path entries")
    help("help")
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()).foreach(Core.ls)
  }
}
