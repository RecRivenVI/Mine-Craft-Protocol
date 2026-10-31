# 编写本地扩展包

## 适用范围

公共 SDK 位于 runtime_core 的 probe.extension 和 probe.api。扩展包是本地批准的 JVM 代码，不是权限沙箱；批准包不能防止恶意 Java 读文件或绕开游戏协议。当前平台实装进度以项目状态页为准，未通过运行门禁的能力不能宣称可用。

## 前置条件

- 固定 Target、Java 和目标模组版本，单独编译适配代码，不复制 SDK 或 Minecraft 类。
- 获得本机所有者对具体内容身份与声明 scope 的批准；更新代码、清单或依赖后重新批准并重启。
- 将用户输入与调试分开；模式不授予 scope、Lease、Debug Arm 或服务器权限。

## 步骤

1. 编写实现 PackageExtension 的入口，只在 initialize 中注册，不在静态初始化访问游戏对象。
2. 生成 extension.json，索引包内每个文件的 SHA-256，写明 Target 变体、物理运行端入口、模组/包依赖、命名空间、能力和 scope。
3. 为 Provider 定义 snapshot/query/revision/Debug schema，声明实际 owner thread、来源、预算、副作用和完整性。Provider V2 仍是 DEBUG；client_known 不自动变成 USER。
4. 让 USER UI 操作进入 Runtime 的真实输入队列，不能调用业务 setter 或 performClick 冒充玩家。USER 世界操作保留原生 pick、距离、碰撞与服务器校验。
5. 为持续资源登记运行实例、包、生命周期、owner executor 与授权失效条件。撤权先禁止发布，再在 owner 上清理；逐项记录失败。关闭注册不代表卸载 Mixin。
6. 为 early hook 声明独立 Mixin 命名空间与 exports；共享状态 helper 不放在 Mixin 类包内。旧 Forge 用相应 Target 的真实映射生成 refmap，不将开发环境的命中作为成品证明。
7. 通过离线安装流程把包与审批记录放入实例的 minecraft-protocol/extensions，重启后查询实际目录；不要在运行中替换或远程上传代码。
8. 编写标准接口场景，分别记录 Arrange、正常 Act、独立 Assert、证据和 Cleanup；调试准备不作为 PLAYTEST 验收。

## 验收

- 未审批、哈希变化、错端、缺依赖、重复类、循环依赖在入口运行前被拒绝；初始化失败撤销该包注册。
- 读取不启动隐藏采样或修改；无 scope/Arm/Lease、旧版本、取消和超时的负向场景明确失败。
- 两个包分别故障与恢复、离开世界、撤权、运行关闭后无迟到发布，清理异常不阻断其他资源。
- 仅通过标准 Native/MCP 接口验证结果；单元、实际装载、正常业务闭环与真人体验分别报告。
