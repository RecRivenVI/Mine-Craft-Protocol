# 本地扩展平台架构

扩展平台复用 Mine-Craft-Protocol 的公开协议与执行器。本地安装受信任 Java 包，Agent 查阅其实际能力并通过标准工具使用；不是远程上传代码、任意 Java 调用或 JVM 沙箱。实现进度以[状态页](../project/status-milestones.md)为准，冻结候选和预览面的最终划分由用户确认。

## 组成与加载

runtime_core 提供 Minecraft 无关的 SDK、内容/审批预检、事务注册、实例级 Provider/schema 和资源所有权。各 Target 的 LoaderExtensionBridge 负责早期目录、物理运行端和模组事实；它不持输入权限，也不创建第二套 Lease。

普通包的代码在独立类空间运行；SDK 与现有原生类保持父加载器身份。跨包依赖必须声明 exports。早期 hook 使用离线派生的原生库，共享 hook helper 显式导出并使用原生类身份，避免与私有入口各有一份状态。

加载顺序是完整文件索引与本地审批、Target/运行端/依赖/类身份预检、早期 hook 验证、入口暂存注册，最后整包发布。失败撤销该包的注册和资源；字节码变换本身不能热卸载，代码变化必须重新审批并重启。加载桥细节见[移植说明](../development/porting-26_3.md#扩展包早期装载)。

## 注册与权限

PackageExtension.initialize 收到当前 Runtime 的 PackageContext，通过上下文注册并取得可关闭句柄。Provider 复用原有的 schema、owner thread、预算、资源版本、Deep Observation 与 typed Debug；静态目录和文档不会调用游戏采样。

本地代码审批与远程调用权限分别生效。包声明不授予 scope、模式、Arm、Lease 或服务器委托；READ 不启动采样，USER 操作不能由业务 setter 认证为 GAME_ROUTED。协议执行器产生实际路由与证据，不能接受包自己声称“正常玩家操作成功”。

UI 可见投影、逻辑输入适配和世界部件接口按后续阶段接入，不能以普通 Provider/Mixin 装载成功宣称这些能力已经完成。早期 Loader 桥、复杂框架输入与运动部件保持预览。

## 资源与异步执行

资源按 Runtime、包、生命周期和具体上下文分区。PROCESS、WORLD、CONNECTION、SCREEN、DEBUG_GRANT 与 INPUT_LEASE 分别失效；先拒绝新操作与迟到发布，再逐项调度 owner 清理。失败、拒绝、超时与未释放保留诊断，不伪造全部成功。

Debug 采样属于独立 grant。输入 Lease 结束或真人 Esc 不顺带结束合法 Debug；Arm 续期保留身份，撤销/过期与世界变化则使相应任务失效。实际游戏状态只在其 owner 线程读写，工作线程只接收不可变数据。

Provider 同步入口保持既有预算。需要准备资源时先返回有界异步结果，回到游戏 owner 后重查取消、deadline、授权和资源版本，再执行；不可用排队绕过权限或重放旧请求。包初始化时的线程也必须登记 PROCESS 清理。

## 构建、查阅与证明

作者工程暂位于 tool 组件 extension_packages，独立产生包，不合进本体 JAR。SDK 与正式作者文档同批交付，Companion 支持离线规则/合同查阅及在线固定 ID 的安装目录、包文档和测试索引。包文档是数据，不是模型指令或授权。

平台探针将实际 Mixin tick、owner 采样、版本与清理分开断言。成品安装只使用官方 Loader、最终发行 JAR 和已批准原生库；开发 source-set、独立服务端、第三方业务与人工体验分别记录。配置注册或输入已派发不能代替业务效果。

具体接口见[SDK 与包合同](../reference/api-extension_platform.md)，操作见[作者规程](../development/procedure-author_extension.md)，证明规则见[扩展验证](../development/testing-extension_platform.md)。
