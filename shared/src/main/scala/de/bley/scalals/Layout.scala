package de.bley.scalals

/** Computes how to arrange a short listing into columns.
  *
  * The layout rules mirror colorls: `Vertical` fills down each column, while `Horizontal` fills across each row. The
  * widest column count that fits the terminal is used.
  */
object Layout:
  enum Orientation:
    case Horizontal, Vertical

  final case class Arrangement(rows: Vector[Vector[Int]], columnWidths: Vector[Int])

  def arrange(
      widths: IndexedSeq[Int],
      screenWidth: Int,
      orientation: Orientation,
      gap: Int = 2,
  ): Arrangement =
    val n = widths.size

    if n == 0 then Arrangement(Vector.empty, Vector.empty)
    else
      val screen = math.max(1, screenWidth)
      val minWidth = math.max(1, widths.min)
      val maxColumns =
        val byWidth = (screen + gap) / (minWidth + gap)
        math.min(n, math.max(1, byWidth))

      def layoutFor(columns: Int): Option[(Int, Vector[Int])] =
        val (chunkSize, columnWidths) = orientation match
          case Orientation.Horizontal =>
            val paddedRows = widths
              .grouped(columns)
              .map { row =>
                if row.size == columns then row else row ++ Vector.fill(columns - row.size)(0)
              }
              .toVector
            val columnWidths = Vector.tabulate(columns)(column => paddedRows.map(_(column)).max)
            (columns, columnWidths)

          case Orientation.Vertical =>
            val rows = (n + columns - 1) / columns
            val columnWidths = widths.grouped(rows).map(_.max).toVector
            (rows, columnWidths)

        val totalWidth = columnWidths.sum + gap * (columnWidths.size - 1)
        Option.when(totalWidth <= screen)((chunkSize, columnWidths))
      end layoutFor

      val found = Iterator.range(maxColumns, 0, -1).map(layoutFor).collectFirst { case Some(layout) => layout }

      // Searching downwards also works for vertical layouts: shrinking the requested column count never increases the
      // actual number of columns produced by grouping into rows.

      found match
        case Some((chunkSize, columnWidths)) =>
          val rows = orientation match
            case Orientation.Horizontal =>
              widths.indices.grouped(chunkSize).map(_.toVector).toVector

            case Orientation.Vertical =>
              val columns = widths.indices.grouped(chunkSize).map(_.toVector).toVector
              val rowCount = columns.map(_.size).max
              Vector.tabulate(rowCount)(row => columns.flatMap(_.lift(row)))

          Arrangement(rows, columnWidths)

        case None =>
          Arrangement(widths.indices.map(index => Vector(index)).toVector, Vector(widths.max))
      end match
    end if
  end arrange
end Layout
