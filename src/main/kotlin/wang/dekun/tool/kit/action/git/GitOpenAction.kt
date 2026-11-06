package wang.dekun.tool.kit.action.git

import  wang.dekun.tool.kit.action.base.GitAnAction
import  wang.dekun.tool.kit.constants.CommandType
import  wang.dekun.tool.kit.constants.Constant
import com.intellij.openapi.actionSystem.AnActionEvent


/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


class GitOpenAction : GitAnAction() {

    override fun update(event: AnActionEvent) {
        if (event.place == Constant.WELCOME_SCREEN) {
            event.presentation.text = "Git-Open"
        } else {
            event.presentation.text = "Git-Open"
        }
        setEnableVisible(event, true)
    }

    override fun actionPerformed(event: AnActionEvent) {
        executeGitCommand(event, CommandType.GIT_OPEN_COMMAND)
    }
}