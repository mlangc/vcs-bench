package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import com.github.mlangc.vcs.bench.logging.ZioLogger
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.wc.ISVNStatusHandler
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNStatus
import zio.Managed
import zio.Task
import zio.UIO
import zio.ZIO

class SvnKitInterpreterTest extends GenericSvnInterpreterTest {
  private val logger = new ZioLogger[GenericInterpreterTest]

  override protected def debug: Boolean = true
  override protected def lenOfRandomHistory: Int = 50
  override protected val useCustomTmpDir: Option[File] = None

  protected def getInterpreter(projectDir: File): Managed[Throwable, VcmInterpreter] = {
    getTmpTestDir.flatMap { repoDir =>
      SvnKitInterpreter.init(projectDir, repoDir)
    }
  }

  protected def mkNativeVcmInterface(basePath: File): Task[NativeVcmInterface] =
    for {
      manager <- Task(SVNClientManager.newInstance())
      statusClient <- Task(manager.getStatusClient)
      baseUrl = SVNURL.fromFile(basePath)
    } yield {
      new NativeVcmInterface {
        def isProperlyInitialized: UIO[Boolean] =
          Task(statusClient.doStatus(basePath, false)).foldM(
            err => logger.warn("Could not verify repository", err).const(false),
            _ => ZIO.succeed(true)
          )

        def untrackedFiles: Task[List[File]] = Task {
          val revision = SVNRevision.HEAD
          val depth = SVNDepth.INFINITY
          val listBuffer = List.newBuilder[File]

          val statusHandler: ISVNStatusHandler = (status: SVNStatus) => {
            if (!status.isVersioned)
              listBuffer += status.getFile
          }

          statusClient.doStatus(
            basePath, revision, depth, false,
            false, false, false,
            statusHandler, null)

          listBuffer.result()
        }

        def getNumCommits: Task[Int] =
          for {
            info <- Task(statusClient.doStatus(basePath, true))
            numCommits <- Task(Math.toIntExact(info.getRemoteRevision.getNumber))
          } yield numCommits
      }
    }
}

