package de.bley.scalals

import scala.annotation.switch

object Utils {
  // fast globbing implementation, see https://research.swtch.com/glob
  def glob(pattern: String, string: String): Boolean = {
    var px, nx, nextPx, nextNx = 0
    var mismatch = false

    while !mismatch && (px < pattern.length || nx < string.length) do {
      var handled = false

      if px < pattern.length then {
        (pattern(px): @switch) match {
          case '?' =>
            if nx < string.length then {
              px += 1
              nx += 1
              handled = true
            }

          case '*' =>
            // Try to match at nx.
            // If that doesn't work out, restart at nx+1 next.
            nextPx = px
            nextNx = nx + 1
            px += 1
            handled = true

          case c =>
            if nx < string.length && string(nx) == c then {
              px += 1
              nx += 1
              handled = true
            }
        }
      }

      if handled then {
        // continue
      } else if 0 < nextNx && nextNx <= string.length then {
        // Mismatch. Maybe restart.
        px = nextPx
        nx = nextNx
      } else {
        mismatch = true
      }
    }
    !mismatch
  }
}
