package com.github.mlangc.vcs.bench

import zio.{Semaphore, UIO, ZIO}

trait GlobalLock {
  def globalLock: GlobalLock.Service
}

object GlobalLock {
  trait Service {
    def sequential[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
  }

  def make: UIO[GlobalLock.Service] =
    Semaphore.make(1).map { sem =>
      new Service {
        override def sequential[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
          sem.withPermit(zio)
      }
    }
}
