object CNumber {
  def getRadix(s: String) =
    if (s.startsWith("0x") || s.startsWith("0X")) 16
    else if (s.startsWith("0")) 8
    else 10

  // might be an octal, hexadecimal or decimal integer literal
  def readCNumber(s: String): Int = Integer.parseInt(s, getRadix(s))

  def unapply(s: String): Option[Int] = Some(readCNumber(s))
}
