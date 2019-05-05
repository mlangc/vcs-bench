package com.github.mlangc.vcs.bench.convenience

import scalaz.zio.Task
import scalaz.zio.UIO
import scalaz.zio.ZIO
import scalaz.zio.blocking.Blocking
import scalaz.zio.blocking.blocking

package object zio {
  def blockingTask[A](a: => A): ZIO[Blocking, Throwable, A] = blocking(Task(a))
  def blockingUio[A](a: => A): ZIO[Blocking, Nothing, A] = blocking(UIO(a))
}
