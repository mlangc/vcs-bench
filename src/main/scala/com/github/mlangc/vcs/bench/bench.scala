package com.github.mlangc.vcs

import zio.ZIO
import zio.clock.Clock
import zio.random.Random

package object bench {
  type UIOR[R, A] = ZIO[R, Nothing, A]
  type AppEnv = interpreter.InterpreterEnv with Random with Clock
}
