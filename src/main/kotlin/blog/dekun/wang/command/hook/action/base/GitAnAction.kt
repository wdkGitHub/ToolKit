package blog.dekun.wang.command.hook.action.base

import blog.dekun.wang.command.hook.command.Command
import blog.dekun.wang.command.hook.constants.CommandType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager


/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


abstract class GitAnAction : WelcomeScreen, BaseAnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    fun setEnableVisible(event: AnActionEvent, checkRemote: Boolean = false) {
        super.enableVisible(event) {
            when (event.place) {
                "Vcs.Push.ContextMenu" -> {
                    return@enableVisible vcsPushContextMenuGitRepDir(event) != null
                }

                else -> {
                    val project = event.project ?: return@enableVisible false
                    val repoManager = GitRepositoryManager.getInstance(project)
                    for (dir in getSelectedDirectories(event)) {
                        val repo = repoManager.getRepositoryForFileQuick(dir) ?: return@enableVisible false
                        if (checkRemote && repo.remotes.isEmpty()) {
                            return@enableVisible false
                        }
                    }
                    return@enableVisible true
                }
            }
        }
    }


    fun executeGitCommand(event: AnActionEvent, commandType: CommandType) {
        val project = event.project ?: return
        when (event.place) {
            "Vcs.Push.ContextMenu" -> {
                vcsPushContextMenuGitRepDir(event)?.let {
                    Command.build().execute(it, commandType)
                }
            }

            else -> {
                val repoManager = GitRepositoryManager.getInstance(project)
                for (dir in getSelectedDirectories(event)) {
                    repoManager.getRepositoryForFileQuick(dir)?.let {
                        Command.build().execute(dir.path, commandType)
                    }
                }
            }
        }
    }


    fun getSelectedDirectories(event: AnActionEvent): List<VirtualFile> = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { it.isDirectory }.orEmpty()
}
