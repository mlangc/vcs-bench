package com.github.mlangc.vcs

import scalaz.zio.ZIO
import scalaz.zio.clock.Clock
import scalaz.zio.random.Random

package object bench {
  type UIOR[R, A] = ZIO[R, Nothing, A]
  type AppEnv = interpreter.InterpreterEnv with Random with Clock
}
