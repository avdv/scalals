package de.bley.scalals

import java.util.concurrent.TimeUnit
import java.nio.file.{ Path, Paths }

import de.bley.scalals.CoreConfig.{ aliases, files }

import scala.collection.mutable
import scala.io.Source
import scala.util.Try

sealed trait FileSizeMode
final case class ScaleSize(factor: Int)

class FileSize(mode: FileSizeMode):
  def format(fileInfo: FileInfo) =
    fileInfo.size.toString

// more decorators

trait Decorator:
  def decorate(subject: generic.FileInfo, builder: StringBuilder): Int

  def +(other: Decorator): Decorator =
    val self = this
    new Decorator:
      override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =
        self.decorate(subject, builder) + other.decorate(subject, builder)

  // TODO:
  // def colored(color: String): Decorator = ColorDecorator(color, this)
  def colored(mode: ColorMode): Decorator = mode match
    case ColorMode.never                         => this
    case ColorMode.auto if !Terminal.isTTYOutput => this
    case _                                       => ColorDecorator(this)

  def cond(p: Boolean)(d: => Decorator): Decorator =
    if p then this + d else this

object Decorator:
  object name extends Decorator:
    override def decorate(file: generic.FileInfo, builder: StringBuilder): Int =
      builder.append(file.name)
      file.name.length

  def apply(d: Decorator, ds: Decorator*): Decorator = ds.foldLeft(d) { case (lhs, rhs) =>
    lhs + rhs
  }

object ColorDecorator:
  import scala.io.AnsiColor.RESET

  def apply(d: Decorator): Decorator = new Decorator:
    override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =
      val code = Colors.colorFor(subject)
      val o = d.decorate(subject, builder.append(code))
      builder.append(RESET)
      // inner.copy(text = s"$code${inner.text}$RESET")
      o

final case class IndicatorDecorator(style: IndicatorStyle) extends Decorator:
  override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =

    val indicator = style match
      case IndicatorStyle.slash => if subject.isDirectory then "/" else ""
      case IndicatorStyle.none  => ""
      case IndicatorStyle.classify | IndicatorStyle.`file-type` =>
        if subject.isDirectory then "/"
        else if subject.isSymlink then "@"
        else if subject.isSocket then "="
        else if subject.isPipe then // FIFO
          "|"
        else if subject.isExecutable && style == IndicatorStyle.classify then "*"
        else ""
    builder.append(indicator)
    indicator.length

object HyperlinkDecorator:
  def apply(d: Decorator): Decorator = new Decorator:
    override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =
      builder
        .append("\u001b]8;;file://")
        .append(subject.path.toString)
        .append('\u0007')
      val o = d.decorate(subject, builder)

      builder.append("\u001b]8;;\u0007")

      o

object GitDecorator extends Decorator:
  import scala.io.AnsiColor.*

  override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =
    GitDecorator(subject.path).toOption.fold(0) { info =>
      info.get(subject.name).fold(builder.append(s"  $GREEN✓$RESET ")) { modes =>
        val m = modes.map {
          case 'M' => BLUE + 'M' + RESET
          case 'D' => RED + 'D' + RESET
          case 'A' => YELLOW + 'A' + RESET
          case '?' => MAGENTA + '?' + RESET
          case '!' => " "
          case c   => c.toString
        }
        builder
          .append(" " * (3 - modes.size))
          .append(m.mkString)
          .append(' ')
      }
      4
    }

  private val gitStatus = mutable.HashMap.empty[Path, Try[Map[String, Set[Char]]]]

  private def apply(path: Path): Try[Map[String, Set[Char]]] =
    val parent = path.toAbsolutePath.getParent()

    gitStatus.getOrElseUpdate(parent, getStatus(parent))

  private def getStatus(path: Path) =
    val proc = new ProcessBuilder("git", "-C", path.toAbsolutePath.toString, "rev-parse", "--show-prefix").start()
    val status = for
      prefix <- Try(Source.fromInputStream(proc.getInputStream()).mkString.trim)
      if {
        while !proc.waitFor(10, TimeUnit.MILLISECONDS) do ()
        proc.exitValue() == 0
      }
      gitStatus = new ProcessBuilder(
        "git",
        "-C",
        path.toAbsolutePath.toString,
        "status",
        "--porcelain",
        "-z",
        "-unormal",
        "--ignored",
        ".",
      ).start()
      out <- Try(Source.fromInputStream(gitStatus.getInputStream()))
      gitInfo = mutable.HashMap.empty[String, mutable.HashSet[Char]].withDefault(m => mutable.HashSet.empty[Char])
      sb = new StringBuilder
      iter = out.iter.buffered
    yield
      def getc(): Boolean =
        val ch = iter.next()
        if ch == '\u0000' then false
        else
          sb append ch
          true

      while iter.hasNext do
        sb.clear()
        while getc() do {}
        val line = sb.toString
        val mode = line.substring(0, 2).trim
        val file = line.substring(3)

        // skip next line for renames
        if mode.contains('R') then
          while iter.hasNext && iter.next() != '\u0000' do {}
          if iter.hasNext then iter.next() // discard NUL byte

        val f = Paths.get(file.stripPrefix(prefix)).subpath(0, 1).toString

        gitInfo(f) = gitInfo(f) ++= mode
      gitInfo.view.mapValues(_.toSet).toMap
    status

final case class SizeDecorator(scale: Long = 1L) extends Decorator:
  override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =
    val output = (subject.size.toDouble / scale).round.toString
    builder.append(output)
    -output.length // *hacky* negative size indicates right alignment

object IconDecorator extends Decorator:
  override def decorate(subject: generic.FileInfo, builder: StringBuilder): Int =
    val ext =
      val e = subject.name.dropWhile(_ == '.')
      val dot = e.lastIndexOf('.')
      if dot > 0 then e.substring(dot + 1).toLowerCase() // FIXME: Locale.ENGLISH
      else ""
    // val key = if (files.contains(ext)) ext else aliases.getOrElse(ext, ext)
    // val symbol = files.getOrElse(key,  ) //
    val symbol = files.getOrElse(
      ext, {
        aliases.get(ext).fold(if subject.isDirectory then '\uf115' else '\uf15b')(files.getOrElse(_, ' '))
      },
    )
    builder.append(' ').append(symbol).append("  ")

    4
