package de.bley.scalals

import scalanative.unsigned.*
import scalanative.unsafe.*
import scalanative.posix.unistd.STDOUT_FILENO
import scalanative.posix.fcntl.*
import scalanative.posix.unistd.close
import scalanative.posix.sys.ioctl.*

@extern
object termios:
  @name("scalanative_tiocgwinsize")
  def TIOCGWINSZ: CLongInt = extern

@extern
object xunistd:
  @name("scalanative_isatty")
  def isatty(fileno: CInt): Boolean = extern

object types:
  /*
   struct winsize {
   unsigned short ws_row;
   unsigned short ws_col;
   unsigned short ws_xpixel;   /* unused */
   unsigned short ws_ypixel;   /* unused */
   };
   */
  type winsize = CStruct4[UShort, UShort, UShort, UShort]
end types

object Terminal:
  def isTTYOutput: Boolean = xunistd.isatty(STDOUT_FILENO)

  def width: Int =
    val winsz = stackalloc[types.winsize]()

    var tty = open(c"/dev/tty", O_RDWR, 0.toUInt)
    try
      if tty == -1 then tty = STDOUT_FILENO

      if ioctl(tty, termios.TIOCGWINSZ, winsz.asInstanceOf[Ptr[Byte]]) >= 0 then (winsz._2).toInt
      else sys.env.get("COLUMNS").map(_.toInt).getOrElse(80)
    finally if tty != STDOUT_FILENO then close(tty)
  end width
end Terminal
