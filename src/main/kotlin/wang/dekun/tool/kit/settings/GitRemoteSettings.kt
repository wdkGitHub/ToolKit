package wang.dekun.tool.kit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import wang.dekun.tool.kit.repo.GitRemoteUrlResolver


/**
 * Git 远程仓库 Web 地址规则的持久化配置（应用级，配置文件 `toolkit.gitRemote.xml`）。
 *
 * <p>按 host（域名或 IP，忽略大小写）精确匹配：
 * <ul>
 *     <li>命中 → 用规则里的协议（http / https）与 Web 端口覆盖浏览器地址；</li>
 *     <li>未配置任何规则 / 未命中 → 返回 null，由 [GitRemoteUrlResolver] 完全沿用原有逻辑，
 *         公网仓库（github.com 等）的行为不变。</li>
 * </ul>
 *
 * <p>新增规则默认 http，端口按协议带出默认值（http → 80 / https → 443），可手动改成实际 Web 端口。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
@Service(Service.Level.APP)
@State(name = "ToolKitGitRemoteSettings", storages = [Storage("toolkit.gitRemote.xml")])
class GitRemoteSettings : PersistentStateComponent<GitRemoteSettings.State> {

    /**
     * 一条规则。必须是可变属性 + 无参构造，XmlSerializer 才能正常反序列化。
     *
     * <p>`scheme` 的默认值特意设为**空串**而不是 http：XmlSerializer 会跳过「与默认值相同」的字段，
     * 若默认 http，用户选了 http 时配置文件里就不写 scheme，看不到协议是否已保存。
     * 空串会在 [snapshot] / [replaceAll] / [ruleFor] 里统一降级成 http，兼容旧配置文件。
     *
     * @param host   域名或 IP，也支持直接粘贴完整 remote 地址（保存时会规范化成 host）
     * @param scheme http / https，空串表示未指定（按 http 处理）
     * @param port   Web 端口，空串表示不带端口（使用协议默认端口）
     */
    data class Rule(
        var host: String = "",
        var scheme: String = "",
        var port: String = "",
    )

    /** 持久化状态 */
    data class State(
        var rules: MutableList<Rule> = mutableListOf(),
    )

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /** 当前规则快照，供设置页显示与 isModified 比较（scheme 统一规范化，界面不会显示空协议） */
    fun snapshot(): List<Rule> = state.rules.map {
        it.copy(scheme = GitRemoteUrlResolver.effectiveScheme(it.scheme))
    }

    /** 整体替换规则，设置页点“应用 / 确定”时调用 */
    fun replaceAll(rules: List<Rule>) {
        state.rules = rules.map {
            it.copy(scheme = GitRemoteUrlResolver.effectiveScheme(it.scheme))
        }.toMutableList()
    }

    /**
     * 按 host 查找规则。未配置返回 null（调用方走原有逻辑）。
     *
     * <p>返回值已做协议与端口校验，非法值会被降级为「http + 无端口」，不会拼出坏 URL。
     */
    fun ruleFor(host: String): GitRemoteUrlResolver.WebRule? {
        val key = GitRemoteUrlResolver.normalizeHost(host)
        if (key.isEmpty()) {
            return null
        }
        val rule = state.rules.firstOrNull { GitRemoteUrlResolver.normalizeHost(it.host) == key } ?: return null
        val scheme = GitRemoteUrlResolver.effectiveScheme(rule.scheme)
        val port = rule.port.trim().takeIf { GitRemoteUrlResolver.isValidPort(it) }.orEmpty()
        return GitRemoteUrlResolver.WebRule(scheme, port)
    }

    companion object {

        fun getInstance(): GitRemoteSettings =
            ApplicationManager.getApplication().getService(GitRemoteSettings::class.java)
    }
}
