# Mine-Craft-Protocol 当前执行计划

当前推进三部署方案的首批服务端世界DEBUG。初始控制台已完成，此前批次E世界试用和固定Alpha保留，不重新解释为完整里程碑验收。
完整路线与已交付能力以[状态页](status-milestones.md)为准，已完成阶段见[阶段索引](phase-1-core_delivery.md)。

## 目标

交付零玩家可用的主世界DEBUG闭环：有界深读、资源版本、独立Arm、typed方块修改、批次恢复和正常关闭。
复用现有认证、读取分类、授权/取消和证据，不新增模式、类别或玩家Lease，不以控制台命令冒充typed DEBUG。

## 范围

- 五Target沿用原生DedicatedServer入口，不创建在线/假玩家；集成服不额外创建Runtime。
- READ按独立read/debug/debug.world.read读取主世界元数据及最多64个显式方块，不自动加载区块、读磁盘或回退USER。
- OPERATE仅开放world.block.set及有界批次；独立debug.write/debug.world、world Arm、指纹与资源版本/原值条件继续生效。
- 原生属性校验与相邻更新保留；未加载传播明确部分失败，恢复原状态不承诺撤销全部副作用。
- 控制台USER与世界DEBUG分别授权，Operation结果不因新增DEBUG而继承控制台scope/命令回执。
- 默认服务端仍不授予控制台或DEBUG权限；真人Esc、重新同意、客户端输入与截图合同不变。
- 用户已批准不修改模板文件而推进可选部署；mod_side与Loader元数据本轮不改，单端互通仍须实机证明。
- 不实施Fake Player、第二界面、Persistent Write、后续Phase、E1/E2/E3、寻路、整合包或UI美术调整。

## 步骤

1. 完成：核对独立服务端/客户端DEBUG边界，保留既有改动，不提交或推送。
2. 完成：参考端以正式Deep Observation/Arm/typed mutation打通零玩家读改恢复闭环，单JAR经真实Companion运行。
3. 完成：移植其余四端原生方块与生命周期差异；Fabric区块守卫接入common入口，图形Hook仍为client。
4. 完成：生产HTTP负控与资源/取消测试；控制台scope不授权DEBUG，也不成为DEBUG批次的前提。
5. 完成：冻结候选，五端最终JAR经实际Companion零玩家实测；参考端及Fabric客户端单JAR入世界/保存退出回归，spotlessApply/check/verifyRelease/严格合规通过。
6. 完成：Native/OpenAPI/MCP与现行文档、结果入口对齐，不把首批目录或构建标成完整三部署验收。

## 验证与协作

自动验证只操作自有validation实例，每次一个服务端；必要客户端1920×1080静音、最多4GiB。
禁止Computer-Use、桌面自动化、宿主输入注入；EULA使用用户既有明确同意，不访问Prism或重要存档。
服务端安装只用官方Loader、最终产品JAR，不加实验探针或开发classpath；MCP调用与Native身份/模式负控同时证明。
测试核对READ零mutation、缺scope/Arm/模式、旧版本/原值/生命周期拒绝、未加载无加载、批次恢复与正常关闭。
自有原生控制台只建立测试所需的loaded夹具，准备与DEBUG结果分别标源，不宣称产品会自动加载。
队列取消/disarm/关闭用生产引擎和真实HTTP确定性测试；仅交回输入不取消合法独立DEBUG。
正常日志/有限USER命令保持回归，不以Operation completed冒充世界效果确认。

## 集中真人步骤

本轮不做人工验收。最终客户端候选仍需按用户安排集中确认协作、真实撤权与物理Esc；
既有单次合作记录不自动证明新候选或完整统一验收。独立服务器没有物理Esc、HUD或宿主光标表面，不能伪造对应通过项。
最终三部署验收还须覆盖无模组客户端加入及显式操作员授权，不能用零玩家控制台代替这些组合。

## 结果

首批服务端DEBUG五端最终单JAR闭环及HTTP负控通过，结果为integration-server_peer/result/<target>-server-debug.json；
参考端及Fabric客户端单JAR入世界、USER读取和正常保存退出通过。当前证据入口见状态页，未做真人或全部部署验收。
现有固定Alpha不变，不用新JAR配旧清单，不自动发布新快照。
完整可选双端、无模组客户端加入、其余服务器DEBUG目录/存档/Recording/补全与Peer委托仍未完成。
本轮不提交、不推送，Persistent Write调用为0。
