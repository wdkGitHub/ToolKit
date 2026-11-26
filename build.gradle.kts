import org.jdom2.CDATA
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform") version "2.20.0"
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("java")
}
group = "wang.dekun"

repositories {
    intellijPlatform {
        defaultRepositories()
    }
    mavenCentral()
}
tasks.withType(Jar::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
// 个人机器的 javaagent 不写进仓库：需要时在本机 ~/.gradle/gradle.properties 配置 runIde.javaAgent
tasks {
    runIde {
        val homeDir = System.getenv("HOME")
        providers.gradleProperty("runIde.javaAgent").orNull?.let { agent ->
            jvmArgumentProviders += CommandLineArgumentProvider { listOf("-javaagent:${homeDir}$agent") }
        }
    }

    // IntelliJ Platform Gradle Plugin 2.x 的 buildPlugin 只把 archiveBaseName 设为插件名，
    // 归档版本沿用 Gradle 的 project.version；本项目未设置 project.version（默认 unspecified），
    // 因此默认产物是 ToolKit.zip（无版本号）。
    // 这里显式把归档版本绑定到 plugin.version，产物形如 ToolKit-2.0.0.zip，
    // 与 release 附件名、download-url 末段、updatePlugins.xml 保持一致。
    named<Zip>("buildPlugin") {
        archiveVersion.set(providers.gradleProperty("plugin.version"))
    }
}
intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("plugin.id")
        name = providers.gradleProperty("plugin.name")
        version = providers.gradleProperty("plugin.version")
        description = providers.gradleProperty("plugin.description")
        changeNotes = providers.gradleProperty("plugin.changeNotes")
        vendor {
            name = providers.gradleProperty("plugin.vendor.name")
            email = providers.gradleProperty("plugin.vendor.email")
            url = providers.gradleProperty("plugin.vendor.url")
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("plugin.ideaVersion.sinceBuild")
            untilBuild = provider { null }
        }
    }
}
//https://plugins.jetbrains.com/docs/intellij/custom-plugin-repository.html#describing-plugins-in-updatepluginsxml-file
// 自定义插件仓库文档
dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2026.3")
        bundledPlugin("Git4Idea")
    }
    // kotlin-stdlib / kotlinx-coroutines 由目标 IDE 提供，不随插件打包
}


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jdom:jdom2:2.0.6.1")
    }
}

//updatePluginXml -Purl=test
tasks.register("updatePluginXml") {
    val basePath = project.projectDir
    val nowVersion = providers.gradleProperty("plugin.version").get()
    val pluginId = providers.gradleProperty("plugin.id").get()
    val pluginName = providers.gradleProperty("plugin.name").get()
    val pluginDescription = providers.gradleProperty("plugin.description").get()
    val pluginChangeNotes = providers.gradleProperty("plugin.changeNotes").get()
    val vendorName = providers.gradleProperty("plugin.vendor.name").get()
    val vendorEmail = providers.gradleProperty("plugin.vendor.email").get()
    val vendorUrl = providers.gradleProperty("plugin.vendor.url").get()
    val ideaSinceBuild = providers.gradleProperty("plugin.ideaVersion.sinceBuild").get()
    val downloadUrl = providers.gradleProperty("url").getOrElse("no-download-url")
    doLast {
        val xmlFile = File("${basePath}/updatePlugins.xml")
        val document: Document = SAXBuilder().build(xmlFile)
        val rootElement: Element = document.rootElement
        var pluginExists = false
        for (pluginNode in rootElement.getChildren("plugin")) {
            if (pluginNode.getChildText("version") == nowVersion || pluginNode.getChildText("version").contains("SNAPSHOT")) {
                pluginNode.apply {
                    getChild("id").text = pluginId
                    getChild("version").text = nowVersion
                    getChild("name").text = pluginName
                    getChild("vendor").apply {
                        content.clear()
                        text = vendorName
                        setAttribute("email", vendorEmail)
                        setAttribute("url", vendorUrl)
                    }
                    getChild("description").apply {
                        content.clear()
                        addContent(CDATA(pluginDescription))
                    }
                    getChild("change-notes").apply {
                        content.clear()
                        addContent(CDATA(pluginChangeNotes))
                    }
                    getChild("idea-version").setAttribute("since-build", ideaSinceBuild)
                    getChild("download-url").text = downloadUrl
                }
                pluginExists = true
                break
            }
        }
        if (!pluginExists) {
            rootElement.addContent(0, Element("plugin").apply {
                addContent(Element("id").apply {
                    text = pluginId
                })
                addContent(Element("name").apply {
                    text = pluginName
                })
                addContent(Element("version").apply {
                    text = nowVersion
                })
                addContent(Element("idea-version").apply {
                    setAttribute("since-build", ideaSinceBuild)
                })
                addContent(Element("vendor").apply {
                    text = vendorName
                    setAttribute("email", vendorEmail)
                    setAttribute("url", vendorUrl)
                })
                addContent(Element("description").apply {
                    addContent(CDATA(pluginDescription))
                })
                addContent(Element("change-notes").apply {
                    addContent(CDATA(pluginChangeNotes))
                })
                addContent(Element("download-url").apply {
                    text = downloadUrl
                })
            })
        }
        XMLOutputter().apply {
            format = Format.getPrettyFormat().apply {
                indent += indent
                encoding = "UTF-8"
            }
            output(document, xmlFile.writer())
        }
    }
}