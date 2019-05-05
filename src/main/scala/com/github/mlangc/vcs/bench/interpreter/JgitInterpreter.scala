package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import com.github.mlangc.vcs.bench.logging.ZioLogger
import com.github.mlangc.vcs.bench.model.Lines
import com.github.mlangc.vcs.bench.model.Operation
import com.github.mlangc.vcs.bench.model.Path
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import scalaz.zio.Task
import scalaz.zio.TaskR
import scalaz.zio.UIO
import scalaz.zio.ZManaged

object JgitInterpreter {
  def init(dir: File): ZManaged[InterpreterEnv, Throwable, JgitInterpreter] = {
    val gitOpen = Task(Git.open(dir))
    val gitInit = Task(Git.init().setDirectory(dir).call())

    gitOpen.orElse(gitInit *> gitOpen)
      .toManaged(repo => UIO(repo.close()))
      .map(new JgitInterpreter(_, dir))
  }
}

class JgitInterpreter(repo: Git, projectDir: File) extends VcmInterpreter {
  private val logger = new ZioLogger[JgitInterpreter]

  def run(op: Operation): TaskR[InterpreterEnv, Unit] = op match {
    case Operation.Commit(message) => Task {
      repo.commit()
        .setMessage(message)
        .setAuthor("Hanelore Testkraft-Karrenbauer", "hanna-testkraft@karrenbauer.at")
        .call()
      }.unit

    case Operation.Create(path, lines) =>
      createFile(path, lines) *> gitAdd(path)

    case Operation.Copy(src, dst) =>
      copy(projectDir, src, dst) *> gitAdd(dst)

    case Operation.Move(src, dst) =>
      move(projectDir, src, dst) *> gitRm(src) *> gitAdd(dst)

    case Operation.Delete(path) =>
      delete(projectDir, path) *> gitRm(path)

    case Operation.Edit(path, apply) =>
      edit(projectDir, path, apply) *> gitAdd(path)
  }

  private def gitAdd(path: Path): Task[Unit] = {
    val filepattern = path.toList.mkString("/")
    Task(repo.add().addFilepattern(filepattern).call()) *> logger.debug(s"git add $filepattern")
  }

  private def gitRm(path: Path): Task[Unit] = {
    val filepattern = path.toList.mkString("/")
    Task(repo.rm().addFilepattern(filepattern).call()) *> logger.debug(s"git rm $filepattern")
  }

  private def delete(baseDir: File, path: Path): Task[Unit] = Task {
    FileUtils.forceDelete(FsHelpers.toFile(baseDir, path))
  }

  private def copy(baseDir: File, src: Path, dst: Path): Task[Unit] = Task {
    FileUtils.copyFile(FsHelpers.toFile(baseDir, src), FsHelpers.toFile(baseDir, dst))
  }

  private def move(baseDir: File, src: Path, dst: Path): Task[Unit] = Task {
    FileUtils.moveFile(FsHelpers.toFile(baseDir, src), FsHelpers.toFile(baseDir, dst))
  }

  private def toFile(path: Path): File = {
    FsHelpers.toFile(projectDir, path)
  }

  private def createFile(path: Path, lines: Lines): TaskR[InterpreterEnv, File] = {
    FsHelpers.createFile(toFile(path), lines)
  }

  def edit(baseDir: File, path: Path, apply: Lines => Lines): TaskR[InterpreterEnv, Unit] = {
    val file = FsHelpers.toFile(baseDir, path)
    FsHelpers.edit(file, apply)
  }
}
