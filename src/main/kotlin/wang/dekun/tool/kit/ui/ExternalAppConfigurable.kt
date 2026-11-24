package wang.dekun.tool.kit.ui

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import wang.dekun.tool.kit.app.AppInstallerDetector
import wang.dekun.tool.kit.settings.ExternalAppSettings
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel


/**
 * 设置页：配置“某个后缀的文件用哪个外置软件打开”。
 *
 * <p>表格两列：文件后缀、应用程序。应用程序列是一个可编辑下拉框，
 * 候选来自标准应用目录下扫描到的 .app；也可以直接输入 .app 绝对路径或不带后缀的应用名。
 *
 * <p>配置保存后，项目树 / 编辑器 / 变更窗口的右键菜单里会出现
 * `Open With &lt;应用名&gt;`，只对配置过的后缀可见。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class ExternalAppConfigurable : Configurable {

    private val tableModel = object : DefaultTableModel(arrayOf("文件后缀", "应用程序（.app 路径或应用名）"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }

    private val table = JBTable(tableModel)

    /** 已保存的快照，用于 isModified 比较 */
    private var appliedMappings: List<Pair<String, String>> = emptyList()

    override fun getDisplayName(): String = "外部应用打开方式"

    override fun createComponent(): JComponent {
        appliedMappings = ExternalAppSettings.getInstance().snapshot()
        fillTable(appliedMappings)

        val appComboBox = JComboBox<String>().apply {
            isEditable = true
            toolTipText = "从已安装应用中选择，或直接输入 .app 路径 / 应用名"
        }
        table.columnModel.getColumn(1).apply {
            cellEditor = AppCellEditor(appComboBox)
            cellRenderer = AppPathRenderer()
        }
        table.rowHeight = 24
        table.setShowGrid(false)
        // 点“确定/应用”时输入框会失去焦点，必须显式结束编辑，否则最后编辑的单元格不会写回模型
        table.putClientProperty("terminateEditOnFocusLost", true)

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                tableModel.addRow(arrayOf<Any?>("", ""))
                val row = tableModel.rowCount - 1
                table.editCellAt(row, 0)
                table.requestFocusInWindow()
            }
            .setRemoveAction {
                table.selectedRows.sortedDescending().forEach { tableModel.removeRow(it) }
            }
            .setEditAction(null)

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(
                JBLabel("为指定后缀的文件配置外置应用；配置后右键菜单会多出 “Open With 应用名”。").apply {
                    border = JBUI.Borders.emptyBottom(8)
                },
                BorderLayout.NORTH,
            )
            add(decorator.createPanel(), BorderLayout.CENTER)
        }
    }

    override fun isModified(): Boolean = readMappings() != appliedMappings

    override fun apply() {
        val mappings = readMappings()
        ExternalAppSettings.getInstance().replaceAll(mappings)
        appliedMappings = mappings
    }

    override fun reset() {
        appliedMappings = ExternalAppSettings.getInstance().snapshot()
        fillTable(appliedMappings)
    }

    private fun fillTable(mappings: List<Pair<String, String>>) {
        tableModel.rowCount = 0
        mappings.forEach { (extension, appPath) ->
            tableModel.addRow(arrayOf<Any?>(extension, appPath))
        }
    }

    /**
     * 读取表格内容并做规范化：去点号、转小写、去空白、丢弃空行、同后缀以最后一行为准。
     */
    private fun readMappings(): List<Pair<String, String>> {
        val result = LinkedHashMap<String, String>()
        for (row in 0 until tableModel.rowCount) {
            val extension = (tableModel.getValueAt(row, 0) as? String)
                ?.trim()
                ?.removePrefix(".")
                ?.lowercase()
                .orEmpty()
            val appPath = (tableModel.getValueAt(row, 1) as? String)?.trim().orEmpty()
            if (extension.isEmpty() || appPath.isEmpty()) {
                continue
            }
            result[extension] = appPath
        }
        return result.map { it.key to it.value }
    }

    /**
     * 应用列的单元格编辑器。
     *
     * <p>候选应用列表在**第一次开始编辑时**才扫描（约 160ms），
     * 避免打开设置页就同步扫一遍 /Applications 造成 EDT 卡顿；扫描结果在
     * [AppInstallerDetector] 内部缓存，只付一次代价。
     */
    private class AppCellEditor(comboBox: JComboBox<String>) : DefaultCellEditor(comboBox) {

        private val appComboBox = comboBox

        private var loaded = false

        override fun getTableCellEditorComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int,
        ): Component {
            if (!loaded) {
                AppInstallerDetector.installedAppPaths().forEach { appComboBox.addItem(it) }
                loaded = true
            }
            return super.getTableCellEditorComponent(table, value, isSelected, row, column)
        }
    }

    /**
     * 应用列渲染：显示成应用名，而不是一长串路径。
     */
    private class AppPathRenderer : DefaultTableCellRenderer() {

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val reference = value as? String
            val display = reference
                ?.takeIf { it.isNotBlank() }
                ?.let { AppInstallerDetector.appDisplayName(it) }
                .orEmpty()
            return super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, column)
        }
    }
}
