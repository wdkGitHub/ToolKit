package wang.dekun.tool.kit.action.git

import wang.dekun.tool.kit.action.ExternalToolAction
import wang.dekun.tool.kit.app.ExternalApp


/**
 * 用 SourceTree 打开选中目录所属的 git 仓库。
 *
 * <p>未安装 SourceTree 时该动作不会显示，不会出现点了没反应的死按钮。
 * <p>action id：`blog.dekun.wang.sourcetree`（欢迎界面）、`blog.dekun.wang.SourcetreeAppProject`（项目内所有位置）。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class SourcetreeAction : ExternalToolAction(ExternalApp.SOURCETREE)
