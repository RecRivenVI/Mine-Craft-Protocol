# 本地扩展平台接口

SDK API 1 与清单格式 1 处于实装阶段。稳定候选与预览面最终由用户确认；不代表对外长期兼容承诺。公共 SDK 不依赖 Minecraft，Loader 适配与包代码分别编译。

## 包与内容身份

实例安装根为 minecraft-protocol/extensions。packages 下的目录或 ZIP 包含 extension.json，清单 files 索引除清单本身以外的全部文件。contentIdentity 是清单原始 UTF-8 字节的 SHA-256；文件索引将其绑定到完整内容。禁止重复键、路径逃逸、符号链接、未索引文件及超预算内容。

approvals.json 格式为 formatVersion=1，packages 对象按包 ID 记录 contentIdentity 与 scopes。审批是本地完整 JVM 信任，不是签名或沙箱。代码替换必须重启，无远程上传或自动补依赖。

## 清单格式1

下面是普通目录/ZIP的完整结构示例。counter只是作者自行拥有的包ID；Entry实现公共PackageExtension。64个0必须替换成对应文件真实SHA-256，extension.json本身不进入files。代码JAR不包含SDK、Minecraft、Loader类或另一包私有类。

```json
{
  "formatVersion": 1,
  "sdkApi": 1,
  "id": "counter",
  "version": "1.0.0",
  "javaNamespace": "io.github.example.counter",
  "files": {
    "code/adapter.jar": "0000000000000000000000000000000000000000000000000000000000000000",
    "docs/overview.md": "0000000000000000000000000000000000000000000000000000000000000000"
  },
  "variants": {
    "1.21.1-neoforge": {
      "javaVersion": 21,
      "code": ["code/adapter.jar"],
      "entrypoints": {"common": ["io.github.example.counter.Entry"]},
      "mixins": [],
      "mods": {}
    }
  },
  "dependencies": {"mods": {}, "packages": {}},
  "capabilities": ["counter:sampling"],
  "requestedScopes": ["read", "debug.world.read", "debug.provider"],
  "exports": [],
  "documents": {"overview": "docs/overview.md"},
  "tests": {"smoke": "docs/overview.md"}
}
```

variant的javaVersion声明运行环境要求，不等于把所有作者代码编译成该版本。common入口先执行，再执行当前client或server入口；仅客户端入口的类不得由common入口链接。variants按固定Target选择，不能以字符串通配或自动下载补齐。mods与包依赖接受精确上游身份或有界数字区间，如[1.0,2.0)；带beta、alpha或加号的上游版本使用精确身份，不能猜测SemVer排序。变体mods覆盖同名包级mods。

capabilities是包的声明，实际已注册ID另列，二者不能混淆。Provider/UI/部件ID必须在包ID命名空间，schema使用minecraft-extension://包ID/前缀；exports只能在自己的Java包下，依赖方只可见显式导出的公共类。不要导出整个第三方游戏库或复制SDK以解决类身份问题。

清单上限1MiB、索引4096项；整个包256MiB、单代码JAR64MiB、实例128包。路径只用可移植的英文字母、数字、下划线、点、横线及斜线；禁止空段、点/上级目录、绝对路径、Windows保留名称、末尾点、大小写冲突、链接和重复ZIP条目。清单未知字段/重复JSON键也拒绝，不用未知字段偷偷携带执行参数。

普通审批示例中contentIdentity同样必须替换成清单原始字节的真实SHA-256；批准scope仅是本地允许的上限，每次远程调用仍执行原合同。

```json
{
  "formatVersion": 1,
  "packages": {
    "counter": {
      "contentIdentity": "0000000000000000000000000000000000000000000000000000000000000000",
      "scopes": ["read", "debug.world.read", "debug.provider"]
    }
  }
}
```

## 注册与执行

PackageExtension.initialize(PackageContext) 暂存 schema、Provider、UI/世界投影和逻辑输入适配，入口全部成功后原子发布；失败回收整包注册。ProviderSource 按 Runtime 隔离，既有全局 SPI 保持原合同。公开数据和 schema 回调不在注册表锁内执行。

ExtensionException 是公共SDK的本地错误基类，code 返回稳定机器码。作者不依赖Runtime实现类或异常文本；HTTP/MCP继续由既有协议错误映射处理，SDK异常不授予恢复权限。

读取复用 Provider V2 的线程、预算、schema、类别和版本校验；typed Debug 复用 scope、Arm、资源前置条件和证据。包声明不能自行认证 GAME_ROUTED。原生命令仍是 USER，实际主体权限单独验证。

