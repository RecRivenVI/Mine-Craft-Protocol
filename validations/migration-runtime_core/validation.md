# Runtime 核心迁移验证

## 目标

证明共享产品组件实际接入五个开发 Runtime 和五个发行 JAR，迁移不扩大实验路由或改变权限合同。
使用本验证独立、静音的实例和可丢弃世界；不覆盖此前世界验证结果，不使用 Computer Use。

## 运行

从仓库根目录先完成构建、自动测试与严格合规，再按顺序运行五个客户端。
每个客户端完成并干净关闭、释放端口后才启动下一端。

```powershell
./gradlew.bat spotlessApply
./gradlew.bat build check verifyRelease
./gradlew.bat verifyCompliance --strict
pwsh -File components/validation_tools/migration-runtime_core/task/Invoke-RuntimeCoreMigrationSmoke.ps1
```

服务端验证仅在 local.toml 已接受 EULA 时执行参考 Target 的运行任务，观察正常启动后输入 stop 关闭。
未同意 EULA 时明确跳过，不自动同意；服务端结果不冒充五端完整 Peer 验收。

```powershell
./gradlew.bat :version:26.3-neoforge:runValidationMigrationRuntimeCoreServer
```

服务端正常停止后汇总本次结果；没有服务端或任一客户端证据时汇总失败，不生成虚假全绿结果。

```powershell
pwsh -File components/validation_tools/migration-runtime_core/task/Write-RuntimeCoreMigrationReport.ps1
```

## 通过标准

- 共享核心契约测试与五端原生测试通过，严格合规失败 0、警告 0。
- 五个发行 JAR 各有且仅有一份共享核心/SPI 类，无旧内部类或重复嵌入库。
- 每端真实 Native 标题/世界基础用例通过；源码指纹包含 runtime_core，运行中不变。
- READ/DEBUG 分类、TAKEOVER/Lease、语义输入和正常退出保持现有合同；退出 held input 为零。
- 正式 Persistent Write 调用为 0；没有模拟真人 Esc 或新的 UI/世界里程碑完成声明。
- 自有客户端正常关闭且端口释放；EULA 授权下参考服务端正常启动与停止。
- 结果位于本验证 result/<target>.json，不复用历史制品或世界验证结果证明当前候选。
