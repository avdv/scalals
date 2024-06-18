package de.bley.scalals

import scala.scalanative.unsafe.*
import scala.scalanative.posix.unistd.STDOUT_FILENO

@extern
object termios:
  @name("scalanative_consoleWidth")
  def consoleWidth: CLongInt = extern

  def isatty(fileno: CInt): Boolean = extern

object Terminal:
  def isTTYOutput: Boolean = termios.isatty(STDOUT_FILENO)

  def width: Int = termios.consoleWidth.toInt
end Terminal
