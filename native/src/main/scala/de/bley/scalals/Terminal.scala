package de.bley.scalals

import scala.scalanative.libc.stdio.perror
import scala.scalanative.unsigned.*
import scala.scalanative.unsafe.*
import scala.scalanative.posix.unistd.STDOUT_FILENO
import scala.scalanative.posix.fcntl.*
import scala.scalanative.posix.unistd.close
import scala.scalanative.posix.sys.ioctl.*

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
    finally
      if tty != STDOUT_FILENO then
        val ret = close(tty)
        if ret != 0 then perror(c"close tty")
    end try
  end width
end Terminal
