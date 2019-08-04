package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import org.eclipse.jgit.api.Git
import zio.Task
import zio.ZManaged

import scala.collection.JavaConverters._

class JgitInterpreterTest extends GenericInterpreterTest {
  override protected def debug: Boolean = false

  protected def getInterpreter(testDir: File): ZManaged[InterpreterEnv, Throwable, VcmInterpreter] = {
    JgitInterpreter.init(testDir)
  }

  protected def mkNativeVcmInterface(basePath: File): Task[NativeVcmInterface] = {
    Task(Git.open(basePath)).map { git =>
      new NativeVcmInterface {
        def isProperlyInitialized: Task[Boolean] =
          Task(git.status().call().isClean)

        def untrackedFiles: Task[List[File]] =
          for {
            status <- Task(git.status().call())
            untracked <- Task(status.getUntracked.asScala.toList)
          } yield untracked.map(new File(basePath, _))

        def getNumCommits: Task[Int] =
          Task(git.log().call().asScala.size)
      }
    }
  }
}

