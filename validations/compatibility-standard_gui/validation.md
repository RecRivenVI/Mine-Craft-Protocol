# Standard GUI compatibility

验证当前源码、Native 合同、Target 所有权和制品，而不是某个开发阶段的版本标签。

## 本地入口（不启动 Minecraft）

```powershell
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-StandardGuiLocalGate.ps1 -Offline
```

该入口构建五个实际 Target、验证 OpenAPI/生成模型，保留本项路由、类型与实现存在性断言。
制品身份取自 gradle.properties。仅在本轮已经完成构建时可用 `-SkipBuild`；
该选项不代表重新构建过。离线需要已有依赖缓存。

`-SkipBuild` 只跳过 Gradle，五端源码/Schema/Hook/产物检查仍执行。产物按精确文件名及 Loader 元数据核对身份和版本。
门禁自身的隔离正反例：`pwsh -File components/validation_tools/integration-server_peer/task/Test-LocalGateChecks.ps1`。

## Runtime 场景（另行授权）

当前原版语义 UI 专项（标题页起点，不使用日常实例）：

```powershell
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-VanillaUiTreeConformance.ps1 -Target 26.3-neoforge -BaseUri http://127.0.0.1:25582 -TokenFile validations/feature-runtime_probe/instance/26.3-neoforge/client/minecraft-protocol/token
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-VanillaRenderedUiConformance.ps1 -Target 26.3-neoforge -BaseUri http://127.0.0.1:25582 -TokenFile validations/feature-runtime_probe/instance/26.3-neoforge/client/minecraft-protocol/token -Cases all
```

工具复用 feature-runtime_probe 专用实例；不创建或改写世界。通过标准为实际检查成功、
设置恢复、回 READ 且 held input 为 0；固定 result 只证明列出的 Native 场景，不是全 UI 验收。
有存档时必须完成世界行选择，没有存档时明确不含该检查。

Rendered 专项验证资源包选择/排序、切石机、织布机、附魔、交易、信标、书本编辑/签名/翻页和两种告示牌。
它只使用本项授权的 runtime-probe 隔离实例，创建并复用带工具所有权记录的创造世界；
准备动作使用玩家命令，标为 diagnostic Arrange，不冒充正常 gameplay。UI Act 必须走标准
GAME_ROUTED 语义节点；不允许截图、手写坐标或直接按钮业务回调。结束恢复资源包顺序、
回标题页/READ、held input=0；任一检查失败结果写 FAIL，固定 rendered-ui-<target>.json
绑定当前源码与工具哈希。五端分别实际运行，不从代码相同推断结果。

Fabric 消费者专项在 Rendered 专项通过后运行；要求网页已连接同一 runtime-probe 实例，
客户端处于标题页/READ。它经真实 MCP stdio 与网页 HTTP 检查新语义字段、书本编辑和告示牌，
固定结果为 ui-consumers-26.3-fabric.json；不使用 CUA 或截图判断。

```powershell
$env:MCP_MINECRAFT_BASE_URL='http://127.0.0.1:25583'
$env:MCP_MINECRAFT_TOKEN_FILE=(Resolve-Path validations/feature-runtime_probe/instance/26.3-fabric/client/minecraft-protocol/token).Path
node components/companion/scripts/live-vanilla-ui.mjs
```

[具名黑盒场景](../../components/validation_tools/compatibility-standard_gui/task/Invoke-Phase7AlignmentConformance.ps1)保留实际参数，先阅读再执行。
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

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
```

## 通过标准

以本说明中的具体结果字段、日志与失败条件为准；未执行的 Live/人工场景必须保持未验收。
