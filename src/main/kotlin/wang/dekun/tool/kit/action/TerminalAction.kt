package wang.dekun.tool.kit.action

import wang.dekun.tool.kit.app.ExternalApp


/**
 * 用 iTerm2（缺失时回退 Terminal.app）打开选中目录。
 *
 * <p>action id：`blog.dekun.wang.terminal`（欢迎界面）、`blog.dekun.wang.ITerm2`（项目内所有位置）。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class TerminalAction : ExternalToolAction(ExternalApp.ITERM2)
