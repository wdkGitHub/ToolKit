package wang.dekun.tool.kit.action.base

import  wang.dekun.tool.kit.command.Command
import  wang.dekun.tool.kit.constants.Constant
import  wang.dekun.tool.kit.utils.Utils
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vcs.VcsDataKeys
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


interface WelcomeScreen {

    fun enableVisible(event: AnActionEvent, visible: () -> Boolean) {
        val isSupported = Command.isSupport()
        val visibleInvoke = visible()
        event.presentation.apply {
            isVisible = when (event.place) {
                Constant.WELCOME_SCREEN -> Utils.isRecentProjectItem(event)
                else -> isSupported
            }
            isEnabled = when (event.place) {
                Constant.WELCOME_SCREEN -> Utils.isRecentProjectItem(event)
                else -> isSupported && visibleInvoke
            }
        }
    }

    fun vcsPushContextMenuGitRepDir(event: AnActionEvent): String? {
        val project = event.project ?: return null
        val gitRepositoryManager = GitRepositoryManager.getInstance(project)
        val data = event.getData(VcsDataKeys.CHANGES_SELECTION)
        val firstFile = data?.list?.firstOrNull()?.afterRevision?.file ?: return null
        return runBlocking { // 确保在非 UI 线程（EDT）获取仓库路径
            withContext(Dispatchers.IO) {
                ReadAction.compute<String?, RuntimeException> {
                    gitRepositoryManager.getRepositoryForFile(firstFile)?.root?.path
                }
            }
        }
    }
}


