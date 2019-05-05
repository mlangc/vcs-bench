package com.github.mlangc.vcs.bench.util

import java.io.File
import java.nio.file.Files

import com.github.mlangc.vcs.bench.logging.ZioLogger
import org.apache.commons.io.FileUtils
import scalaz.zio.Managed
import scalaz.zio.Task
import scalaz.zio.UIO

trait TmpFilesSupport {
  private val logger = new ZioLogger[TmpFilesSupport]

  protected def useCustomTmpDir: Option[File] = None
  protected def keepTmpDirs: Boolean = false

  protected def getTmpTestDir: Managed[Throwable, File] =
    mkTmpDir.toManaged { dir =>
      if (keepTmpDirs) logger.debug(s"Not deleting temp test dir @ $dir")
      else UIO(FileUtils.deleteQuietly(dir))
    }

  private def mkTmpDir: Task[File] =
    Task {
      val prefix = "test-"

      useCustomTmpDir.map { customTmpDir =>
        Files.createTempDirectory(customTmpDir.toPath, prefix)
      }.getOrElse(Files.createTempDirectory(prefix))
    }.map(_.toFile)
}
