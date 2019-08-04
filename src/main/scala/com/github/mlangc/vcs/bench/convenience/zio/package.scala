package com.github.mlangc.vcs.bench.convenience

import _root_.zio.Task
import _root_.zio.UIO
import _root_.zio.ZIO
import _root_.zio.blocking.Blocking
import _root_.zio.blocking.blocking

package object zio {
  def blockingTask[A](a: => A): ZIO[Blocking, Throwable, A] = blocking(Task(a))
  def blockingUio[A](a: => A): ZIO[Blocking, Nothing, A] = blocking(UIO(a))
}
