# 编写本地扩展包

## 适用范围

公共 SDK 位于 runtime_core 的 api.extension 和 api。扩展包是本地批准的 JVM 代码，不是权限沙箱；批准包不能防止恶意 Java 读文件或绕开游戏协议。当前平台实装进度以项目状态页为准，未通过运行门禁的能力不能宣称可用。

## 前置条件

- 固定 Target、Java 和目标模组版本，单独编译适配代码，不复制 SDK 或 Minecraft 类。
- 获得本机所有者对具体内容身份与声明 scope 的批准；更新代码、清单或依赖后重新批准并重启。
- 将用户输入与调试分开；模式不授予 scope、Lease、Debug Arm 或服务器权限。

公共SDK JAR只包含api与api.extension接口，不包含Runtime实现或Gson/Mixin。编译依赖使用SDK、与目标游戏兼容的Gson，以及确实使用hook时的Mixin；不得把它们复制进作者代码JAR。七个正式扩展包由独立source-set、仅公开SDK类路径编译，作为[可运行参考](../../components/extensionpacks/extensionpacks/computercraft/java/io/github/recrivenvi/minecraftprotocol/extensions/computercraft/ComputerCraftExtension.java)，不要求修改本体源码或另声明Loader模组。

## 步骤

1. ID采用目标mod_id；Java根包为io.github.recrivenvi.minecraftprotocol.extensions.<mod_id>，非法包名字符改为下划线，Java关键字追加下划线。入口用目标模组驼峰名加Extension，只在initialize中注册。客户端实现放client，Mixin放mixin，与之共用的导出状态放bridge，验证夹具放fixture。
2. 生成 extensionpack.json，索引包内每个文件的 SHA-256，写明 Target 变体、物理运行端入口、模组/包依赖、命名空间、能力和 scope。
3. 为 Provider 定义 snapshot/query/revision/Debug schema，声明实际 owner thread、来源、预算、副作用和完整性。Provider V2 仍是 DEBUG；client_known 不自动变成 USER。
4. 用 UiAdapter 发布已绑定上下文、坐标空间和时效的投影；原生控件只补充语义，异步框架在自己的线程准备不可变快照。让 USER UI 操作进入 Runtime 的真实输入队列，不能调用业务 setter 或 performClick 冒充玩家。USER 世界操作保留原生 pick、距离、碰撞与服务器校验。
5. 为持续资源登记运行实例、包、生命周期、owner executor 与授权失效条件。撤权先禁止发布，再在 owner 上清理；逐项记录失败。关闭注册不代表卸载 Mixin。
6. 为 early hook 声明独立 Mixin 命名空间与 exports；共享状态 helper 不放在 Mixin 类包内。旧 Forge 用相应 Target 的真实映射生成 refmap，不将开发环境的命中作为成品证明。
7. 把包安装到实例根的 extensionpacks/，由物理输入初次启用批准；本地审批位于mine_craft_protocol/extensions，重启后查询实际目录；不要在运行中替换或远程上传代码。
8. 编写标准接口场景，分别记录 Arrange、正常 Act、独立 Assert、证据和 Cleanup；调试准备不作为 PLAYTEST 验收。

精确清单与审批格式见[接口](../reference/api-extension_platform.md#清单格式1)。早期hook由各Loader的启动桥准备派生库，开发验证的主机暂存操作见[构建与主机工具](build-extensionpacks.md)。测试索引引用文档内已索引的固定文件，可与说明共用文件；通过minecraft://extensions/包ID/tests/测试ID查阅只返回数据，不运行代码。回滚同样先停机，恢复已批准的整个旧包/审批并重启，不能只换一个JAR或在运行中删除Mixin。

## 资源与错误处理

通过 contextResources 取得当前 WORLD、CONNECTION、SCREEN 或 INPUT_LEASE 资源组；processResources 只管理进程资源，DebugContext.resources 只管理已经授权的任务。保存脱离游戏对象的上下文身份用于比较，不在后台保存原生世界或客户端对象。Scope.publish 是发布屏障，失效后不得重新发布；ThreadOwner.cleanupBinding 在登记时捕获实际owner，不能把旧世界清理安排到新服务器。

只依赖公共SDK即可捕获 ExtensionException 并读取稳定 code；不要导入 core.extension 的实现类或解析异常文字。EXTENSION_CONTEXT_UNAVAILABLE 表示当前运行端或生命周期没有该上下文，不创建替代上下文。清理必须尝试全部资源并保留逐项异常，不能吞掉错误后声明已释放。

框架输入清理失败时，READ/OPERATE 仍可在独立权限下诊断。普通屏障允许修复后显式 emergency-release；inputQueuePoisoned=true 则要求重启Runtime，不能循环重放玩家操作。恢复授权与恢复已经退休的任务是两回事，新的任务必须显式启动。

## 验收

- 未审批、哈希变化、错端、缺依赖、重复类、循环依赖在入口运行前被拒绝；初始化失败撤销该包注册。
- 读取不启动隐藏采样或修改；无 scope/Arm/Lease、旧版本、取消和超时的负向场景明确失败。
- 两个包分别故障与恢复、离开世界、撤权、运行关闭后无迟到发布，清理异常不阻断其他资源。
- 仅通过标准 Native/MCP 接口验证结果；单元、实际装载、正常业务闭环与真人体验分别报告。
- 框架轮询只在 LogicalInput 的获准作用域读取；跨线程使用一次性 defer 并确认真实消费。reset 清理按住、未消费事件和输入法缓冲，不能获取 Lease、移动宿主光标或在撤销后重新派发。
