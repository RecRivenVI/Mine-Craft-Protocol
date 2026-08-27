# Runtime 与构建依赖

Target 专属依赖以各端 `target.properties` 为权威，共用构建与测试依赖以
[版本目录](../../gradle/libs.versions.toml)为权威。Loader 管理的 Minecraft 运行库不由测试依赖覆盖。
Companion 的 Node 依赖单独见[Companion 依赖](dependency-companion.md)。

## Minecraft 与 Loader

五端事实由[项目规范](../AGENTS.md)登记。前三端固定 Minecraft 版本，两个滚动 Target 只跟进最新正式版；
Loader 可以采用已发布的 Beta，但版本必须固定，不能使用动态版本。
Fabric API 从 Fabric Target 的事实读取；ModDev、Loom 和 Wrapper 从各自构建事实读取。
已有最低 Loader 要求与编译用版本分离，新 Minecraft Target 不沿用旧端的运行下限。

## 运行库与测试库

Minecraft 提供 Netty、Gson、LWJGL 和 Mixin 运行库。旧端缺少的 Netty HTTP 模块只补同族、同版本，
不能与其他游戏 Netty 模块混搭；26.3 NeoForge 测试直接继承游戏的 Netty，不重复指定更新版本。
Mixin annotation processor 仅用于 Forge 编译和 refmap 生成，不替换 Loader 的运行时 Mixin。

JUnit Jupiter 与 Platform Launcher 按版本目录匹配，产品组件测试须保持 Java 17 下限。
五个 Target 的坐标回归测试使用本端 Minecraft 编译/运行依赖；Fabric 另加入原生 client 输出。
不以其他版本的 Gson 覆盖旧端 Minecraft 的严格依赖约束，也不将 JUnit 带入产品构件。
Gson 的独立测试依赖不表示游戏内 Gson 已升级。OpenAPI Generator 负责生成模型，生成输出不手工修改。

## 格式工具与验证

Spotless、ktfmt、Java 格式器和 rumdl 由版本目录固定。Java 格式器必须兼容 Spotless 使用的接口；
不能为了升级格式器修改受保护模板组件或跳过格式检查。

升级后执行 `spotlessApply`、`build check verifyRelease` 和 Companion 测试/审计；
改变 Loader 或 Minecraft 时补对应的真实 Runtime 验证。自动启动与接口检查不代替真人输入或视觉验收。
本次升级事实与结果集中记录在[执行计划](../project/plan-execution.md)。
