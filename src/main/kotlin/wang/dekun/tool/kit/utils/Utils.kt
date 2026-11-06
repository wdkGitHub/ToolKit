package wang.dekun.tool.kit.utils

import  wang.dekun.tool.kit.constants.Constant
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


class Utils {

    companion object {

        private const val RECENT_PROJECT_SELECTED_ITEM = "RECENT_PROJECT_SELECTED_ITEM"


        /**
         * ProjectsGroupItem
         * RecentProjectItem
         * @see com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RecentProjectsWelcomeScreenActionBase
         */
        fun isRecentProjectItem(event: AnActionEvent): Boolean {
            val recentProjectSelectedItem = event.getData(DataKey.create<Any>(RECENT_PROJECT_SELECTED_ITEM))
            return recentProjectSelectedItem != null && recentProjectSelectedItem::class.java.simpleName == "RecentProjectItem"
        }

        fun getProjectPath(event: AnActionEvent): String? {
            val recentProjectSelectedItem = event.getData(DataKey.create<Any>(RECENT_PROJECT_SELECTED_ITEM)) ?: return null
            val projectPathField = recentProjectSelectedItem::class.java.getField("projectPath")
            return projectPathField[recentProjectSelectedItem].toString()
        }

        /**
         * 判断当前选中的文件是否是markdown文件
         */
        fun checkMarkdownFile(event: AnActionEvent): Boolean {
            val psiFile = event.getData(PlatformDataKeys.PSI_FILE) ?: return false
            return !psiFile.isDirectory && psiFile.name.endsWith(".md")
        }


        fun getVirtualFile(event: AnActionEvent): VirtualFile? {
            return event.getData(PlatformDataKeys.VIRTUAL_FILE)
        }


        fun showNotificationAnActionId(project: Project, title: String, content: String) {
            NotificationGroupManager.getInstance().getNotificationGroup(Constant.NOTIFICATION_GROUP_AN_ACTION_ID)
                .createNotification(title, content, NotificationType.INFORMATION)
                .notify(project)
        }
    }
}