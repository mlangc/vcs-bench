package com.github.mlangc.vcs.bench.util.zio

import scalaz.zio.ZIO
import scalaz.zio.clock.Clock
import scalaz.zio.clock
import scalaz.zio.duration.Duration

object StopWatch {
  def timed[R, E, A](zio: ZIO[R, E, A]): ZIO[R with Clock, E, ResultWithDuration[A]] =
    for {
      nanosBefore <- clock.nanoTime
      res <- zio
      nanosAfter <- clock.nanoTime
    } yield ResultWithDuration(res, Duration.fromNanos(nanosAfter - nanosBefore))
}
