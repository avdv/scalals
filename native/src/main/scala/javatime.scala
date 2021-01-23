package java.time

case class Instant(seconds: Long) {
  def toEpochMilli(): Long = seconds * 1000
}

object Instant {
  def ofEpochSecond(seconds: Long): Instant = Instant(seconds)
}
