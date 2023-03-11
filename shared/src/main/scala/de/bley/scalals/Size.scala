package de.bley.scalals

object Size:
  private val prefixes = Array("", "k", "M", "G", "T", "P")

  /// render human readable size
  def render(value: Long, powers: Long): String =
    var prefix = 0
    var scaled = value.toDouble

    while scaled > powers && prefix < prefixes.size do
      scaled /= powers
      prefix += 1

    if prefix != 0 && scaled < 10 then f"$scaled%.1f${prefixes(prefix)}"
    else f"${scaled.round}${prefixes(prefix)}"
  end render
end Size
