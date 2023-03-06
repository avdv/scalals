package de.bley.scalals

class UtilsTests extends munit.FunSuite:
  import Utils.glob

  test("glob - simple") {
    assert(glob("abc", "abc"))
  }
end UtilsTests
