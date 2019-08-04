package com.github.mlangc.vcs.bench.interpreter

import java.io.File

import com.github.mlangc.vcs.bench.logging.ZioLogger
import org.apache.commons.io.FileUtils
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNUpdateClient
import org.tmatesoft.svn.core.wc.SVNWCClient
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient
import zio.Managed
import zio.Task

object SvnKitInterpreter {
  private val logger = new ZioLogger[SvnKitInterpreter]

  def init(projectDir: File, repoDir: File): Managed[Throwable, SvnKitInterpreter] = {
    val createInterpreter = for {
      clientManger <- Task(SVNClientManager.newInstance())
      adminClient <- Task(clientManger.getAdminClient)
      updateClient <- Task(clientManger.getUpdateClient)
      repoUrl <- initRepoDir(repoDir, adminClient)
      revision <- initProjectDir(projectDir, repoUrl, updateClient)
      _ <- logger.debug(s"Checkout out revision $revision from $repoUrl at $projectDir")
    } yield new SvnKitInterpreter(projectDir, clientManger)

    Managed.fromEffect(createInterpreter)
  }

  private def initRepoDir(repoDir: File, adminClient: SVNAdminClient): Task[SVNURL] = Task {
    adminClient.doCreateRepository(repoDir, null, false, false)
  }

  private def initProjectDir(projectDir: File, repoUrl: SVNURL, updateClient: SVNUpdateClient): Task[Long] = Task {
    updateClient.doCheckout(repoUrl, projectDir, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false)
  }
}

class SvnKitInterpreter private(projectDir: File, clientManager: SVNClientManager) extends GenericSvnInterpreter(projectDir) {
  private val logger = new ZioLogger[SvnKitInterpreter]

  protected def svnMove(src: File, dst: File): Task[Unit] = {
    val mkDstDir = Task(FileUtils.forceMkdirParent(dst))
    val addDstDir = svnAdd(dst.getParentFile)
    val moveFile = logger.logExecutionTime("move file")(Task(FileUtils.moveFile(src, dst)))
    val svnMv = logger.logExecutionTime("svn mv")(Task(clientManager.getMoveClient.doVirtualCopy(src, dst, true)))
    val log = logSvnMv(src, dst)

    mkDstDir *> addDstDir *> moveFile *> svnMv *> log
  }

  protected def svnCopy(src: File, dst: File): Task[Unit] = {
    val mkDstDir = Task(FileUtils.forceMkdirParent(dst))
    val addDstDir = svnAdd(dst.getParentFile)
    val copyFile = logger.logExecutionTime("copy file")(Task(FileUtils.copyFile(src, dst)))
    val svnCp = logger.logExecutionTime("svn cp")(Task(clientManager.getMoveClient.doVirtualCopy(src, dst, false)))
    val log = logSvnCp(src, dst)

    mkDstDir *> addDstDir *> copyFile *> svnCp *> log
  }

  protected def svnDelete(file: File): Task[Unit] =
    for {
      _ <- svnRevert(file)
      underVersionControl <- isUnderVersionControl(file)
      _ <- {
        if (!underVersionControl) Task(FileUtils.deleteQuietly(file)) *> logger.debug(s"Delete ${file.getPath}")
        else svnDoDelete(file) *> logSvnDelete(file)
      }
    } yield ()

  protected def svnAdd(file: File): Task[Unit] = logger.logExecutionTime("svn add") {
    withWcClient(_.doAdd(file, false,
      false, true, SVNDepth.INFINITY,
      false, true)
    ) *> logSvnAdd(file)
  }

  protected def commit(message: String): Task[Unit] = logger.logExecutionTime("commit") {
    Task {
      clientManager.getCommitClient.doCommit(
        Array(projectDir), false, message, null,
        null, true, false, SVNDepth.INFINITY)
    } *> logSvnCommit(message)
  }

  private def svnDoDelete(path: File): Task[Unit] = logger.logExecutionTime("svn delete") {
    Task {
      clientManager.getWCClient.doDelete(path, false, false)
    }
  }

  private def isUnderVersionControl(path: File): Task[Boolean] = logger.logExecutionTime("is under version control") {
    Task {
      Option(clientManager.getStatusClient.doStatus(path, false)).exists(_.isVersioned)
    }
  }

  private def svnRevert(path: File): Task[Unit] = logger.logExecutionTime("svn revert") {
    Task {
      clientManager.getWCClient.doRevert(Array(path), SVNDepth.EMPTY, null)
    }
  }

  private def withWcClient[T](op: SVNWCClient => T): Task[T] =
    Task(op(clientManager.getWCClient))

}
