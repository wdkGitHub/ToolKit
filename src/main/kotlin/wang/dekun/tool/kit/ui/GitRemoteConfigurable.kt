package wang.dekun.tool.kit.ui

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import wang.dekun.tool.kit.repo.GitRemoteUrlResolver
import wang.dekun.tool.kit.settings.GitRemoteSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel


/**
 * 设置页：按域名 / IP 配置「用浏览器打开 Git 远程仓库」时的协议与 Web 端口。
 *
 * <p>表格三列：主机（域名 / IP）、协议（http / https，新增默认 http）、Web 端口。
 * 新增规则的端口按协议自动带出默认值（http → 80，https → 443），可随时手动改成实际
 * 访问用的 Web 端口；把协议从 http 改成 https 时，没被手动改过的端口会自动跟着变成 443。
 * 端口明确留空表示不带端口（走协议默认端口）。
 *
 * <p>没有配置任何规则时，插件保持原有逻辑：SSH / scp / git:// 一律转 https 并丢弃 SSH 端口，
 * http(s) 地址原样打开。配置后只有命中的主机才会被替换协议与端口。
 *
 * <p>点击「Open In Browser」时会弹出通知显示插件实际生成的地址，便于区分「规则没命中」
 * 与「浏览器把 http 自动升级成 https」（HSTS / HTTPS-First）。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class GitRemoteConfigurable : Configurable {

    private val tableModel = object : DefaultTableModel(arrayOf("域名 / IP", "协议", "Web 端口"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true

        /**
         * 单元格写回模型时做联动，规则只有一条：**端口不等于当前协议默认端口 = 用户手动改过，绝不覆盖**。
         * <ul>
         *     <li>协议切换：端口仍是旧协议默认端口时，跟着换成新协议默认端口（80 ↔ 443）；</li>
         *     <li>主机列粘贴完整 remote：端口仍是默认值时，带出 remote 里读到的端口。</li>
         * </ul>
         */
        override fun setValueAt(value: Any?, row: Int, column: Int) {
            if (row !in 0 until rowCount) {
                super.setValueAt(value, row, column)
                return
            }
            val oldScheme = getValueAt(row, COLUMN_SCHEME) as? String
            super.setValueAt(value, row, column)
            when (column) {
                COLUMN_SCHEME -> {
                    val oldDefault = GitRemoteUrlResolver.defaultPortFor(oldScheme)
                    val newDefault = GitRemoteUrlResolver.defaultPortFor(value as? String)
                    if (oldDefault != newDefault && getValueAt(row, COLUMN_PORT) as? String == oldDefault) {
                        super.setValueAt(newDefault, row, COLUMN_PORT)
                    }
                }
                COLUMN_HOST -> {
                    val scheme = GitRemoteUrlResolver.effectiveScheme(getValueAt(row, COLUMN_SCHEME) as? String)
                    val defaultPort = GitRemoteUrlResolver.defaultPortFor(scheme)
                    if (getValueAt(row, COLUMN_PORT) as? String == defaultPort) {
                        val detected = GitRemoteUrlResolver.portFromInput(value as? String)
                        if (detected.isNotEmpty() && detected != defaultPort) {
                            super.setValueAt(detected, row, COLUMN_PORT)
                        }
                    }
                }
            }
        }
    }

    private val table = JBTable(tableModel)

    private val statusLabel = JBLabel("")

    /** 已保存的快照，用于 isModified 比较 */
    private var appliedRules: List<GitRemoteSettings.Rule> = emptyList()

    override fun getDisplayName(): String = "Git 远程 Web 地址"

    override fun createComponent(): JComponent {
        fillTable(GitRemoteSettings.getInstance().snapshot())
        appliedRules = readRules()

        table.columnModel.getColumn(COLUMN_SCHEME).cellEditor = DefaultCellEditor(
            JComboBox(arrayOf(GitRemoteUrlResolver.SCHEME_HTTP, GitRemoteUrlResolver.SCHEME_HTTPS)),
        )
        table.rowHeight = 24
        table.setShowGrid(false)
        // 点“确定/应用”时输入框会失去焦点，必须显式结束编辑，否则最后编辑的单元格不会写回模型
        table.putClientProperty("terminateEditOnFocusLost", true)

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                tableModel.addRow(
                    arrayOf<Any?>(
                        "",
                        GitRemoteUrlResolver.SCHEME_HTTP,
                        GitRemoteUrlResolver.defaultPortFor(GitRemoteUrlResolver.SCHEME_HTTP),
                    ),
                )
                val row = tableModel.rowCount - 1
                table.editCellAt(row, COLUMN_HOST)
                table.requestFocusInWindow()
            }
            .setRemoveAction {
                table.selectedRows.sortedDescending().forEach { tableModel.removeRow(it) }
            }
            .setEditAction(null)

        val header = JBLabel(
            "<html>按域名或 IP 匹配远程主机：命中后用这里的协议与 Web 端口打开仓库。<br>" +
                "未配置的主机保持原有逻辑（SSH / scp / git:// 转 https 并丢弃 SSH 端口）。<br>" +
                "新增规则默认 http / 80；协议改成 https 时端口自动变成 443（手动改过的端口不会被覆盖）。<br>" +
                "主机列可直接粘贴完整 remote 地址，端口留空表示走协议默认端口。</html>",
        ).apply { border = JBUI.Borders.emptyBottom(8) }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(header, BorderLayout.NORTH)
            add(decorator.createPanel(), BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply { add(statusLabel) },
                BorderLayout.SOUTH,
            )
        }
    }

    override fun isModified(): Boolean = readRules() != appliedRules

    override fun apply() {
        val rules = readRules()
        GitRemoteSettings.getInstance().replaceAll(rules)
        appliedRules = rules
        // 回填规范化后的值（粘贴完整 remote 时会自动拆出 host 与端口），保证界面与已保存状态一致
        fillTable(rules)
        statusLabel.text = "已保存 ${rules.size} 条规则；协议已写入 toolkit.gitRemote.xml，未匹配主机仍按原有逻辑打开。"
    }

    override fun reset() {
        appliedRules = GitRemoteSettings.getInstance().snapshot()
        fillTable(appliedRules)
        appliedRules = readRules()
        statusLabel.text = ""
    }

    private fun fillTable(rules: List<GitRemoteSettings.Rule>) {
        tableModel.rowCount = 0
        rules.forEach { tableModel.addRow(arrayOf<Any?>(it.host, it.scheme, it.port)) }
    }

    /**
     * 读取表格并规范化：主机取域名 / IP（支持粘贴完整 remote），协议非法降级 http，
     * 端口非法丢弃；同主机以最后一行为准。
     *
     * <p>端口列 null = 从没编辑过的旧数据 → 自动带入 remote 里读到的默认端口；
     * 空串 = 用户明确清空 → 不带端口（走协议默认端口）。新增行会直接填入协议默认端口，
     * 因此正常流程下不会走到 null 分支。
     */
    private fun readRules(): List<GitRemoteSettings.Rule> {
        val result = LinkedHashMap<String, GitRemoteSettings.Rule>()
        for (row in 0 until tableModel.rowCount) {
            val rawHost = tableModel.getValueAt(row, COLUMN_HOST) as? String
            val host = GitRemoteUrlResolver.normalizeHost(rawHost)
            if (host.isEmpty()) {
                continue
            }
            val scheme = GitRemoteUrlResolver.effectiveScheme(tableModel.getValueAt(row, COLUMN_SCHEME) as? String)
            val portCell = tableModel.getValueAt(row, COLUMN_PORT)
            val port = when {
                portCell == null -> GitRemoteUrlResolver.portFromInput(rawHost)
                portCell !is String -> ""
                portCell.trim().isEmpty() -> ""
                GitRemoteUrlResolver.isValidPort(portCell) -> portCell.trim()
                else -> ""
            }
            result[host] = GitRemoteSettings.Rule(host, scheme, port)
        }
        return result.values.toList()
    }

    private companion object {
        const val COLUMN_HOST = 0
        const val COLUMN_SCHEME = 1
        const val COLUMN_PORT = 2
    }
}
