# Vulkan 后端回归

## 目标

保留 26.2 NeoForge/Fabric 的真实 Vulkan 后端验证，不能把请求 Vulkan 后回退 OpenGL 计为通过。

## 运行

从仓库根目录执行，启动前由工具为 NeoForge 的隔离实例设置 earlyWindowControl=false：

```powershell
pwsh -File components/validation_tools/regression-v1_release/task/Invoke-Phase8SequentialLiveGate.ps1
```

单端运行任务为 `:version:<target>:runValidationRegressionVulkanClient`，实例在本验证的 instance/<target>/client。
物理输入验收仍由用户执行；本验证不读写其他游戏实例。

## 通过标准

Runtime/capture 返回实际 Vulkan，读取、采证与关闭完成；fallback、崩溃、超时或未执行均不得计为 PASS。
