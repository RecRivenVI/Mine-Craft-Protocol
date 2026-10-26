# Server Peer

验证当前源码、Native 合同、Target 所有权和制品，而不是某个开发阶段的版本标签。

## 本地入口（不启动 Minecraft）

```powershell
pwsh -File components/validation_tools/integration-server_peer/task/Invoke-ServerPeerLocalGate.ps1 -Offline
```

该入口构建五个实际 Target、验证 OpenAPI/生成模型，保留本项路由、类型与实现存在性断言。
制品身份取自 gradle.properties。仅在本轮已经完成构建时可用 `-SkipBuild`；
该选项不代表重新构建过。离线需要已有依赖缓存。

`-SkipBuild` 只跳过 Gradle，五端源码/Schema/产物检查仍执行。产物按精确文件名及 Loader 元数据核对身份和版本。
门禁自身的隔离正反例：`pwsh -File components/validation_tools/integration-server_peer/task/Test-LocalGateChecks.ps1`。

## Runtime 场景（另行授权）

[具名黑盒场景](../../components/validation_tools/integration-server_peer/task/Invoke-Phase6PeerConformance.ps1)保留实际参数，先阅读再执行。
需要有效 BaseUri、TokenFile、正确 Target 和对应 scope / Lease / Debug Arm，以及安全测试世界。
Server Peer 场景另需独立授权的服务端；无 Peer 时应拒绝不可用能力。
一次只保留一个普通客户端，不操作重要存档，不使用 CUA/宿主输入注入。
物理 Esc / 焦点 / 真人点击必须请求用户执行并由 Runtime/Audit 核对。

这些脚本中的保留编号是入口标识，不要求恢复旧源码或旧构建。
构建/静态不代替 Live；不同 Target 独立 PASS 不自动构成跨端 parity。
每次适用性、失败和未执行项均要按当次实际结果记录，不能从计划推导 PASS。

## 资产与证据

脚本由 components/validation_tools/ 承载；task/ 仅保存验证数据，instance/ 保存 ignored 验证实例，result/ 保存固定路径证据。
没有结果或实例就不创建占位目录。原生 JUnit/Node 测试留在源码体系。
PLAYTEST / FIXTURE / DEBUG_PRIVILEGED 不混用；不实现 Persistent Write 或越过当前授权范围。
当前状态见[执行计划](../../documents/project/plan-execution.md)。

## 目标

保留以下现有合同与验收边界，不以迁移后的构建替代历史或真人验收。

## 运行

可选双端安装的真实互通检查（每次一个客户端和一个服务器）：

```powershell
./gradlew.bat verifyRelease :companion:assemble
node components/validation_tools/integration-server_peer/task/optional-install-smoke.mjs --target=26.3-neoforge
```

对五个已登记Target分别执行。同项instance/<target>/optional-install保存三个独立组合：产品客户端加入原版服务器、
原版客户端加入产品服务器、双方最终单JAR。使用既有官方安装缓存及console-server的官方服务端依赖，不使用开发classpath或probe。
工具仅在用户已接受EULA时运行，自动客户端1920×1080静音、最多4GiB；固定结果为result/<target>-optional-install.json。
只有当前进程确实入服、客户端USER读取/真实背包输入、无Peer明确不可用、双端Peer往返、服务端独立入口与禁用接管、
断线清空Peer及正常停服均成立才通过。原版客户端没有Runtime退出接口，服务端正常保存退出后仅终止本工具拥有的已断线客户端；
结果明确标注这项清理，不将其写成原版客户端正常退出。产品客户端使用GAME_ROUTED语义退出，检查endpoint清理。
旧endpoint不作为新进程就绪证据，既有活动实例拒绝覆盖；不从单端互通推断完整Peer委托或全部服务端目录已经验收。

独立服务端控制台的最终单JAR场景，不需要客户端或实验probe。先完成构建及Companion编译，再逐端执行：

```powershell
./gradlew.bat build :companion:assemble
node components/validation_tools/integration-server_peer/task/server-console-smoke.mjs --target=26.3-neoforge
```

同一命令可选其他已登记Target。工具使用本项instance下的console-server、官方Loader及既有安装缓存，
结果覆盖result/<target>-console.json；不读取日常实例或用户世界，使用者须已明确接受EULA。
安装缓存来自integration-packaged_runtime；缺少时明确拒绝，不能用开发classpath补齐。
命令拒绝、零玩家READ/OPERATE、实际MCP、原控制台并存、错误反馈和正常stop须全部成立才为PASS。
queued取消/关闭/执行时权限重查由runtime_core的ServerConsole生产引擎和HTTP测试补充，不能据此声称五端真人或全部三部署验收。

首批独立服务端世界DEBUG使用同一单JAR入口，加上world-debug场景：

```powershell
node components/validation_tools/integration-server_peer/task/server-console-smoke.mjs --target=26.3-neoforge --world-debug
```

五个已登记Target各执行一次，结果固定为result/<target>-server-debug.json，记录当前JAR哈希。
除控制台基础检查外，必须经实际Companion完成零玩家主世界/方块读取、Arm、OPERATE typed修改、旧版本/原值拒绝、
未加载查询及修改零加载、批次恢复和disarm拒绝，最终正常stop及endpoint清理。
工具只通过自有实例的原生stdin建立forceload夹具并在最后移除；这是准备条件，不是产品自动加载或玩家验收证据。
独立世界DEBUG不借server_console权限的HTTP回归、排队中显式取消/disarm/关闭零late mutation及生命周期版本拒绝由runtime_core测试补充。
仅切回READ或客户端交回输入不等于撤销已经授权的独立DEBUG，沿用现有合同。
范围仅主世界world/blocks及world.block.set，不能据此宣称完整服务器DEBUG、Peer委托、客户端互通或人工体验通过。

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
```

## 通过标准

以本说明中的具体结果字段、日志与失败条件为准；未执行的 Live/人工场景必须保持未验收。
