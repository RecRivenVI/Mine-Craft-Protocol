# 世界基础读取分类验证

## 目标

验证六个基础世界读取入口的 USER/DEBUG 分类、独立读取权限、模式约束和诚实能力声明。
USER 玩家读取仅覆盖已有可见热栏/副手/效果呈现；场景/服务器 USER 查询在捕获前明确拒绝。
不证明完整可见性、Deep/Provider/Wait/Event/Recording 的全链隔离、真人撤销或完整世界里程碑。
UI 推进暂停；这里只复用既有 UI 进入专用世界，不新增 UI 能力。

## 运行

从仓库根目录执行；`<target>` 为本验证登记的 Target。一次只启动一个自有静音实例。

```powershell
.\gradlew.bat check verifyRelease
pwsh -NoProfile -File components/validation_tools/conformance-world_read/task/Invoke-WorldReadSmoke.ps1 -Target <target>
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs
```

实例位于本验证的 instance；首次通过现有语义 UI 创建独立创造超平坦世界，后续选择同名世界。
只经 Native 接口操作；不用截图、Computer Use 或宿主输入，不读取日常实例或其他项目的存档。
游戏正常保存自己的测试世界，不调用正式 Persistent Write；客户端最后通过正常菜单退出。
五端生产 WorldReadEngine 单元测试随 check 运行，共享投影和 Companion 转发另有自动测试。

## 通过标准

- capabilities 明确基本分类范围、待迁移消费者及 milestoneComplete=false。
- USER 玩家响应没有原始坐标/生命/UUID；仅返回有界可见 HUD，背包打开后没有该 HUD。
- USER block/entities/server 请求及非法 category 在读取前失败；不回退 DEBUG、客户端或磁盘。
- DEBUG 保留原始精度/数据来源，不要求 write/Arm/Lease；TAKEOVER 下拒绝 DEBUG，OPERATE 下拒绝 USER。
- READ 缺输入 Lease 时不能发玩家输入；收尾 held keys/buttons 为零。
- 源码运行中不变，结果记录实际检查、源码指纹、commit/dirty 和开发 Runtime 性质。
- 自有客户端干净退出、端口释放，真实 Persistent Write 调用为零。
- 不把本验证的 PASS 写成五端完整世界验收或最终 JAR Runtime 验收。

## 当前结果

2026-10-15 当前 dirty 候选五端分别通过 9/9 项真实 Native 基础用例，
绑定当次源码、Schema 与工具哈希，sourceChangedDuringRun=false；各端正常退出并释放端口。
实例均静音，jcmd 确认最大堆为 4 GiB；实际保存发生在本验证自己的世界，正式 Persistent Write 为 0。
具体字段与逐项结果在 result/<target>.json，不由某端或共用代码推定其他端。
世界是可重复 smoke 资产，不是每次重置的固定副本；完整感知、服务端 Peer、真人行为及世界里程碑尚未验收。
Native Scope/mode 单元与实际 HTTP 绑定另随五端 check 通过，Companion 本轮为真实 SDK 到测试服务器的自动转发检查，
不把它写成新世界 MCP 到真实游戏的 Live 结果。UI 统一验收继续暂停。
