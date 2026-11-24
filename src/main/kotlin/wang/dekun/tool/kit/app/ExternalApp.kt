package wang.dekun.tool.kit.app

import com.intellij.icons.AllIcons
import icons.Icons
import javax.swing.Icon


/**
 * 外部目标的打开方式，决定“用哪个路径”去启动应用。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
enum class OpenMode {

    /** 打开目录（终端、Git 客户端等声明了“文件夹”类型文档的应用） */
    DIRECTORY,

    /** 打开具体文件（例如 Typora 打开 .md 文件） */
    FILE,

    /** 打开 URL（浏览器打开远程仓库地址） */
    URL,

    /** 在访达里定位：文件用 `open -R` 选中它，目录用 `open` 直接打开 */
    REVEAL,
}


/**
 * 本插件能唤起的外部目标声明。
 *
 * <p>新增一个外部应用只需要两步：
 * <ol>
 *     <li>在这里加一条枚举（声明图标、bundleId、CLI 回退、可见性条件）；</li>
 *     <li>在 plugin.xml 里加一行 action 注册。</li>
 * </ol>
 * 不需要改动启动逻辑、可见性判断或任何 when 分支。
 *
 * <p>启动优先使用 macOS 的 `open -b &lt;bundleId&gt;`：由 LaunchServices 自己解析应用安装位置，
 * 因此既不需要硬编码可执行文件路径，也不需要用 mdfind / Spotlight 去查找应用。
 *
 * @param text               动作在右键菜单里显示的文字
 * @param icon               动作图标
 * @param openMode           打开方式，决定使用目录、文件还是 URL
 * @param bundleIds          `open -b` 使用的 bundleId，按顺序尝试；为空表示不用 open 启动
 * @param appBundleNames     安装检测用的 .app 包名（不含 .app 后缀）
 * @param cliFallbacks       CLI 回退命令模板，`{path}` 会被替换成真实路径；首个元素若为绝对路径则直接检测该文件
 * @param requiresRepository 是否要求目标位于 git 仓库内
 * @param requiresRemoteUrl  是否要求仓库配置了远程地址
 * @param fileExtension      只对指定后缀（不含点号）的文件可见
 * @param checkInstalled     是否在显示前检测应用是否安装；未安装则动作不可见
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
enum class ExternalApp(
    val text: String,
    val icon: Icon,
    val openMode: OpenMode = OpenMode.DIRECTORY,
    val bundleIds: List<String> = emptyList(),
    val appBundleNames: List<String> = emptyList(),
    val cliFallbacks: List<List<String>> = emptyList(),
    val requiresRepository: Boolean = false,
    val requiresRemoteUrl: Boolean = false,
    val fileExtension: String? = null,
    val checkInstalled: Boolean = true,
) {

    /**
     * iTerm2 打开选中目录。iTerm2 的 Info.plist 声明了 CFBundleDocumentTypes = Folders，
     * 所以 `open -b com.googlecode.iterm2 <目录>` 可以直接在该目录开一个会话窗口。
     * 未安装 iTerm2 时自动回退到系统自带的 Terminal.app。
     */
    ITERM2(
        text = "Reveal In Terminal",
        icon = Icons.TERMINAL_ICON,
        bundleIds = listOf("com.googlecode.iterm2", "com.apple.Terminal"),
        // iTerm2 缺失时回退到 Terminal.app，后者系统自带，因此永不隐藏该动作
        checkInstalled = false,
    ),

    /**
     * Fork 打开选中目录/仓库。`open -b` 失败时回退到 Fork 自带的 CLI（`fork open <path>`）。
     */
    FORK(
        text = "Reveal In Fork",
        icon = Icons.FORK,
        bundleIds = listOf("com.DanPristupov.Fork"),
        appBundleNames = listOf("Fork"),
        cliFallbacks = listOf(
            listOf("/usr/local/bin/fork", "open", "{path}"),
            listOf("/opt/homebrew/bin/fork", "open", "{path}"),
        ),
        requiresRepository = true,
    ),

    /**
     * SourceTree 打开选中仓库。未安装时（本机即未安装）动作直接不可见，不留死按钮。
     */
    SOURCETREE(
        text = "Reveal In SourceTree",
        icon = Icons.SOURCETREE,
        bundleIds = listOf("com.torusknot.SourceTreeNotMAS"),
        appBundleNames = listOf("SourceTree"),
        cliFallbacks = listOf(listOf("stree", "{path}")),
        requiresRepository = true,
    ),

    /**
     * 浏览器打开 git 远程仓库。直接使用平台 BrowserUtil，不再依赖 git-open 脚本，
     * 也不起子进程；远程地址由 `.git/config` 解析后转换成 https 形式。
     */
    BROWSER(
        text = "Open In Browser",
        icon = AllIcons.General.Web,
        openMode = OpenMode.URL,
        requiresRepository = true,
        requiresRemoteUrl = true,
        checkInstalled = false,
    ),

    /**
     * 在访达中定位选中项。
     *
     * <p>选中文件时用 `open -R` 打开所在目录并选中该文件；选中目录时直接打开该目录。
     * 访达是系统自带应用，因此不做安装检测，也不要求目标位于 git 仓库内。
     */
    FINDER(
        text = "Reveal In Finder",
        icon = AllIcons.Nodes.Folder,
        openMode = OpenMode.REVEAL,
        checkInstalled = false,
    ), ;

    /**
     * 该应用是否声明了后缀限制（声明后只在选中对应类型文件时出现）。
     */
    val hasFileTypeLimit: Boolean
        get() = !fileExtension.isNullOrBlank()

    companion object {
        /** bundleId / CLI 命令里的路径占位符 */
        const val PATH_PLACEHOLDER = "{path}"
    }
}
