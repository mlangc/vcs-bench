package com.github.mlangc.vcs.bench

import scalaz.zio.blocking.Blocking
import scalaz.zio.random.Random
import scalaz.zio.system

package object interpreter {
  type InterpreterEnv = Blocking with system.System with GlobalLock with Random
}
