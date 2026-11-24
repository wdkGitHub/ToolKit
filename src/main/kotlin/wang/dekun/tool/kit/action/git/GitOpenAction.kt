package wang.dekun.tool.kit.action.git

import wang.dekun.tool.kit.action.ExternalToolAction
import wang.dekun.tool.kit.app.ExternalApp


/**
 * 用浏览器打开选中目标所属仓库的远程地址。
 *
 * <p>action id：`blog.dekun.wang.git.open`（欢迎界面）、`blog.dekun.wang.GitOpenProject`（项目内所有位置）。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class GitOpenAction : ExternalToolAction(ExternalApp.BROWSER)
