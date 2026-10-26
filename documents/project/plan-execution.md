# Mine-Craft-Protocol 当前执行计划

客户端/服务端均可选安装及五端互通验证已完成。既有独立控制台、首批服务端世界DEBUG及固定Alpha保留，不重新解释为完整里程碑验收。
完整路线与已交付能力以[状态页](status-milestones.md)为准，已完成阶段见[阶段索引](phase-1-core_delivery.md)。

## 目标

交付两端可选安装的产品事实、原生通道协商和最终JAR互通证明。
只装客户端可加入原版服务器；只装服务端允许原版客户端加入；双方安装保留现有有限Peer，不把安装兼容扩成完整部署能力。

## 范围

- 五Target的mod_client_side与mod_server_side均为optional，由模板生成原生元数据。
- Forge采用原生acceptMissingOr；NeoForge保留optional注册并在发送前检查hasChannel；Fabric保留canSend。
- 无对端时明确SERVER_PEER_UNAVAILABLE，不回退为客户端/存档或隐式命令，不自动授予服务器权限。
- 客户端输入仍需TAKEOVER/Lease；独立服务端仍不适用TAKEOVER，控制台/DEBUG分别授权。
- 不改真人Esc、重新同意、资源版本、Arm、证据或截图隔离；不实现完整Peer委托与其余服务器目录。
- 不实施Fake Player、第二界面、Persistent Write、后续Phase、E1/E2/E3、寻路、整合包或UI美术调整。

## 步骤

1. 完成：核对五端原生网络与独立Runtime，设置可选安装事实并补齐缺失通道守卫。
2. 完成：参考端最终JAR验证仅客户端、仅服务端及双端三个组合，真实原版对端不加载本项目probe。
3. 完成：其余四端执行同一互通矩阵，验证正常背包输入、Peer可用/不可用、断线清理及权限边界。
4. 完成：五端Java测试、共享组件/Companion检查、发行构建、静态/文档/严格合规及diff检查。
5. 完成：安装、配置、架构与状态同步；只记录本次真实结果，不发布Alpha，用户已授权打包提交推送。

## 验证与协作

自动验证只操作自有validation实例，每次一个客户端加一个服务器；客户端1920×1080静音、最多4GiB。
禁止Computer-Use、桌面自动化、宿主输入注入；EULA使用用户既有明确同意，不访问Prism或重要存档。
服务端安装只用官方Loader、最终产品JAR，不加实验探针或开发classpath；MCP调用与Native身份/模式负控同时证明。
客户端正常输入通过本项目Runtime，原版对端通过原生quickPlay启动加入；不模拟真人桌面输入。
产品客户端通过语义菜单正常退出；原版客户端没有Runtime退出接口，服务端正常保存关闭后仅终止自有已断线客户端，单独说明清理方式。
结果绑定当前发行JAR哈希；不使用历史结果证明新候选，不将握手成功当作完整Peer授权证明。

## 集中真人步骤

本轮不做人工验收。最终客户端候选仍需按用户安排集中确认协作、真实撤权与物理Esc；
既有单次合作记录不自动证明新候选或完整统一验收。独立服务器没有物理Esc、HUD或宿主光标表面，不能伪造对应通过项。
当前互通验证不能替代完整三部署目录、细粒度Peer委托或最终人工体验验收。

## 结果

五端各三个最终JAR互通组合通过，固定结果为integration-server_peer/result/<target>-optional-install.json，当前JAR哈希逐一匹配。
产品客户端均经语义菜单正常关闭，服务器均正常保存关闭，endpoint及自有进程清理通过；原版客户端清理方式另记，不宣称正常客户端退出。
Forge采用官方缺失/原版兼容谓词，NeoForge缺失通道不发送负载；五端生产编译路径及反向门禁补充覆盖，不以静态检查代替互通。
其余服务器DEBUG目录、存档、Recording、补全与完整Peer委托仍待实施；两个领域里程碑尚未READY。
现有固定Alpha不变，不用新JAR配旧清单，不自动发布新快照。
按用户授权打包提交推送本次成果，不发布新Alpha，Persistent Write调用为0。
