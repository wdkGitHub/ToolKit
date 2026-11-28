package wang.dekun.tool.kit.ui

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 设置树中的 ToolKit 一级分组节点。
 *
 * <p>本身不承载配置项，仅通过 plugin.xml 中的 parentId 把
 * “ToolKit Config”“ToolKit Git Remote” 归为二级节点。
 * IntelliJ Platform 要求 applicationConfigurable 必须声明 instance，
 * 因此提供一个无状态的空实现。
 *
 * @author WangDeKun
 */
class ToolKitConfigurable : Configurable {

    override fun getDisplayName(): String = "ToolKit"

    override fun createComponent(): JComponent = JPanel(BorderLayout()).apply {
        add(JBLabel("请在左侧选择 ToolKit 下的具体配置项。").apply {
            border = JBUI.Borders.empty(16)
        }, BorderLayout.NORTH)
    }

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    override fun reset() = Unit

    override fun disposeUIResources() = Unit
}
