package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import com.github.mlangc.vcs.bench.logging.ZioLogger
import com.github.mlangc.vcs.bench.model.Lines
import com.github.mlangc.vcs.bench.model.Operation
import com.github.mlangc.vcs.bench.model.Path
import scalaz.zio.Task
import scalaz.zio.UIO

abstract class GenericSvnInterpreter(protected val projectDir: File) extends VcmInterpreter {
  private val logger = new ZioLogger[GenericSvnInterpreter]
  protected val trunkDir = new File(projectDir, "trunk")

  protected def svnMove(src: File, dst: File): Task[Unit]
  protected def svnCopy(src: File, dst: File): Task[Unit]
  protected def svnDelete(file: File): Task[Unit]
  protected def svnAdd(file: File): Task[Unit]
  protected def commit(message: String): Task[Unit]

  final protected def toFile(path: Path): File = {
    FsHelpers.toFile(trunkDir, path)
  }

  final protected def logSvnAdd(file: File): UIO[Unit] =
    logger.debug(s"svn add ${file.getPath}")

  final protected def logSvnCp(src: File, dst: File): UIO[Unit] =
    logger.debug(s"svn cp ${src.getPath} ${dst.getPath}")

  final protected def logSvnMv(src: File, dst: File): UIO[Unit] =
    logger.debug(s"svn mv ${src.getPath} ${dst.getPath}")

  final protected def logSvnDelete(file: File): UIO[Unit] =
    logger.debug(s"svn delete ${file.getPath}")

  final protected def logSvnCommit(message: String): UIO[Unit] =
    logger.debug(s"svn commit -m '$message'")

  final def run(op: Operation): Task[Unit] = op match {
    case Operation.Create(path, lines) =>
      val file = toFile(path)
      FsHelpers.createFile(file, lines).flatMap(svnAdd)

    case Operation.Commit(message) =>
      commit(message)

    case Operation.Copy(src, dst) =>
      svnCopy(src, dst)

    case Operation.Move(src, dst) =>
      svnMove(src, dst)

    case Operation.Delete(path) =>
      svnDelete(path)

    case Operation.Edit(path, apply) =>
      edit(path, apply)
  }

  private def svnCopy(src: Path, dst: Path): Task[Unit] =
    svnCopy(toFile(src), toFile(dst))

  private def svnMove(src: Path, dst: Path): Task[Unit] =
    svnMove(toFile(src), toFile(dst))

  private def svnDelete(path: Path): Task[Unit] =
    svnDelete(toFile(path))

  private def edit(path: Path, apply: Lines => Lines): Task[Unit] = {
    val file = toFile(path)
    FsHelpers.edit(file, apply) *> logger.debug(s"Edited ${file.getPath}")
  }
}
