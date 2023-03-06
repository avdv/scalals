package de.bley

package scalals {
  sealed trait Env
}

package object scalals:
  object EmptyEnv extends Env

  @inline
  def Env[T](f: Env => T) = f(EmptyEnv)
