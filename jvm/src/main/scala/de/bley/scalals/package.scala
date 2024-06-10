package de.bley

package scalals:
  sealed trait Env

  object Env extends Env:
    inline def apply[T](inline f: Env ?=> T): T = f(using this)
