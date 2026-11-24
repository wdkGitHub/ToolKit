package wang.dekun.tool.kit.action.git

import wang.dekun.tool.kit.action.ExternalToolAction
import wang.dekun.tool.kit.app.ExternalApp


/**
 * 用 Fork 打开选中目录所属的 git 仓库。
 *
 * <p>action id：`blog.dekun.wang.fork`（欢迎界面）、`blog.dekun.wang.ForkAppProject`（项目内所有位置）。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class ForkAction : ExternalToolAction(ExternalApp.FORK)