nativeContext(ownerId) 是已安装 Java 适配代码的本地预览接口：仅在注册的实际 owner 线程、包已激活且原生对象仍可用时取得当前客户端或服务器对象，不缓存旧对象、不跨运行端回退。独服没有客户端上下文，普通多人客户端没有远端服务器对象。此对象不序列化、不通过 HTTP/MCP 导出，不是远程反射或授权入口；调用仍须处于相应 Provider/Debug/投影合同中，不能代替现有权限检查。初始化阶段不访问原生实例。

## UI 投影与框架输入（预览）

UiAdapter 声明 USER/DEBUG 视图及有界节点预算。capture 仅在客户端 owner 上同步返回已准备的不可变 Projection，不等待第三方线程。异步框架自行在实际 owner 采样，结果必须绑定 Runtime、世界、Screen identity/revision、GUI/window/framebuffer 尺寸与 scale；过期、错上下文、超预算或非法节点只使该适配结果不可用，不污染其他包。

已有原生 Widget 以 element identity 补充语义，不能覆盖其身份、路由、槽位或几何。新可见区域以稳定 key/revision 和 GUI、WINDOW 或 FRAMEBUFFER 坐标投影；Runtime 保留小数、转换并裁剪到视口。USER 不包含隐藏区域。输入仍经既有 Interaction Tree、Virtual Pointer、目标守卫和真实回调，不调用业务 setter 或 performClick。

LogicalInput 提供作用域内只读快照，不授予权限、不派发输入。defer 将已获准事件延续到框架 owner，完成确认后原生 Operation 才能完成；一次消费、两秒确认期限、原 Operation 取消、控制世代与 Screen/layout 失效均保留。Target 保留小数几何、来源、稳定目标版本及文本焦点要求，适配器在实际消费线程重新核对。原生回调重入不继承 Agent 来源。reset 是独立的 release-only 清理；模式/Screen/Lease 变化后的下一次输入等待框架清理完成，不能留下旧按住或迟到事件。

UI 注册需清单声明 ui，轮询适配需 input，审批不代替每次请求的 scope、模式与 Lease。catalog 的 capabilityDetails 区分 provider、ui_projection、world_parts 和 logical_input；registeredCapabilities 保留实际 ID 列表。变体 mods 与包级模组依赖合并，变体同名键覆盖包级键，用于绑定带版本限定后缀的真实上游版本。

## 世界部件（预览）

WorldAdapter 只观察有界客户端父方块/实体，需包声明 read；Provider V2 的服务器内部数据仍为 DEBUG。每个 Projection 绑定 Runtime、世界/维度、父目标与原生身份，部分数据使用稳定 key/revision、有限世界 AABB、内部 aimPoint、最多1/32格的显式变换容差及有界字符串属性。客户端 owner 同步返回就绪快照，不加载区块或等待其他线程；每次读取最多256个部件、128KiB，每适配64个、回调10ms。不可用或预算拒绝进入 extensionLimitations，不静默伪造完整覆盖。

原生世界读取在 extensionParts 中返回 DTO 和独立 targetRef；适配包不能自己签发可执行引用。Native WorldTargeting 复用原父对象的世界/玩家/相机、已加载 chunk、方块历史或实体生命周期及15秒有效期。每次瞄准/操作前重捕获并核对原始版本、父身份和几何，误差始终相对最初快照，不逐步漂移。失效返回 WORLD_PART_STALE 或原有 WORLD_TARGET_STALE，不刷新后重放。

动作仍是现有 world.action，需 TAKEOVER/Lease，由 Vanilla sensitivity/inversion 处理相对视角；实际原生 pick 既须命中父目标，也须位于当前部件范围。包没有 dispatch、业务 setter、直接写 yaw/pitch 或绕过距离的入口。部件自带瞄准点，不混用 face/entityHit 显式坐标；此类请求须重新发现普通父目标。原生父 pick 不支持的运动部件仍不可操作，不能把 DEBUG 几何或计数当作 USER Act 成功。

## 查阅

