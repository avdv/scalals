package de.bley.scalals

import java.nio.file.attribute._
import java.nio.file.{ Files, LinkOption, Path }
import java.time.Instant

import scala.collection.JavaConverters._

object FileInfo {
  private val executableBits =
    Set(PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OTHERS_EXECUTE)
  //private val modeField = {
  //  val f = Class.forName("sun.nio.fs.UnixFileAttributes").getDeclaredField("st_mode")
  //  f.setAccessible(true)
  //  f
  //}
  private def mode(attr: PosixFileAttributes) = {
    import PosixFilePermission._

    attr.permissions().asScala.foldLeft(0) {
      case (z, a) =>
        z | {
          a match {
            case GROUP_EXECUTE  => S_IXGRP
            case GROUP_READ     => S_IRGRP
            case GROUP_WRITE    => S_IWGRP
            case OTHERS_EXECUTE => S_IXOTH
            case OTHERS_READ    => S_IROTH
            case OTHERS_WRITE   => S_IWOTH
            case OWNER_EXECUTE  => S_IXUSR
            case OWNER_READ     => S_IRUSR
            case OWNER_WRITE    => S_IWUSR
          }
        }
    }
  }
}

final case class FileInfo(path: Path) extends generic.FileInfo {
  private val attributes = Files.readAttributes(path, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)
  val name = path.getFileName.toString

  @inline def isDirectory: Boolean = attributes.isDirectory
  @inline def isRegularFile: Boolean = attributes.isRegularFile
  @inline def isSymlink: Boolean = attributes.isSymbolicLink
  @inline def group: GroupPrincipal = attributes.group()
  @inline def owner: UserPrincipal = attributes.owner()
  @inline def permissions: Int = FileInfo.mode(attributes).toInt
  @inline def size: Long = attributes.size()
  @inline def lastModifiedTime: Instant = attributes.lastModifiedTime().toInstant
  @inline def lastAccessTime: Instant = attributes.lastAccessTime().toInstant
  @inline def creationTime: Instant = attributes.creationTime().toInstant
  @inline def isExecutable = attributes.permissions().asScala.exists(FileInfo.executableBits)
}
