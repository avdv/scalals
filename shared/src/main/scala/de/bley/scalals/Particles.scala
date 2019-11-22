package de.bley.scalals

import java.util.concurrent.TimeUnit
import java.util.Locale
import java.nio.file.{ Path, Paths }

import de.bley.scalals.CoreConfig.{ aliases, files }

import scala.collection.mutable
import scala.io.Source
import scala.util.Try
import squants.information.Information
import squants.information.InformationConversions._
import squants.experimental.formatter.DefaultFormatter
import squants.experimental.unitgroups.information.IECInformation

sealed trait FileSizeMode
final case class ScaleSize(factor: Int)

class FileSize(mode: FileSizeMode) {
  def format(fileInfo: FileInfo) = {
    fileInfo.size.toString
  }
}

// more decorators

trait Decorator {
  def decorate(subject: FileInfo, builder: StringBuilder): Int

  def +(other: Decorator): Decorator = {
    val self = this
    new Decorator {
      override def decorate(subject: FileInfo, builder: StringBuilder): Int = {
        self.decorate(subject, builder) + other.decorate(subject, builder)
      }
    }
  }

  // TODO:
  //def colored(color: String): Decorator = ColorDecorator(color, this)
  def colored(mode: ColorMode.ColorMode): Decorator = mode match {
    case ColorMode.never                         => this
    case ColorMode.auto if !Terminal.isTTYOutput => this
    case _                                       => ColorDecorator(this)
  }

  def cond(p: Boolean)(d: ⇒ Decorator): Decorator = {
    if (p) this + d else this
  }
}

object Decorator {
  object name extends Decorator {
    override def decorate(file: FileInfo, builder: StringBuilder): Int = {
      builder.append(file.name)
      file.name.length
    }
  }

  def apply(d: Decorator, ds: Decorator*): Decorator = ds.foldLeft(d) {
    case (lhs, rhs) => lhs + rhs
  }
}

object ColorDecorator {
  import scala.io.AnsiColor.RESET

  def apply(d: Decorator): Decorator = new Decorator {
    override def decorate(subject: FileInfo, builder: StringBuilder): Int = {
      val code = Colors.colorFor(subject)
      val o = d.decorate(subject, builder.append(code))
      builder.append(RESET)
      //inner.copy(text = s"$code${inner.text}$RESET")
      o
    }
  }
}

final case class IndicatorDecorator(style: IndicatorStyle.IndicatorStyle) extends Decorator {
  override def decorate(subject: FileInfo, builder: StringBuilder): Int = {

    val indicator = style match {
      case IndicatorStyle.none                           => ""
      case IndicatorStyle.slash if (subject.isDirectory) => "/"
      case IndicatorStyle.classify | IndicatorStyle.fileType =>
        if (subject.isDirectory)
          "/"
        else if (subject.isSymlink)
          "@"
        else if (subject.isSocket)
          "="
        else if (subject.isPipe) // FIFO
          "|"
        else if (subject.isExecutable && style == IndicatorStyle.classify)
          "*"
        else
          ""
    }
    builder.append(indicator)
    indicator.length
  }
}

object HyperlinkDecorator {
  def apply(d: Decorator): Decorator = new Decorator {
    override def decorate(subject: FileInfo, builder: StringBuilder): Int = {
      builder
        .append("\u001b]8;;file://")
        .append(subject.path.toString)
        .append('\u0007')
      val o = d.decorate(subject, builder)

      builder.append("\u001b]8;;\u0007")

      o
    }
  }
}

object GitDecorator extends Decorator {
  import scala.io.AnsiColor._

  override def decorate(subject: FileInfo, builder: StringBuilder): Int = {
    GitDecorator(subject.path).toOption.fold(0) { info ⇒
      info.get(subject.name).fold(builder.append(s"  $GREEN✓$RESET ")) { modes ⇒
        val m = modes.map {
          case 'M' ⇒ BLUE + 'M' + RESET
          case 'D' ⇒ RED + 'D' + RESET
          case 'A' ⇒ YELLOW + 'A' + RESET
          case '?' ⇒ MAGENTA + '?' + RESET
          case '!' => " "
          case c ⇒ c.toString
        }
        builder
          .append(" " * (3 - modes.size))
          .append(m.mkString)
          .append(' ')
      }
      4
    }
  }

