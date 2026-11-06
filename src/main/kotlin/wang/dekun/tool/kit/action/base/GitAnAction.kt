package wang.dekun.tool.kit.action.base

import  wang.dekun.tool.kit.command.Command
import  wang.dekun.tool.kit.constants.CommandType
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
                "Vcs.Push.ContextMenu", "ChangesViewPopup" -> {
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
//        println(event.place)
//        val virtualFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { it.isDirectory }.orEmpty()
//        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
//        val directoryPath = when {
//            file == null -> null
//            file.isDirectory -> file.path
//            else -> file.parent?.path
//        }
//        println(directoryPath)
        when (event.place) {
            "Vcs.Push.ContextMenu", "ChangesViewPopup" -> {
                vcsPushContextMenuGitRepDir(event)?.let {
                    Command.build().execute(it, commandType)
                }
            }

            else -> {
                val repoManager = GitRepositoryManager.getInstance(project)
                val selectedDirectories = getSelectedDirectories(event)
                println("selectedDirectories: $selectedDirectories")
                for (dir in selectedDirectories) {
                    repoManager.getRepositoryForFileQuick(dir)?.let {
                        Command.build().execute(dir.path, commandType)
                    }
                }
            }
        }
        //PluginManagerCore
    }

    // 刷新
    // com.intellij.openapi.vcs.changes.actions.RefreshAction
    // RefreshAction.doRefresh(project)
    fun getSelectedDirectories(event: AnActionEvent): List<VirtualFile> = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { it.isDirectory }.orEmpty()
}
