package com.github.mlangc.vcs.bench

import zio.blocking.Blocking
import zio.clock.Clock
import zio.random.Random
import zio.{UIO, system}

object Environments {
  def live: UIO[AppEnv] = GlobalLock.make.map { lock =>
    new GlobalLock with Clock.Live with Random.Live with Blocking.Live with system.System.Live {
      val globalLock: GlobalLock.Service = lock
    }
  }
}
