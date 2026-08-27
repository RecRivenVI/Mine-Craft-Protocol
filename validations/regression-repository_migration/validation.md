# 仓库迁移回归

## 目标

对照迁移前 HEAD 的独立 archive 构建与当前发行 JAR，逐类仅排除行号后比较字节，
逐文件比较资源；不把新的打包结构、规范格式化或 Loader 升级差异隐藏为一致。

## 运行

从仓库根目录执行。archive 导出到仓库外，以原来的构建方式构建全部 Target。
将已有 ASM 9.10.1 库路径作为类路径传给比较工具，不增加产品依赖：

```powershell
./gradlew.bat spotlessApply check verifyRelease
java -cp <asm-jar> components/validation_tools/regression-repository_migration/task/CompareArtifacts.java <baseline-directory> .
```

将输出覆盖本验证的 result/comparison.json；理由记录在 result/summary.json。
参考 Target runClient 的日志与 Runtime 状态为本轮新证据，不沿用旧 packaged attestation。

## 通过标准

三项构建检查通过，没有 P-06/P-07。JAR 中的全部差异有明确迁移原因；
参考客户端产品加载、到达标题界面且无崩溃。服务端只有用户在 local.toml 接受 EULA 才运行。
人工视觉、五端完整游戏场景与未执行的旧 Live 验证仍明确标为未执行。

迁移检查和历史产品 Release Gate 分开报告。依赖审计已在现有开发依赖中发现 high，
不因本轮构建通过而改写成安全审计 PASS，也不在禁止升级依赖的迁移中自动修复。
