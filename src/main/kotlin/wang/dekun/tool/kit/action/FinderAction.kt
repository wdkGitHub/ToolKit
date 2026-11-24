package wang.dekun.tool.kit.action

import wang.dekun.tool.kit.app.ExternalApp


/**
 * 在访达中定位选中项：选中文件时打开所在目录并选中它，选中目录时直接打开该目录。
 *
 * <p>action id：欢迎界面 `blog.dekun.wang.finder`，项目内 `blog.dekun.wang.FinderAppProject`。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class FinderAction : ExternalToolAction(ExternalApp.FINDER)
