package com.github.mlangc.vcs.bench.logging

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import zio.UIO
import zio.ZIO
import zio.clock.Clock.Live.clock
import zio.duration.Duration

class ZioLogger[T : reflect.ClassTag] {
  private var cache: Logger = _

  private val logger: UIO[Logger] = UIO {
    if (cache != null) cache else {
      val clazz = implicitly[reflect.ClassTag[T]].runtimeClass
      cache = LoggerFactory.getLogger(clazz)
      cache
    }
  }

  private def withLogger(enabled: Logger => Boolean, op: Logger => Unit): UIO[Unit] =
    logger.flatMap { logger =>
      UIO {
        if (enabled(logger)) op(logger)
        else ()
      }
    }

  private def isDebugEnabled: UIO[Boolean] =
    logger.flatMap(logger => UIO(logger.isDebugEnabled))

  def debug(msg: => String): UIO[Unit] = withLogger(_.isDebugEnabled, _.debug(msg))
  def info(msg: => String): UIO[Unit] = withLogger(_.isInfoEnabled, _.info(msg))
  def warn(msg: => String): UIO[Unit] = withLogger(_.isWarnEnabled, _.warn(msg))
  def warn(msg: => String, th: => Throwable): UIO[Unit] = withLogger(_.isWarnEnabled, _.warn(msg,th))

  def logExecutionTime[R, E, A](what: => String)(io: ZIO[R, E, A]): ZIO[R, E, A] =
    isDebugEnabled.flatMap {
      case false => io

      case true =>
        for {
          nanosBefore <- clock.nanoTime
          a <- io
          nanosAfter <- clock.nanoTime
          millis = Duration(nanosAfter - nanosBefore, TimeUnit.NANOSECONDS).toMillis
          _ <- debug(f"$millis%7dms for executing '$what'")
        } yield a
    }
}

