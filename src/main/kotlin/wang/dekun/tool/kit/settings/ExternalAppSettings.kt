package wang.dekun.tool.kit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import wang.dekun.tool.kit.app.AppInstallerDetector
import java.io.File


/**
 * “后缀 → 外置应用”映射的持久化配置。
 *
 * <p>应用级（不是项目级）配置：同一台机器上所有项目共用一份映射。
 * 存放于 IDE 配置目录的 `toolkit.externalApps.xml`。
 *
 * <p>首次使用时（配置文件还不存在）会自动探测 Typora：装了就把 `md` / `markdown`
 * 默认指向它，这样从旧版本升级上来的用户行为不变；没装则保持空表。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
@Service(Service.Level.APP)
@State(name = "ToolKitExternalAppSettings", storages = [Storage("toolkit.externalApps.xml")])
class ExternalAppSettings : PersistentStateComponent<ExternalAppSettings.State> {

    /**
     * 一条映射。
     *
     * <p>必须是可变属性 + 无参构造，XmlSerializer 才能正常反序列化。
     */
    data class Mapping(
        var extension: String = "",
        var appPath: String = "",
    )

    /** 持久化状态 */
    data class State(
        var mappings: MutableList<Mapping> = mutableListOf(),
    )

    private var state: State = State(defaultMappings())

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * 当前映射快照（后缀 → 应用），供设置页显示与比较。
     */
    fun snapshot(): List<Pair<String, String>> = state.mappings.map { it.extension to it.appPath }

    /**
     * 整体替换映射，设置页点“应用”时调用。
     */
    fun replaceAll(mappings: List<Pair<String, String>>) {
        state.mappings = mappings.map { Mapping(it.first, it.second) }.toMutableList()
    }

    /**
     * 查询某个文件应使用哪个外置应用；没有配置返回 null。
     */
    fun appFor(filePath: String?): String? {
        val extension = extensionOf(filePath) ?: return null
        return state.mappings
            .firstOrNull { it.extension.equals(extension, ignoreCase = true) }
            ?.appPath
            ?.takeIf { it.isNotBlank() }
    }

    companion object {

        fun getInstance(): ExternalAppSettings =
            ApplicationManager.getApplication().getService(ExternalAppSettings::class.java)

        /**
         * 取文件后缀（不含点号，统一小写）；没有后缀返回 null。
         */
        fun extensionOf(filePath: String?): String? {
            val name = filePath?.let { File(it).name } ?: return null
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) {
                return null
            }
            return name.substring(dot + 1).lowercase()
        }

        /**
         * 默认映射：装了 Typora 就把 markdown 交给它，保持旧版本的用户体验。
         */
        private fun defaultMappings(): MutableList<Mapping> {
            val typora = AppInstallerDetector.findAppBundle("Typora") ?: return mutableListOf()
            return mutableListOf(
                Mapping("md", typora.absolutePath),
                Mapping("markdown", typora.absolutePath),
            )
        }
    }
}
