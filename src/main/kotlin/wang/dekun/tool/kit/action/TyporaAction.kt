package wang.dekun.tool.kit.action

import  wang.dekun.tool.kit.command.Command
import  wang.dekun.tool.kit.constants.CommandType
import  wang.dekun.tool.kit.constants.Constant
import  wang.dekun.tool.kit.utils.Utils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent


/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


class TyporaAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        if (event.place == Constant.WELCOME_SCREEN) {
            event.presentation.text = "Reveal In Typora"
        } else {
            event.presentation.text = "Reveal In Typora"
        }
        event.presentation.isEnabledAndVisible = Utils.checkMarkdownFile(event)
    }

    override fun actionPerformed(event: AnActionEvent) {
        Utils.getVirtualFile(event)?.path?.let { Command.build().execute(it, CommandType.TYPORA_APP) }
    }
}