  private val gitStatus = mutable.HashMap.empty[Path, Try[Map[String, Set[Char]]]]

  private def apply(path: Path): Try[Map[String, Set[Char]]] = {
    val parent = path.getParent()

    gitStatus.getOrElseUpdate(parent, getStatus(parent))
  }

  private def getStatus(path: Path) = {
    val proc = new ProcessBuilder("git", "-C", path.toAbsolutePath.toString, "rev-parse", "--show-prefix").start()
    val status = for {
      prefix ← Try(Source.fromInputStream(proc.getInputStream()).mkString.trim)
      if {
        while (!proc.waitFor(10, TimeUnit.MILLISECONDS)) ()
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
        "."
      ).start()
      out ← Try(Source.fromInputStream(gitStatus.getInputStream()))
      gitInfo = mutable.HashMap.empty[String, mutable.HashSet[Char]].withDefault(m ⇒ mutable.HashSet.empty[Char])
      sb = new StringBuilder
      iter: BufferedIterator[Char] = out.iter.buffered
    } yield {
      def getc(): Boolean = {
        val ch = iter.next()
        if (ch == '\u0000') false
        else {
          sb append ch
          true
        }
      }

      while (iter.hasNext) {
        sb.clear()
        while (getc()) {}
        val line = sb.toString
        val mode = line.substring(0, 2).trim
        val file = line.substring(3)

        // skip next line for renames
        if (mode.contains('R')) {
          while (iter.hasNext && iter.next() != '\u0000') {}
          if (iter.hasNext) iter.next() // discard NUL byte
        }

        val f = Paths.get(file.stripPrefix(prefix)).subpath(0, 1).toString

        gitInfo(f) = gitInfo(f) ++= mode
      }
      gitInfo.toMap.mapValues(_.toSet)
    }
    status
  }
}

object SizeDecorator {
  val sizeFormatter = new DefaultFormatter(IECInformation)
}

final case class SizeDecorator(scale: Information) extends Decorator {

  /**
    * @see https://stackoverflow.com/questions/3263892/format-file-size-as-mb-gb-etc
    * @see https://en.wikipedia.org/wiki/Zettabyte
    * @param fileSize Up to Exabytes
    * @return
    */
  private def humanReadableByteSize(fileSize: Long): String = {
    if (fileSize <= 0) return "0 B"
    // kilo, Mega, Giga, Tera, Peta, Exa, Zetta, Yotta
    val units: Array[String] = Array("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroup = (Math.log10(fileSize.toDouble) / Math.log10(1024))
    f"${fileSize / Math.pow(1024, digitGroup)}%3.3f ${units(digitGroup.toInt)}"
  }

  override def decorate(subject: FileInfo, builder: StringBuilder): Int = {
    val scaled = subject.size.bytes
    //val output = f"$scaled%.2f"
    val best = SizeDecorator.sizeFormatter.inBestUnit(scaled)
    val output = f"${best.value}%f ${best.unit.symbol}" //.toString()
    builder.append(output).append(' ')
    output.length + 1
  }
}

object IconDecorator extends Decorator {
  override def decorate(subject: FileInfo, builder: StringBuilder): Int = {
    val ext = {
      val e = subject.name.dropWhile(_ == '.')
      val dot = e.lastIndexOf('.')
      if (dot > 0) e.substring(dot + 1).toLowerCase(Locale.ENGLISH)
      else ""
    }
    //val key = if (files.contains(ext)) ext else aliases.getOrElse(ext, ext)
    //val symbol = files.getOrElse(key,  ) //
    val symbol = files.getOrElse(ext, {
      aliases.get(ext).fold(if (subject.isDirectory) '\uf115' else '\uf15b')(files.getOrElse(_, ' '))
    })
    builder.append(' ').append(symbol).append("  ")

    4
  }
}
