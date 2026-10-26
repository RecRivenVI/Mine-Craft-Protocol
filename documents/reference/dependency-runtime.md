# Runtime 与构建依赖

Target 专属依赖以各端 `target.properties` 为权威，共用构建与测试依赖以
[版本目录](../../gradle/libs.versions.toml)为权威。Loader 管理的 Minecraft 运行库不由测试依赖覆盖。
Companion 的 Node 依赖单独见[Companion 依赖](dependency-companion.md)。

## Minecraft 与 Loader

五端事实由[项目规范](../AGENTS.md)登记。前三端固定 Minecraft 版本，两个滚动 Target 只跟进最新正式版；
Loader 可以采用已发布的 Beta，但版本必须固定，不能使用动态版本。
Fabric API 从 Fabric Target 的事实读取；ModDev、Loom 和 Wrapper 从各自构建事实读取。
已有最低 Loader 要求与编译用版本分离，新 Minecraft Target 不沿用旧端的运行下限。
Minecraft、最低Loader要求及项目拥有的依赖保持已登记事实；模板规则以[仓库规范](../../AGENTS.md)为准，模板升级不自动升级Loader、Fabric API或Loom。
Fabric Loader最新稳定为0.19.5，0.20.x标为非稳定，本批不采用。

2026-10-20更新范围与官方来源如下；版本事实仍只从Target文件与版本目录读取。

| 依赖 | 更新前 | 当前 | 上游来源 |
| --- | --- | --- | --- |
| NeoForge / 1.21.1 | 21.1.259 | 21.1.264 | [官方Maven](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) |
| NeoForge / 26.1.2 | 26.1.2.117 | 26.1.2.122 | 同上 |
| NeoForge / 26.3 | 26.3.0.77 | 26.3.0.94 | 同上 |
| Fabric API / 26.3 | 0.162.0+26.3 | 0.164.0+26.3 | [官方Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml) |
| Loom | 1.18.3 | 1.19.1 | [官方发布模块](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.1/fabric-loom-1.19.1.module) |

Forge47.4.28、ModDev2.0.148及其余适用项目依赖已是当前稳定发布；模板工具版本不动。
Loom1.19.1要求的Java25与Gradle9.8 API由现有daemon/Wrapper满足，不为此修改模板。

## 运行库与测试库

独立服务端日志采集以 Minecraft 自带 Log4j2 为运行时来源；runtime_core 对 log4j-core 仅 compileOnly，
使用版本目录中的兼容编译基线，并在单元测试中提供该库。发行 JAR 不再嵌入第二份 Log4j，
不覆盖各 Target 的游戏日志实现；具体兼容性由对应 Loader 的独立安装验证确认。

Minecraft 提供 Netty、Gson、LWJGL 和 Mixin 运行库。旧端缺少的 Netty HTTP 模块只补同族、同版本，
不能与其他游戏 Netty 模块混搭；Forge 1.20.1 与 NeoForge 1.21.1通过Loader Jar-in-Jar交付缺少的HTTP模块，
不只加入开发类路径。嵌入依赖不传递捆绑游戏已有的buffer/common/handler等模块。
原版26.1.2和26.3的官方library清单已包含HTTP模块，分别使用同族4.2.7.Final和4.2.16.Final；
包括Fabric在内不再重复嵌入另一份游戏已提供的库。五端均须独立正常Loader安装验证，不从开发类路径推定成功。
Mixin annotation processor 仅用于 Forge 编译和 refmap 生成，不替换 Loader 的运行时 Mixin。

JUnit Jupiter 与 Platform Launcher 按版本目录匹配，产品组件测试须保持 Java 17 下限。
五个 Target 的坐标回归测试使用本端 Minecraft 编译/运行依赖；Fabric 另加入原生 client 输出。
不以其他版本的 Gson 覆盖旧端 Minecraft 的严格依赖约束，也不将 JUnit 带入产品构件。
Gson 的独立测试依赖不表示游戏内 Gson 已升级。OpenAPI Generator 负责生成模型，生成输出不手工修改。
runtime_core 与 runtime_safety 的编译和契约测试使用项目专用 mcp-runtime-gson 别名，固定为最老 Target 实际提供的 Gson 2.10。
这样编译即拒绝游戏运行库没有的 API（例如 JsonObject.isEmpty），不以新版测试库掩盖旧端链接错误；模板工具 Gson 条目保持不变。
runtime_safety 的 UI 分类投影以 compileOnly 使用该 API 基线，测试使用同一依赖；
运行时由 Minecraft 提供 Gson，不嵌入另一份 Gson 或覆盖 Loader 的版本约束。