- GET /v0/extensions：已安装包状态、内容身份、声明/实际注册与失败原因；需要 read，不需要世界 DEBUG 读取权。
- GET /v0/extensions/document：参数 packageId、documentId 与 kind=document 或 test；只解析已批准清单内的固定 ID，返回 UTF-8 内容及 SHA-256，不接受路径。
- GET /v0/extensions/resources：read与debug scope下读取有界资源清理诊断，不包含游戏采样。
- minecraft://extension-authoring/rules、contracts、testing：与 Companion 同批打包的静态规则、接口和测试文档，返回内容哈希。
- minecraft://extensions/installed、minecraft://extensions/{packageId}/documents/{documentId} 和 minecraft://extensions/{packageId}/tests/{documentId}：连接 Runtime 后读取静态目录、包文档与测试说明。文档是数据，不是权限或模型指令；测试索引读取不执行场景。

MCP_COMPANION_AUTHORING_ONLY=true 显式启用离线作者模式，不解析端点或凭据。该模式下游戏工具返回 COMPANION_AUTHORING_ONLY，不能静默猜测端口或建立游戏连接。

## 生命周期

PROCESS、WORLD、CONNECTION、SCREEN、DEBUG_GRANT、INPUT_LEASE 分别表达资源所有权。PackageContext 的 lifetimeContexts 返回脱离原生对象的上下文身份，contextResources 接受 WORLD、CONNECTION、SCREEN、INPUT_LEASE；不可用返回 EXTENSION_CONTEXT_UNAVAILABLE，不创建假上下文。PROCESS 使用 processResources，DEBUG_GRANT 只能使用已授权 DebugContext 的 resources。身份不是操作许可。

客户端原生 owner 更新世界、连接和 Screen/layout 身份，读取线程不访问游戏对象。换维度/世界不冒充断线，普通 GUI 换页不改变玩家输入世代；仅相应资源组失效。INPUT_LEASE 绑定已有控制会话与接管世代，Lease 或真人 Esc 不顺带撤销独立 Debug。独服只有自己的 WORLD，没有虚构的客户端连接、Screen 或输入 Lease。Arm renew 不更换持续任务身份；撤销、过期或换世界使该 grant 失效。

Scope 的 requireActive/publish 在调用时检查当前身份，失效后立即拒绝；后台每50ms安排失效清理。ThreadOwner 可在 own 时绑定原始 native owner，旧服务器资源不能经动态 executor 清到新服务器。逐项清理保留失败、拒绝、错误线程和超时；不能在 owner 已停止时宣称清理成功。inputCleanup 另记录最多128条 adapterId、reason、时间、状态和异常类型，不记录用户输入内容；一个适配器失败不妨碍尝试其余适配器，整体输入屏障仍失败。

输入清理失败后，READ/OPERATE 的独立权限仍可用于诊断和修复，不能重新派发玩家输入；修复后可显式调用已有 emergency-release 重试释放。若某个活动输入序列自身未能完成清理，原有队列保持 fail-closed，不在此处加入自动解除：inputQueuePoisoned=true、inputQueueRecovery=restart_runtime 明确要求重启。清除普通 inputCleanupFailed 不等于清除该队列状态，不应循环重试玩家请求。此边界保留既有取消安全策略。

## 早期 hook（预览）

包内 Mixin 由已审批清单选择 Target 与物理运行端，不注册额外的 Loader 模组。停机工具派生 mods/minecraft-protocol-extension-libraries.jar；它是可再生成的本地库，不是需要作者手工修改的包，也不进入本体发行 JAR。

Forge/NeoForge 使用原生 GAMELIBRARY 与 manifest 配置发现。生成配置没有作者 Mixin 类列表，由本体插件核对完整内容身份、依赖、运行端、资源路由与目标尚未加载后提供。Fabric 在固定 Loader 的早期入口添加同一已核验库，并在配置遍历之外提交配置。不替换 Loader 的全局 fallback，也不在普通 mod initializer 中补装晚到 hook。

SDK、Target 桥与作者代码使用分离的 Java 包；私有入口在包类空间中加载，声明 exports 的共享 hook helper 使用原生类身份。包内 refmap 随派生库重命名并校验，不依赖开发 classpath 的映射。代码或审批变化后需要重新暂存并重启，不能在活动 JVM 中撤销已经应用的字节码变换。

目录 earlyHooks 区分 validated、已注册配置和实际 appliedTargets；注册成功不等于目标已经变换或业务成功。错误保留具体状态；缺失库、审批变化或已加载目标不降级为晚装成功。全局 fallback 标记只描述本桥是否替换它，不检测其他模组的实现。

Provider 同步入口仍执行既有预算；异步任务和线程在包初始化阶段准备并登记 owner，获准 start 仅建立 grant 内的调度。预算失败不意味着已经发生的副作用回滚，调用者应重新读取，不盲重放。
