package wang.dekun.tool.kit.utils

import wang.dekun.tool.kit.constants.Constant
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project


/**
 * 通用工具方法。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class Utils {

    companion object {

        /**
         * 在 [Constant.NOTIFICATION_GROUP_AN_ACTION_ID] 通知组里弹出一条通知。
         *
         * @param project 当前项目，欢迎界面场景为 null
         * @param title   通知标题
         * @param content 通知内容
         */
        fun showNotificationAnActionId(
            project: Project?,
            title: String,
            content: String,
            type: NotificationType = NotificationType.INFORMATION,
        ) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(Constant.NOTIFICATION_GROUP_AN_ACTION_ID)
                .createNotification(title, content, type)
                .notify(project)
        }
    }
}
