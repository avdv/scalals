package de.bley.scalals

import scalanative.unsafe.*

@extern
object locale {
  @name("scalanative_locale_lc_all")
  def LC_ALL: CInt = extern

  @name("scalanative_locale_lc_collate")
  def LC_COLLATE: CInt = extern

  @name("scalanative_locale_lc_messages")
  def LC_MESSAGES: CInt = extern

  @name("scalanative_locale_lc_numeric")
  def LC_NUMERIC: CInt = extern

  def setlocale(category: CInt, locale: CString): CString = extern

  // int strcoll(const char *s1, const char *s2);
  def strcoll(a: CString, b: CString): CInt = extern
}
