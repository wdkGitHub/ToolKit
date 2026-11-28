# ToolKit

> macOS 自用的 IntelliJ IDEA 插件，Kotlin 编写，通过自定义插件仓库分发。
> 前身项目名为 CommandHook。

## 功能

- **外部 App 唤起**：在 Terminal / iTerm2、Fork、SourceTree 中打开当前 Git 仓库或选中目录
- **Git-Open**：用浏览器打开当前仓库的远程地址；在 `Settings | Other Settings | ToolKit Git Remote` 可按域名/IP 配置 http/https 与 Web 端口（私有环境的 SSH 端口与 Web 端口不一致时使用），未配置则保持默认 https 逻辑。新增规则默认 `http / 80`，协议切到 https 时端口自动变成 443，手动改过的端口不会被覆盖；打开时会弹出通知显示插件实际生成的地址（浏览器可能因 HSTS 把 http 再次升级为 https）
- **Typora**：用 Typora 打开选中的 Markdown 文件
- **AnAction 探查与注销**：显示任意 Action 的 ID / GroupId，可在启动时注销指定 Action
- **IDE 偏好**：自动开启项目视图「单击打开」与「自动定位到源文件」

> 仅支持 macOS；目标 IDE 为 IntelliJ IDEA Ultimate 2026.3+（`since-build=263`），依赖 `Git4Idea`。

## 使用方法

在 IDEA 的 `Settings | Plugins | ⚙ | Manage Plugin Repositories` 中添加
[仓库地址](https://raw.githubusercontent.com/wdkGitHub/ToolKit/refs/heads/master/updatePlugins.xml)，
然后搜索 `ToolKit` 安装。

## 构建与发布

```bash
./gradlew buildPlugin                 # 产物在 build/distributions/
./gradlew updatePluginXml -Purl=<zip 下载地址>
```