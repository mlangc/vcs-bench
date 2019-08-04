package com.github.mlangc.vcs.bench

import zio.blocking.Blocking
import zio.random.Random
import zio.system

package object interpreter {
  type InterpreterEnv = Blocking with system.System with GlobalLock with Random
}
