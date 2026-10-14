# Runtime 核心组件

`runtime_core` 是随五个 Target 发布的纯 Java 产品组件。它共享协议执行与策略，不持有 Minecraft
对象，不实现 Loader hook，也不改变 READ / TAKEOVER / OPERATE、权限、证据或持久化合同。

## 职责与接口

组件包含 Provider SPI 与注册表、HTTP/WebSocket 传输、认证预算、Control Lease/Debug Arm 与操作状态、
Pipeline/Wait/Assert、观察与资源版本、Provider 执行、录制和 Artifact，以及内置诊断 Provider。
共享执行器通过 `ProbeService` 调用 Target 原生适配器；游戏、渲染与服务端线程调度留在各 Target。

`probe.api` 的公开类名、方法与 JSON 合同保持不变。内部执行器使用独立的 `core` 包，
避免开发运行中产品组件与 Target 的包内访问或模块包冲突。只公开原生适配器和原生契约测试所需边界；
可见性不是额外授权，HTTP 请求仍由现有认证、scope、mode、Lease、Arm 与前置条件检查。

正式服务接口为 `ProbeService`。现有实验能力通过 `Phase9AExperimentalService` 显式接入，
仅 Forge 1.20.1、NeoForge 26.3 和 Fabric 26.3 实现；另外两端对应实验路由继续不存在。
通用传输的 Target 身份由原生构造处传入，Provider namespace 使用 Target 生成的 `ProtocolIdentity.MOD_ID`，
组件不生成第二份模组身份，也不硬编码产品 ID。

## 构建与依赖

组件在 `settings.gradle.kts` 中登记为 `product`，由模板机制合并进五个发行 JAR，
开发运行使用 Loader 原生依赖 classpath；不复制共享源码或要求用户单独安装组件。
编译字节码保持 Java 17 下限，与现有 `runtime_safety` 通过组合构建依赖连接。

Gson、Netty 与 `runtime_safety` 为编译依赖；游戏运行仍使用 Minecraft 提供的运行库。
Netty 编译/独立测试基线取现有 Forge 1.20.1 的 4.1.82.Final API，不把该测试库嵌入产品或覆盖其他端。
具体第三方依赖事实见[依赖说明](../reference/dependency-runtime.md)。

## 验证边界

与游戏无关的契约测试随组件 `check` 执行，验证取消、Lease、人工撤销 latch、权限、版本、Provider、
录制、独立 HTTP 请求及正式/实验接口分离；Minecraft UI 和原生几何测试继续在各 Target 执行。
五端构建与 `verifyRelease` 必须核对共享类没有遗漏或重复；真实开发 Runtime 验证不能由构建代替。
本迁移不属于新的 UI 人工验收或完整世界里程碑验收；当前结果见[执行计划](../project/plan-execution.md)。
