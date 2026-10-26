# 本项目运行配置

当前构建只消费 [仓库配置](../../gradle/conventions/specification/reference/configuration-repository.md)规定的文件，
不再消费旧 `instance.properties` / `local.properties`。

## 配置归属

| 文件 | 本项目用途 |
| --- | --- |
| `gradle.properties` | 原产品身份、许可、描述与 Gradle 参数；`mod_side=both` |
| `versions/<target>/target.properties` | Java、编译 Loader、原最低 Loader 要求、Target 专属依赖 |
| `gradle/libs.versions.toml` | 共享库与构建工具；产品库版本保留 |
| `instances.toml` | 客户端默认 2560×1440 启动窗口与失焦继续运行的现有 JVM 参数 |
| `local.toml` | 被忽略的个人窗口/内存/玩家设置、本地输入与用户 EULA 决定 |
| `validations/<name>/validation.toml` | 独立测试实例与角色，不继承个人参数 |

## 个人设置示例

独立服务器 Runtime 默认只给 read/event/control/diagnostics，不授予控制台所有者能力。
所有者决定开放时，可在服务器的 jvm-args 显式加入既有 scope 配置：

```text
-Dminecraft.protocol.scopes=read,event,control,command,diagnostics,server_console
```

启动后从该服务器目录的 minecraft-protocol/endpoint.json 发现自动分配端口，token 在同目录的 token 文件，不能复制到聊天或证据。
Companion 设置 MCP_MINECRAFT_INSTANCE_DIR 为此实例目录，并可设 MCP_MINECRAFT_ROLE=DEDICATED_SERVER 防止选错客户端。
控制台授权不开放玩家输入、DEBUG、存档或全部管理命令；完整初始目录见[部署设计](../design/concept-deployment_scenarios.md#独立入口与原生输入)。

独立世界DEBUG使用自己的scope，不需要server_console或command：

```text
-Dminecraft.protocol.scopes=read,event,control,diagnostics,debug,debug.world.read,debug.write,debug.world
```

只读用途可省略debug.write/debug.world；修改仍须显式OPERATE与world Arm、指纹及资源版本。
首批只支持主世界有界读取和已加载方块修改，具体请求见[Native合同](../reference/protocol-native_v0.md#独立服务器世界-debug)。

日常客户端（含多人客户端）与 Control UI Showcase 默认以 2560×1440 启动。
日常实例仍可通过 `local.toml` 覆盖窗口尺寸；Showcase 使用自己的验证配置。
启动尺寸在下次启动时生效，不调整当前已经打开的窗口。

```toml
[client]
width = 1280
height = 720
memory-max = "4G"
```

不写 `eula` 就没有接受 EULA。不要替用户自动添加。
TOML 的覆盖与默认值以配置参考为准，不能继续按旧 properties 双文件 cascade 推断。
当前目录已经是 `instances/<target>/client`，迁移不覆盖其日志、选项或世界。

## 验证命令

```powershell
./gradlew.bat verifyConfiguration
./gradlew.bat :version:26.3-neoforge:runClient
```

产品 Runtime 配置格式、认证、scope 与存档格式不在此次构建配置迁移中改变。
