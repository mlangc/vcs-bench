package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import org.tigris.subversion.svnclientadapter.{SVNClientAdapterFactory, SVNStatusKind}
import scalaz.zio.Task
import scalaz.zio.TaskR
import scalaz.zio.ZManaged

abstract class SvnClientAdapterInterpreterTest extends GenericSvnInterpreterTest {
  override protected def debug: Boolean = false
  override protected val useCustomTmpDir = None

  protected def flavour: SvnClientAdapterInterpreter.Flavour

  protected def getInterpreter(projectDir: File): ZManaged[InterpreterEnv, Throwable, VcmInterpreter] = {
    getTmpTestDir.flatMap { repoDir =>
      SvnClientAdapterInterpreter.init(projectDir, repoDir, flavour)
    }
  }

  protected def mkNativeVcmInterface(basePath: File): TaskR[InterpreterEnv, NativeVcmInterface] =
    for {
      _ <- SvnClientAdapterInterpreter.doClientAdapterFactorySetup
      clientAdapter <- Task(SVNClientAdapterFactory.createSVNClient(flavour.clientType))
    } yield {
      new NativeVcmInterface {
        def isProperlyInitialized: Task[Boolean] = Task {
          clientAdapter.getInfo(basePath).getRepository ne null
        }

        def untrackedFiles: Task[List[File]] = Task {
          clientAdapter.getStatus(basePath, true, false)
            .filter(_.getPropStatus == SVNStatusKind.MISSING)
            .map(_.getFile)
            .toList
        }

        def getNumCommits: Task[Int] = Task {
          Math.toIntExact {
            clientAdapter.getInfo(basePath, true)
              .filter(_ != null)
              .filter(_.getRevision != null)
              .map(_.getRevision.getNumber)
              .reduceOption(_ max _)
              .getOrElse(0L)
          }
        }
      }
    }
}

