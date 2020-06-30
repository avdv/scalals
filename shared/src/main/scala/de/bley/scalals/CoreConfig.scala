package de.bley.scalals

import java.nio.file.Files

object CoreConfig {
  val files: Map[String, Char] = SFiles.map
//    val p = new Properties()
//    p.load(this.getClass.getClassLoader.getResourceAsStream("files.properties"))
  //  p.load(new FileInputStream("files.properties"))
  //p.asScala.mapValues(_.charAt(0)).toMap
  //}
  val aliases: Map[String, String] = SAliases.map
  //val p = new Properties()
//    p.load(this.getClass.getClassLoader.getResourceAsStream("aliases.properties"))
  //p.load(new FileInputStream("aliases.properties"))
  //p.asScala.toMap
  //}
}

sealed trait FileType
case object RegularFile extends FileType
case object Directory extends FileType
case object Socket extends FileType
case object Pipe extends FileType
case object Special extends FileType
case object Symlink extends FileType
case object Orphan extends FileType
case object Executable extends FileType

class Extension(val suffix: String) extends FileType {
  override def equals(other: Any): Boolean = other match {
    case s: String if s != null    => s.endsWith(suffix)
    case e: Extension if e != null => suffix == e.suffix
    case _                         => false
  }
  override def hashCode: Int = suffix.##
}

object Extension {
  def apply(glob: String): Extension = new Extension(glob.stripPrefix("*."))
}

object Colors {
  def ansiColor(str: String): String = "\u001b[" + str + "m"

  val getType: PartialFunction[String, FileType] = {
    case "fi"               => RegularFile
    case "di"               => Directory
    case "ln"               => Symlink
    case "or"               => Orphan
    case "ex"               => Executable
    case "so"               => Socket
    case "pi"               => Pipe
    case "do" | "bd" | "cd" => Special // door, block device, char device
    // case "mh" => multi hardlink
    case s if s.startsWith("*.") => Extension(s)
  }

  lazy val getColors: Map[AnyRef, String] = {
    val Assign = raw"([^=]+)=([\d;]+|target)".r
    for {
      definition <- sys.env.get("LS_COLORS").filterNot(_.isEmpty).toList
      assign <- definition.split(':')
      Assign(lhs, rhs) = assign
      if getType.isDefinedAt(lhs)
      fileType = getType(lhs)
    } yield {
      fileType -> { if (rhs == "target") "" else ansiColor(rhs) }
    }
  }.toMap[AnyRef, String].withDefaultValue(ansiColor("00"))

  def colorFor(file: FileInfo) = {
    val color = if (file.isDirectory) {
      Directory
    } else if (file.isSymlink) {
      if (Files.notExists(file.path) && getColors.contains(Orphan))
        Orphan
      else
        Symlink
    } else if (file.isPipe) {
      Pipe
    } else if (file.isSocket) {
      Socket
    } else if (file.isRegularFile) {
      if (file.isExecutable)
        Executable
      else
        RegularFile
    } else {
      Special
    }

    //println("colors: #" + getColors.size.toString)
    //getColors.get(color)
    val fileColor = if (color eq RegularFile) {
      getColors.collectFirst {
        case (k, v) if k == file.name => v
      }
    } else None

    fileColor.getOrElse(getColors(color))
  }
}
