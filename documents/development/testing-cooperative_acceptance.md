# Developer Preview 集中合作验收

本清单只收真人步骤，预计约30～40分钟，可分两次完成。自动回归和Alpha发布不表示这些步骤已经通过。
使用本轮固定Alpha及相应源码；开始前由Agent读取[状态页](../project/status-milestones.md)和latest.txt，固定批次后不在中途换包。

## 准备

用户回来后说“开始集中合作验收”即可，由Agent启动和连接，不要求用户记端口或复制命令。
主验收选NeoForge 1.21.1：两种附加Mod均有匹配发布；需要时用参考端NeoForge 26.3补看体验。
客户端为2560×1440、RecRivenVI、主音量50%、音乐关闭；安装已登记的Custom Skin Loader和Zume。
26.3没有匹配Zume发布时跳过并说明，不安装其他Minecraft版本的文件。附加Mod不进入纯净自动回归或发行JAR。

人工客户端使用独立的Showcase验收实例，不借用自动测试世界。Agent使用已有的[实例配置工具](../../components/validation_tools/Prepare-InstanceProfile.ps1)及[客户端启动器](../../components/validation_tools/feature-control_ui_showcase/task/Start-ControlUiShowcaseClient.ps1)，选择client验收角色，不运行案例、不覆盖自动Core展示报告。
该启动器运行同批源码及验证探针；记录这一环境，不冒充最终单JAR纯净安装。五端最终发行JAR的独立安装证明另见固定快照清单的packagedRuntime。
启动后停在主菜单，等待用户准备好。现有隔离的原版服务器和产品服务器分别位于
validations/integration-server_peer/instance/1.21.1-neoforge/optional-install/client_only-server和both-server；
需要普通服务器权限测试时使用前者，Peer委托展示使用后者。启动和停止由Agent管理，一次只保留一个客户端和一个服务器。
验收前先明确服务器、玩家身份和当前授权；只使用这些可恢复测试副本，不接触重要存档。
需要用户执行控制台命令时，Agent提供实际正在运行的服务器控制台，不另开空终端。Peer委托被撤销后需由操作员重新签发，恢复OP不恢复旧委托。

## 一次完成的步骤

| 项目 | 用户做什么 | 应该看到什么；Agent核对什么 | 预计时间 |
| --- | --- | --- | --- |
| 观察与非接管协作 | 正常走动、打开背包并随意操作；按聊天通知配合一个小任务 | READ不阻挡输入；OPERATE原生命令/已授权DEBUG不抢键鼠、不改聊天草稿。Agent明确标出USER/DEBUG，并独立读回结果 | 5分钟 |
| 真实撤权与恢复 | 按通知在服务器真实控制台执行deop RecRivenVI，观察拒绝后再执行op RecRivenVI | 同一连接下原生权限实际变化；撤权后受限操作拒绝且没有效果，恢复后重新观察可用命令并成功；恢复OP不自动授予DEBUG scope或Arm | 5分钟 |
| 命令协作 | 自己打开聊天并输入未发送草稿，观察Agent通过OPERATE提交原生命令；随后在普通服务器重复权限检查 | 草稿和焦点保留，不需要接管。命令提交、原生反馈、世界实际变化分别表达；不拿产品Peer服务器代替普通服务器证明 | 5分钟 |
| 物理Esc撤销 | 明确允许接管后，先移动宿主光标、切走再切回，确认光标仍自由；等Agent通知再亲手按一次Esc，不赶固定倒计时 | TAKEOVER不抢宿主光标；Esc后活动和排队玩家输入取消、held keys/buttons=0、连续8秒Agent输入派发=0、mode=READ、reconsentRequired=true。接管Chrome退回READ活动提示、图标恢复原图、标题去除接管后缀；活动任务结束后Chrome收起、标题恢复原文。Agent自己的GUI Esc不触发人工撤销 | 3分钟 |
| 撤销后的合作与重新接管 | 先观察READ和独立授权OPERATE仍可用；准备好后明确说“同意重新接管” | 人工撤销提示持续保留；普通移动/模式切换不清除。只有显式重新申请接管成功后清除，不能把Agent policy当作Runtime读取聊天的能力 | 3分钟 |
| 完整展示与证据 | 观看一次界面/背包→世界交互→等待/断言→调试诊断→采证；查看截图与记录 | 虚拟指针/界面节奏自然；画面里的Operator UI不进入默认截图/录制。准备、玩家操作、DEBUG和未确认效果区分；另展示服务器结构化记录和有期限Peer授权 | 10～15分钟 |

Agent在每项开始前可用获准的服务器原生say命令发聊天说明，标为USER通知/准备操作；没有获准通道时直接在对话提示。
物理输入步骤等待用户回复，不使用Computer-Use、宿主输入注入或Agent Routed Esc代替。
用户Esc只撤销TAKEOVER；合法独立DEBUG是否继续，结合用户当时决定，不默认为全部工作已停。

## 结果与收尾

每项分别记录用户反馈、Native/Event/Audit及实际效果；没有看到或没有执行就保留待验收，不用自动PASS补齐。
遇到异常先停止相关步骤，保留结果；不盲重试可能已生效的命令或点击。
结束后Agent恢复受控前置状态、结束录制、释放输入与Lease、撤销临时委托和Arm，再正常保存退出、停服。
本清单完成后仍只按已执行范围更新结论，不自动发布1.0.0、启动扩展平台或开始整合包测试。
