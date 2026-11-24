package wang.dekun.tool.kit.action

import wang.dekun.tool.kit.action.base.BaseAnAction
import wang.dekun.tool.kit.services.UnregisterActionService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent


/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


class ListenerAction : BaseAnAction() {

    override fun update(event: AnActionEvent) {
        event.presentation.text = "监控AnAction"
        if (UnregisterActionService.isEnableNotify()) {
            event.presentation.icon = AllIcons.General.InspectionsOKEmpty
        } else {
            event.presentation.icon = AllIcons.Actions.Close
        }
    }


    override fun actionPerformed(event: AnActionEvent) {
        UnregisterActionService.toggleNotify()
    }
}