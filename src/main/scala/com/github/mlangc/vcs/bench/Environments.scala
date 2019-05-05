package com.github.mlangc.vcs.bench

import scalaz.zio.blocking.Blocking
import scalaz.zio.clock.Clock
import scalaz.zio.random.Random
import scalaz.zio.{UIO, system}

object Environments {
  def live: UIO[AppEnv] = GlobalLock.make.map { lock =>
    new GlobalLock with Clock.Live with Random.Live with Blocking.Live with system.System.Live {
      val globalLock = lock
    }
  }
}
