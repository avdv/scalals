package de.bley.scalals

import scalanative.unsigned._
import scalanative.unsafe._
import scalanative.posix.unistd.STDOUT_FILENO
import scalanative.posix.fcntl._
import scalanative.posix.unistd.close
import scalanative.posix.sys.ioctl._

@extern
object termios {
  @name("scalanative_tiocgwinsize")
  def TIOCGWINSZ: CLongInt = extern
}

@extern
object xunistd {
  @name("scalanative_isatty")
  def isatty(fileno: CInt): Boolean = extern
}

object types {
  /*
   struct winsize {
   unsigned short ws_row;
   unsigned short ws_col;
   unsigned short ws_xpixel;   /* unused */
   unsigned short ws_ypixel;   /* unused */
   };
   */
  type winsize = CStruct4[UShort, UShort, UShort, UShort]
}

object Terminal {
  def isTTYOutput: Boolean = xunistd.isatty(STDOUT_FILENO)

  def width: Int = {
    val winsz = stackalloc[types.winsize]

    var tty = open(c"/dev/tty", O_RDWR, 0.toUInt)
    try {
      if (tty == -1) tty = STDOUT_FILENO

      if (ioctl(tty, termios.TIOCGWINSZ, winsz.asInstanceOf[Ptr[Byte]]) >= 0) {
        (winsz._2).toInt
      } else {
        sys.env.get("COLUMNS").map(_.toInt).getOrElse(80)
      }
    } finally {
      val _ = if (tty != STDOUT_FILENO) close(tty)
    }
  }
}
