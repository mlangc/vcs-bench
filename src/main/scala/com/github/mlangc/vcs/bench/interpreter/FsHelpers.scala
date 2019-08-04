package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import cats.data.Chain
import com.github.mlangc.vcs.bench.model.Lines
import com.github.mlangc.vcs.bench.model.Path
import org.apache.commons.io.FileUtils
import zio.Task
import zio.ZIO

import scala.collection.JavaConverters._

object FsHelpers {
  def createFile(file: File, lines: Lines): Task[File] = {
    val mkDirs = Task(file.getParentFile.mkdirs())
    val writeFile = Task(FileUtils.writeStringToFile(file, lines.iterator.mkString("\n"), "UTF-8"))
    mkDirs *> writeFile *> ZIO.succeed(file)
  }

  def toFile(baseDir: File, path: Path): File = {
    path.foldLeft(baseDir)((file, component) => new File(file, component.value))
  }

  def edit(file: File, apply: Lines => Lines): Task[Unit] =
    for {
      origLines <- Task(Chain.fromSeq(FileUtils.readLines(file, "UTF-8").asScala))
      newLines = apply(origLines)
      _ <- Task(FileUtils.writeLines(file, "UTF-8", newLines.iterator.toSeq.asJava, "\n", false))
    } yield ()
}
