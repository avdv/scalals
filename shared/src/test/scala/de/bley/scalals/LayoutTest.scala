package de.bley.scalals

import de.bley.scalals.Layout.{ Arrangement, Orientation }

class LayoutTests extends munit.FunSuite:
  private val mixedWidths = Vector(5, 35, 33, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

  test("vertical layout fills columns top-to-bottom") {
    val arrangement = Layout.arrange(mixedWidths, screenWidth = 80, Orientation.Vertical, gap = 2)

    assertEquals(arrangement.columnWidths, Vector(35, 10, 14, 15))
    assertEquals(
      arrangement.rows,
      Vector(
        Vector(0, 4, 8, 12),
        Vector(1, 5, 9),
        Vector(2, 6, 10),
        Vector(3, 7, 11),
      ),
    )
  }

  test("horizontal layout fills rows left-to-right") {
    val arrangement = Layout.arrange(Vector.fill(6)(5), screenWidth = 19, Orientation.Horizontal, gap = 2)

    assertEquals(arrangement.columnWidths, Vector(5, 5, 5))
    assertEquals(arrangement.rows, Vector(Vector(0, 1, 2), Vector(3, 4, 5)))
  }

  test("a long entry does not force a single column") {
    val arrangement = Layout.arrange(mixedWidths, screenWidth = 80, Orientation.Vertical, gap = 2)

    assertEquals(arrangement.columnWidths.size, 4)
  }

  test("layout fits exactly at the terminal width") {
    val arrangement = Layout.arrange(Vector(10, 10), screenWidth = 22, Orientation.Horizontal, gap = 2)

    assertEquals(arrangement.columnWidths, Vector(10, 10))
    assertEquals(arrangement.rows, Vector(Vector(0, 1)))
  }

  test("too narrow for two columns falls back to one column") {
    val arrangement = Layout.arrange(Vector(10, 10), screenWidth = 21, Orientation.Vertical, gap = 2)

    assertEquals(arrangement.columnWidths, Vector(10))
    assertEquals(arrangement.rows, Vector(Vector(0), Vector(1)))
  }

  test("empty input produces an empty arrangement") {
    assertEquals(
      Layout.arrange(Vector.empty, screenWidth = 80, Orientation.Vertical),
      Arrangement(Vector.empty, Vector.empty),
    )
  }

  test("every entry appears exactly once") {
    for orientation <- List(Orientation.Horizontal, Orientation.Vertical) do
      val arrangement = Layout.arrange(mixedWidths, screenWidth = 80, orientation, gap = 2)
      assertEquals(arrangement.rows.flatten.sorted, mixedWidths.indices.toVector)
  }

  test("auto layout is vertical on a tty") {
    val config = Main.effectiveConfig(Config(), isTTY = true)

    assertEquals(config.layout, LayoutMode.vertical)
    assert(!config.oneLine)
  }

  test("auto layout is one entry per line when piped") {
    val config = Main.effectiveConfig(Config(), isTTY = false)

    assert(config.oneLine)
    assertEquals(config.layout, LayoutMode.auto)
  }

  test("explicit horizontal layout is kept when piped") {
    val config = Main.effectiveConfig(Config(layout = LayoutMode.horizontal), isTTY = false)

    assertEquals(config.layout, LayoutMode.horizontal)
    assert(!config.oneLine)
  }
end LayoutTests
