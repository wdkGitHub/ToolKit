package wang.dekun.tool.kit.repo


/**
 * Git remote 地址 → 浏览器可访问地址。
 *
 * <p>**没有命中配置规则**：完全沿用插件原有逻辑（http(s)/ftp(s) 原样；ssh:// / git:// / scp
 * 形式转成 https 并丢弃 SSH 端口），公网仓库的行为零变化。
 *
 * <p>**命中配置规则**：按 host（域名或 IP）匹配，用规则里的协议与 Web 端口重新拼地址，
 * 解决私有环境只有 http、以及「拉取数据的端口」与「实际访问 Web 的端口」不一致的问题。
 *
 * <p>本文件是纯字符串解析，不依赖 IntelliJ 平台类，可单独验证。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
object GitRemoteUrlResolver {

    const val SCHEME_HTTP = "http"
    const val SCHEME_HTTPS = "https"

    /** 配置页新增规则时按协议带出的默认 Web 端口 */
    const val DEFAULT_PORT_HTTP = "80"
    const val DEFAULT_PORT_HTTPS = "443"

    private val SUPPORTED_SCHEMES = setOf("http", "https", "ftp", "ftps", "ssh", "git")

    /** scp 形式：`user@host:path`，host 部分不允许出现 `/` 与 `:`（即不支持端口） */
    private val SCP_LIKE = Regex("""^[^/@\s]+@[^/:\s]+:.+$""")

    private const val GIT_SUFFIX = ".git"

    /**
     * 一条 Web 地址覆盖规则。
     *
     * @param scheme http / https，默认 http
     * @param port   Web 端口；空表示不带端口（走协议默认的 80 / 443）
     */
    data class WebRule(val scheme: String = SCHEME_HTTP, val port: String = "")

    /**
     * remote 解析结果。
     *
     * @param host      主机名（域名或 IPv4 / IPv6，IPv6 不含方括号）
     * @param port      remote 中显式写出的端口，没有则 null
     * @param path      以 `/` 开头的仓库路径，已去掉结尾 `/` 与 `.git`
     * @param legacyUrl 原有逻辑得到的浏览器地址；未命中规则时直接返回它，保证行为不变
     */
    data class RemoteAddress(
        val host: String,
        val port: Int?,
        val path: String,
        val legacyUrl: String,
    )

    /**
     * 解析 remote 地址。本地路径（`file://` / `/Users/x/repo.git` / `~/repo.git`）、
     * ssh config 别名以及无法识别的形式一律返回 null —— 不能把本地路径塞给浏览器。
     */
    fun parse(rawUrl: String?): RemoteAddress? {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            return null
        }
        val schemeEnd = url.indexOf("://")
        if (schemeEnd > 0) {
            val scheme = url.substring(0, schemeEnd).lowercase()
            if (scheme !in SUPPORTED_SCHEMES) {
                return null
            }
            val rest = url.substring(schemeEnd + 3)
            val withoutUser = dropUser(rest)
            val (authority, rawPath) = splitAuthority(withoutUser)
            if (authority.isEmpty()) {
                return null
            }
            val (host, port) = splitHostPort(authority)
            if (host.isEmpty()) {
                return null
            }
            val legacy = when (scheme) {
                "http", "https", "ftp", "ftps" -> url
                "ssh" -> "https://" + stripPort(withoutUser)
                else -> "https://" + stripPort(rest)
            }
            return RemoteAddress(host, port, cleanPath(rawPath), cleanupUrl(legacy))
        }
        if (SCP_LIKE.matches(url)) {
            val afterAt = url.substringAfter('@')
            val host = afterAt.substringBefore(':')
            if (host.isEmpty()) {
                return null
            }
            val legacy = "https://" + afterAt.replaceFirst(':', '/')
            return RemoteAddress(host, null, cleanPath("/" + afterAt.substringAfter(':')), cleanupUrl(legacy))
        }
        return null
    }

    /**
     * 按规则生成浏览器地址。
     *
     * @param parsed [parse] 的结果，null 表示不可浏览
     * @param rule   命中的规则；null 表示未配置，直接返回 [RemoteAddress.legacyUrl]
     */
    fun toBrowserUrl(parsed: RemoteAddress?, rule: WebRule? = null): String? {
        parsed ?: return null
        if (rule == null) {
            return parsed.legacyUrl
        }
        val scheme = if (rule.scheme.equals(SCHEME_HTTPS, ignoreCase = true)) SCHEME_HTTPS else SCHEME_HTTP
        val port = rule.port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
        val authority = formatHost(parsed.host) + (port?.let { ":$it" } ?: "")
        return "$scheme://$authority${parsed.path}"
    }

    /** 便捷重载：直接传原始 remote 地址（主要用于预览与验证） */
    fun toBrowserUrl(rawUrl: String?, rule: WebRule? = null): String? = toBrowserUrl(parse(rawUrl), rule)

    /**
     * 主机匹配用的规范化：去空白、去协议/路径/端口、去 IPv6 方括号、去 user@、统一小写。
     * 配置页的「主机」列允许直接粘贴完整 remote 地址，也靠这里取出 host。
     */
    fun normalizeHost(raw: String?): String {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) {
            return ""
        }
        if (text.contains("://")) {
            text = text.substringAfter("://").substringBefore('/')
        }
        text = text.substringBefore('/').substringAfterLast('@')
        if (text.startsWith("[")) {
            return text.substringBefore(']').removePrefix("[").trim().lowercase()
        }
        return text.substringBefore(':').trim().lowercase()
    }

    /**
     * 从「主机」列可能粘贴的完整 remote 地址 / `host:port` 中读取端口，作为配置页的默认值。
     * 读不到或非法时返回空串。
     */
    fun portFromInput(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) {
            return ""
        }
        parse(text)?.port?.let { return it.toString() }
        val authority = if (text.contains("://")) {
            text.substringAfter("://").substringBefore('/').substringAfterLast('@')
        } else {
            text.substringBefore('/')
        }
        val candidate = if (authority.startsWith("[")) {
            authority.substringAfter(']').removePrefix(":")
        } else {
            authority.substringAfterLast(':', "")
        }
        return candidate.takeIf { isValidPort(it) }.orEmpty()
    }

    fun isValidScheme(scheme: String?): Boolean =
        scheme != null && (scheme.equals(SCHEME_HTTP, true) || scheme.equals(SCHEME_HTTPS, true))

    /**
     * 配置页新增规则时使用的默认 Web 端口：http → 80，https → 443。
     * 协议切换时，只要端口没被用户手动改过（仍等于旧协议的默认端口），就会跟着换成新默认值。
     */
    fun defaultPortFor(scheme: String?): String =
        if (SCHEME_HTTPS.equals(scheme?.trim(), ignoreCase = true)) DEFAULT_PORT_HTTPS else DEFAULT_PORT_HTTP

    /** 非法 / 空 / 大小写不一致的协议统一降级成 http，配置页与持久化层共用同一套规则 */
    fun effectiveScheme(raw: String?): String =
        raw?.trim()?.lowercase()?.takeIf { isValidScheme(it) } ?: SCHEME_HTTP

    fun isValidPort(port: String?): Boolean {
        val value = port?.trim()?.toIntOrNull() ?: return false
        return value in 1..65535
    }

    /** 拼 URL 时 IPv6 主机需要方括号 */
    fun formatHost(host: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]" else host

    private fun cleanupUrl(url: String): String = url.trimEnd('/').removeSuffix(GIT_SUFFIX)

    private fun cleanPath(raw: String): String {
        var path = raw.trim().trimEnd('/')
        if (path.isNotEmpty() && !path.startsWith("/")) {
            path = "/$path"
        }
        return path.removeSuffix(GIT_SUFFIX)
    }

    /** 去掉 authority 里的 `user@`，路径里的 `@` 不受影响 */
    private fun dropUser(authorityAndPath: String): String {
        val slash = authorityAndPath.indexOf('/')
        val authority = if (slash < 0) authorityAndPath else authorityAndPath.substring(0, slash)
        val rest = if (slash < 0) "" else authorityAndPath.substring(slash)
        return authority.substringAfterLast('@') + rest
    }

    /** 丢弃 `host[:port]/path` 中的端口，保留 IPv6 的 `[...]` 形式（原有逻辑） */
    private fun stripPort(hostAndPath: String): String {
        val (authority, rest) = splitAuthority(hostAndPath)
        val host = when {
            authority.startsWith("[") -> authority.substringBefore(']') + "]"
            else -> authority.substringBefore(':')
        }
        return host + rest
    }

    private fun splitAuthority(authorityAndPath: String): Pair<String, String> {
        val slash = authorityAndPath.indexOf('/')
        return if (slash < 0) {
            authorityAndPath to ""
        } else {
            authorityAndPath.substring(0, slash) to authorityAndPath.substring(slash)
        }
    }

    private fun splitHostPort(authority: String): Pair<String, Int?> {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) {
                return authority.removePrefix("[") to null
            }
            val host = authority.substring(1, close)
            val port = authority.substring(close + 1).removePrefix(":").toIntOrNull()
            return host to port
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) {
            return authority to null
        }
        val port = authority.substring(colon + 1).toIntOrNull() ?: return authority to null
        return authority.substring(0, colon) to port
    }
}