runtime_core 以 compileOnly 使用 runtime_safety、Gson 和 Netty；独立契约测试使用相应 testImplementation。
Netty 编译/测试基线固定为 Forge 1.20.1 已有的 4.1.82.Final，版本目录新增项目专用别名，
不是游戏运行库升级。五端继续使用本端 Minecraft/Loader 管理的 Netty；只在缺失HTTP模块的1.x端嵌入匹配模块，
不覆盖4.2系列或打包JUnit等仅测试依赖。
Java 编译保持 17 下限，共享组件与原生适配器分别验证；[组件边界](../design/architecture-runtime_core.md)说明接口分离。

正式运行依赖的交付边界如下；implementation只表示编译/开发使用，不单独证明最终JAR交付。

| 依赖 | 正式运行来源 |
| --- | --- |
| runtime_core、runtime_safety | 产品组件平铺进同一个最终JAR，无需另装 |
| Netty HTTP | 1.x对应Target的Jar-in-Jar；26.1.2/26.3由原版库提供 |
| Netty其余模块、Gson、Guava、Fastutil、JOML、LWJGL/STB及日志库 | 官方Minecraft/Loader运行库，不复制另一版本 |
| Mixin及Loader API | 对应Loader原生运行库，annotation processor不随产品交付 |
| Fabric API | Fabric产品JAR内由Loom include登记的嵌入依赖，不要求单独安装 |
| JUnit、OpenAPI Generator、Node/TypeScript验证工具 | 仅测试或工具，不进入游戏发行JAR |

Netty上游许可和归属说明随产品附带，Fabric API保留其原JAR及模块许可；详见[NOTICE](../../NOTICE)。
每次alpha刷新都先核对当前五端最终JAR的独立安装证据，不用开发类路径补齐结果。

## 验收附加模组

这些是外部本地输入，不是产品依赖，不嵌入发行JAR，也不进入纯净自动验证。
版本、来源与允许哈希只在[inputs.toml](../../inputs.toml)固定，本机文件位置仅在ignored local.toml。
2026-10-16核对Modrinth精确游戏版本和Loader的发布列表：

| 模组 | 可用的本项目Target | 不宣称的范围 |
| --- | --- | --- |
| [Custom Skin Loader](https://modrinth.com/mod/customskinloader) | 五端均有匹配的Universal发布 | 上游标注支持不等于本项目五端附加Mod运行验收已经通过 |
| [Zume](https://modrinth.com/mod/zume) | 1.20.1 Forge、1.21.1 NeoForge | 26.1.2/26.3的对应Loader无匹配发布，跳过；不因其宽泛元数据范围强装 |

使用原始官方JAR，不修改或重新发布第三方二进制；主模组权限、输入和证据合同不因此改变。
下载、元数据/哈希核对、实际加载、用户体验分别判断。附加Mod冲突不得以移入自动基线或修改产品权限来掩盖。

## 格式工具与验证

Spotless、ktfmt、Java 格式器和 rumdl 由版本目录固定。Java 格式器必须兼容 Spotless 使用的接口；
不能为了升级格式器修改受保护模板组件或跳过格式检查。
与 Minecraft 无关的模板工具条目必须与当前规范版本一致，项目专用条目及 ModDev/Loom 继续保留。

升级后执行 `spotlessApply`、`build check verifyRelease` 和 Companion 测试/审计；
改变 Loader 或 Minecraft 时补对应的真实 Runtime 验证。自动启动与接口检查不代替真人输入或视觉验收。
升级阶段与当时结果保留在[阶段记录](../project/phase-1-core_delivery.md)，当前交付状态见[状态页](../project/status-milestones.md)。
