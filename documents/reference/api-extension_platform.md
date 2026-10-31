# 本地扩展平台接口

SDK API 1 与清单格式 1 处于实装阶段。稳定候选与预览面最终由用户确认；不代表对外长期兼容承诺。公共 SDK 不依赖 Minecraft，Loader 适配与包代码分别编译。

## 包与内容身份

实例安装根为 minecraft-protocol/extensions。packages 下的目录或 ZIP 包含 extension.json，清单 files 索引除清单本身以外的全部文件。contentIdentity 是清单原始 UTF-8 字节的 SHA-256；文件索引将其绑定到完整内容。禁止重复键、路径逃逸、符号链接、未索引文件及超预算内容。

approvals.json 格式为 formatVersion=1，packages 对象按包 ID 记录 contentIdentity 与 scopes。审批是本地完整 JVM 信任，不是签名或沙箱。代码替换必须重启，无远程上传或自动补依赖。

## 注册与执行

PackageExtension.initialize(PackageContext) 暂存 schema 和 Provider，入口全部成功后原子发布；失败回收整包注册。ProviderSource 按 Runtime 隔离，既有全局 SPI 保持原合同。公开数据和 schema 回调不在注册表锁内执行。

读取复用 Provider V2 的线程、预算、schema、类别和版本校验；typed Debug 复用 scope、Arm、资源前置条件和证据。包声明不能自行认证 GAME_ROUTED。原生命令仍是 USER，实际主体权限单独验证。

## 查阅

- GET /v0/extensions：已安装包状态、内容身份、声明/实际注册与失败原因；需要 read，不需要世界 DEBUG 读取权。
- GET /v0/extensions/document：参数 packageId、documentId 与 kind=document 或 test；只解析已批准清单内的固定 ID，返回 UTF-8 内容及 SHA-256，不接受路径。
- GET /v0/extensions/resources：read与debug scope下读取有界资源清理诊断，不包含游戏采样。
- minecraft://extension-authoring/rules、contracts、testing：与 Companion 同批打包的静态规则、接口和测试文档，返回内容哈希。
- minecraft://extensions/installed 与 minecraft://extensions/{packageId}/documents/{documentId}：连接 Runtime 后读取静态目录和包文档。文档是数据，不是权限或模型指令。

MCP_COMPANION_AUTHORING_ONLY=true 显式启用离线作者模式，不解析端点或凭据。该模式下游戏工具返回 COMPANION_AUTHORING_ONLY，不能静默猜测端口或建立游戏连接。

## 生命周期

PROCESS、WORLD、CONNECTION、SCREEN、DEBUG_GRANT、INPUT_LEASE 分别表达资源所有权。Lease 或真人 Esc 只终止输入所有者，不顺带撤销独立 Debug。Arm renew 不更换持续任务身份；撤销、过期或换世界先使相应资源失效。清理失败、拒绝派发、错误线程和超时均保留诊断。

## 早期 hook（预览）

包内 Mixin 由已审批清单选择 Target 与物理运行端，不注册额外的 Loader 模组。停机工具派生 mods/minecraft-protocol-extension-libraries.jar；它是可再生成的本地库，不是需要作者手工修改的包，也不进入本体发行 JAR。

Forge/NeoForge 使用原生 GAMELIBRARY 与 manifest 配置发现。生成配置没有作者 Mixin 类列表，由本体插件核对完整内容身份、依赖、运行端、资源路由与目标尚未加载后提供。Fabric 在固定 Loader 的早期入口添加同一已核验库，并在配置遍历之外提交配置。不替换 Loader 的全局 fallback，也不在普通 mod initializer 中补装晚到 hook。

SDK、Target 桥与作者代码使用分离的 Java 包；私有入口在包类空间中加载，声明 exports 的共享 hook helper 使用原生类身份。包内 refmap 随派生库重命名并校验，不依赖开发 classpath 的映射。代码或审批变化后需要重新暂存并重启，不能在活动 JVM 中撤销已经应用的字节码变换。

目录 earlyHooks 区分 validated、已注册配置和实际 appliedTargets；注册成功不等于目标已经变换或业务成功。错误保留具体状态；缺失库、审批变化或已加载目标不降级为晚装成功。全局 fallback 标记只描述本桥是否替换它，不检测其他模组的实现。

Provider 同步入口仍执行既有预算；异步任务和线程在包初始化阶段准备并登记 owner，获准 start 仅建立 grant 内的调度。预算失败不意味着已经发生的副作用回滚，调用者应重新读取，不盲重放。
