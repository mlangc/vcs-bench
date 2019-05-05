package com.github.mlangc.vcs.bench.interpreter
import java.io.File

import com.github.mlangc.vcs.bench.GlobalLock
import com.github.mlangc.vcs.bench.logging.ZioLogger
import org.apache.commons.io.FileUtils
import org.tigris.subversion.svnclientadapter._
import org.tigris.subversion.svnclientadapter.commandline.CmdLineClientAdapterFactory
import org.tigris.subversion.svnclientadapter.javahl.JhlClientAdapterFactory
import org.tigris.subversion.svnclientadapter.svnkit.SvnKitClientAdapterFactory
import scalaz.zio._

object SvnClientAdapterInterpreter {
  sealed abstract class Flavour(val clientType: String)

  object Flavour {
    case object JavaHl extends Flavour(JhlClientAdapterFactory.JAVAHL_CLIENT)
    case object SvnKit extends Flavour(SvnKitClientAdapterFactory.SVNKIT_CLIENT)
    case object Cli extends Flavour(CmdLineClientAdapterFactory.COMMANDLINE_CLIENT)
  }

  private val logger = new ZioLogger[SvnClientAdapterInterpreter]

  def init(projectDir: File, repoDir: File, flavour: Flavour)
  : ZManaged[InterpreterEnv, Throwable, SvnClientAdapterInterpreter] = ZManaged.fromEffect {
    for {
      _ <- doClientAdapterFactorySetup
      clientAdapter <- Task(SVNClientAdapterFactory.createSVNClient(flavour.clientType))
      _ <- Task(clientAdapter.createRepository(repoDir, ISVNClientAdapter.REPOSITORY_FSTYPE_FSFS))
      _ <- logger.debug(s"Created SVN repository at ${repoDir.getPath}")
      _ <- Task(clientAdapter.checkout(toSvnUrl(repoDir), projectDir, SVNRevision.HEAD, true))
    } yield new SvnClientAdapterInterpreter(projectDir, clientAdapter)
  }

  private[interpreter] def doClientAdapterFactorySetup: TaskR[GlobalLock, Unit] =
    ZIO.accessM[GlobalLock] { env =>
      env.globalLock.sequential {
        Task {
          if (!SVNClientAdapterFactory.isSVNClientAvailable(Flavour.JavaHl.clientType)) {
            try {
              ignoreAlreadyRegisteredException(JhlClientAdapterFactory.setup())
            } catch {
              case e: SVNClientException =>
                val loadErrors = JhlClientAdapterFactory.getLibraryLoadErrors
                throw new IllegalStateException(s"Cannot setup JhlClientAdapterFactory\n$loadErrors", e)
            }
          }
        } *> Task {
          if (!SVNClientAdapterFactory.isSVNClientAvailable(Flavour.SvnKit.clientType))
            ignoreAlreadyRegisteredException(SvnKitClientAdapterFactory.setup())
        } *> Task {
          if (!SVNClientAdapterFactory.isSVNClientAvailable(Flavour.Cli.clientType))
            ignoreAlreadyRegisteredException(CmdLineClientAdapterFactory.setup())
        }
      }
    }

  private def ignoreAlreadyRegisteredException(thunk: => Unit): Unit = {
    try {
      thunk
    } catch {
      case e: SVNClientException if e.getMessage != null =>
        if (!e.getMessage.contains("already registered"))
          throw e
    }
  }

  private def toSvnUrl(file: File): SVNUrl = {
    new SVNUrl(file.toURI.toString.replace("file:/", "file:///"))
  }
}

class SvnClientAdapterInterpreter private(projectDir: File, clientAdapter: ISVNClientAdapter) extends GenericSvnInterpreter(projectDir) {
  protected def svnAdd(file: File): Task[Unit] =
    Task(clientAdapter.addFile(file)) *> logSvnAdd(file)

  protected def svnMove(src: File, dst: File): Task[Unit] = {
    val mkDirs = Task(FileUtils.forceMkdirParent(dst))
    val svnAddDir = svnAdd(dst.getParentFile)
    val svnMv = Task(clientAdapter.move(src, dst, false))
    val log = logSvnMv(src, dst)

    mkDirs *> svnAddDir *> svnMv *> log
  }

  protected def svnCopy(src: File, dst: File): Task[Unit] = {
    val mkDirs = Task(FileUtils.forceMkdirParent(dst))
    val svnCp = Task(clientAdapter.copy(src, dst))
    val log = logSvnCp(src, dst)

    mkDirs *> svnCp *> log
  }

  protected def svnDelete(file: File): Task[Unit] =
    Task(clientAdapter.remove(Array(file), true)) *> logSvnDelete(file)

  protected def commit(message: String): Task[Unit] =
    Task(clientAdapter.commit(Array(projectDir), message, true)) *> logSvnCommit(message)
}
