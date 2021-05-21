package de.bley.scalals

import utest._

object UtilsTests extends TestSuite {
  import Utils.glob

  val tests = Tests {
    test("glob - simple") {
      assert(glob("abc", "abc"))
    }
  }
}