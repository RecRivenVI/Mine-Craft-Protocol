# 本项目运行配置

当前构建只消费 [仓库配置](../reference/configuration-repository.md)规定的文件，
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
