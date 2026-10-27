# Minecraft 26.3 移植

26.3 NeoForge/Fabric 使用 SDL 输入与窗口后端及 Renderpearl GPU 接口。
本次只迁移既有行为，不调整控制模式、视觉设计、权限或证据分级。

## 输入与窗口

独立服务器Runtime使用各Loader的SERVER_STARTED/STOPPING生命周期，不在集成服创建第二入口。
Forge1.20.1的服务器目录为File、命令结果回调为三参Brigadier ResultConsumer；其余Target目录为Path、回调为两参CommandResultCallback。
Fabric原版CommandSourceStack.source为private，反馈委托给DedicatedServer自身，不使用NeoForge扩宽访问或新access widener。
Fabric的access widener由全局元数据声明，文件放在src/main/resources中，使原生开发客户端和独立服务端都能解析；声明内容及发行JAR中的路径一致，不改变客户端访问规则。
所有命令仍经服务器自身任务队列和原生分发器，普通终端输入不被替换；未持客户端Lease不影响独立操作员命令。

世界原生引擎由同Target的集成服、独立服务器和Peer复用；Fabric引擎、存档适配器和服务器访问器位于main，客户端暂停状态通过客户端安装的读取函数提供。
独立服务器不加载IntegratedServer等客户端类型。属性、容器、调度、加载摘要和环境修改版本所需Mixin在公共配置注册；绘制与鼠标Hook仍仅客户端注册。
26.x的ChunkPos为仅接收两个整数的record，区块中心由BlockPos坐标右移4位推导；1.x保持原生API。
显式服务器选择器有自己的维度/采样中心，真实玩家只在玩家/菜单资源中必需；无玩家容器修改仍标记dirty并使用Vanilla正常同步，不访问虚构菜单。

生命HUD采样：26.3在Hud/Renderpearl的extract方法观察GuiGraphicsExtractor.blitSprite；
26.1.2使用Gui/Blaze3d RenderPipeline，1.21.1使用GuiGraphics.blitSprite及renderAirLevel。
Forge1.20.1分别观察Gui.renderHeart和原生ForgeGui四种绘制，以icons.png原生UV解码图标身份，帧边界来自GameRenderer。
五端均先原样调用绘制一次再采样；Forge自有方法remap=false，但其内部Minecraft blit保持remap=true。
专属字节码测试约束委托原参数与调用次数，不允许以Mixin静默跳过代替实际渲染验证。

BlockState 的 DEBUG 属性与 Resource Version 统一使用原生 `Property.getName(value)` 的序列化值，不能使用枚举 `toString()`；
例如 NoteBlockInstrument 的内部枚举名称不是可写回的属性值。Native setter 先校验完整映射，再进行一次原版 setBlockAndUpdate；
同类型更新保留未指定属性，跨类型使用默认状态。各端解析名称/API 自行适配，不共享 Minecraft 类型。

CHAT 玩家命令 helper 使用各端当前 Chat 绑定及原生字符回调，不直接调用 sendCommand；独立 NATIVE 路由见下方原生命令边界。
26.3 的 SDL 键先转协议键，旧端保留 GLFW keysym/scancode；Forge/NeoForge 的带修饰 Chat 绑定明确拒绝，
不假装普通 T 键仍有效。聊天框和连接身份在每一阶段复验，原生 256 UTF-16 输入上限包含斜杠。
各 Target 自己提取 ChatScreen/EditBox 和派发字符，MC 无关的草稿守卫和阶段编排在 runtime_core。
命令方块/矿车配置由各端原生 ServerboundSetCommandBlockPacket/ServerboundSetCommandMinecartPacket 提取，
共用最终命令许可与证据分类。Forge 最终 send 重载为两个参数，1.21.1 为 PacketSendListener 加 flush，
现代端为 ChannelFutureListener 加 flush；Forge 不引用其版本不存在的独立签名命令包。
26.3 的时间查询使用原生 time query gametime，不假定旧版本的 daytime 字面量仍存在。

世界持续动作的共享 core 只编排有界 gesture；各 Target 的 WorldGestureMixin 在 MultiPlayerGameMode
原版入口复验客户端目标，未持有语义 gesture 时不改变 Vanilla。新钩子计入生产 Hook manifest 与兼容门禁。
26.3 的 interact 合并 EntityHitResult 参数；1.x 的 interact/interactAt 分开。物品使用时长、原版 pick
所属对象、键位枚举和反转设置按 Target 适配，不共享 Minecraft 源码。取消仍保留原生松键效果。

UI 原生容器在五端分别提取。1.20.1/1.21.1 用具名 Invoker 读取受保护列表行边界，
Accessor 读取世界行 summary，字符回调仍是 GLFW 参数；26.1.2/26.3 使用 CharacterEvent。
26.3 NeoForge 的字符入口公开，其他端经具名 Invoker，不用 Reflection。
Forge/NeoForge 创造标签只取当前 Loader 页内集合，Fabric 取原版可见 tabs；
起点使用各端原生 getter/Accessor。26.x RecipeButton使用原生display stack；
1.20.1从当前Recipe与collection.registryAccess取得显示结果，1.21.1先解RecipeHolder.value。
旧端仅在实际可见、启用且视口交集非空时公开物品标签/种类/数量，不调用不存在的新版接口；真实菜单填入仍须另行回读，不以显示结果证明服务器槽位变化。

资源包控制使用 Native 32 像素图标命中区；旧端从父列表行边界取起点，现代端从 Entry content 坐标取值。
切石机旧端读取 Recipe/RecipeHolder，现代端读取可见 SingleInputEntry 与 SlotDisplay；
织布机和信标读取注册资源身份，Forge 1.20.1 的信标 Effect 不是 Holder。
1.20.1/1.21.1 书本编辑与签名共用 Screen、原生 TextFieldHelper，通过 keyboard-only 语义字段输入；
26.1.2/26.3 使用 MultiLineEditBox 和独立签名 Screen。告示牌四行统一经原生键盘选行，
不虚构鼠标框。五端保留各自原生格式与输入入口，外部选择器合同一致。

V0 协议继续使用既有 GLFW 数值语义；26.3 的 NativeInputMapping 将按键、修饰键和鼠标按钮转换为 SDL。
SDL KeyEvent 的 key 是 scancode，keycode 由 SDL 的当前键盘布局解析；不把旧 scanCode 直接当成新 keycode。
键盘、文字、IME、鼠标与滚轮的原生任务统一在 SDLEventHandler 入队边界标记来源，
Owner Thread 上仍执行相同的 TAKEOVER 排他与真人 Esc 撤销规则。

Vanilla 的 grabMouse/releaseMouse 均可能定位宿主光标，因此 TAKEOVER 中拦截这两个入口；
Runtime 只关闭 SDL relative mode，不调用宿主光标定位。相对 Agent 输入仍经过 Vanilla camera processing。
逻辑捕获/释放只有状态真实变化才归中并更新 Pointer 显隐，普通 GUI 换页中的重复释放不重置坐标。
1.20.1/1.21.1 的 InputConstants 接收 GLFW window handle，26.1.2 接收 Window；
26.3 的 grabMouse/releaseMouse 分离。各端在屏蔽物理 warp 前记录游戏的显式定位/模式请求。
26.3 resyncMousePosition 读取 SDL 宿主位置，不是游戏定位意图；TAKEOVER 中只恢复逻辑位置。
窗口图标保存实际 SDL surface 的像素、格式与 pitch，结束接管时重新应用，不用默认图标替代。

## 渲染与证据

Forge1.20.1的ForgeGui覆盖Gui.render，F3及Demo文字实际在renderHUDText的两个String绘制点，动作栏/唱片在renderRecordOverlay的FormattedCharSequence绘制点。
采样委托Forge自有方法时remap=false，内部Minecraft绘制保持remap=true；每次先原参数绘制一次并保留int返回值，再进入现有有界晚期帧。
F3和Demo按真实renderDebug/isDemo分支区分，普通事件文字不伪装为这两类；不使用Gui.render、DebugScreenOverlay.renderLines或Gui.renderDemoOverlay等被绕过的出口补缓存。

世界 USER 呈现通过原生 LevelRenderer 的瞄准轮廓方法只读观察。
1.20.1/1.21.1 的 renderHitOutline 接收 Entity、BlockPos 和 BlockState，按同一原生 Shape 排除空轮廓；
26.1.2 的 renderHitOutline 接收 BlockOutlineRenderState 与 ARGB；26.3 两端改为 submitHitOutline。
现代端读取原生 shape/pos/color，旧端遵循实际 CollisionContext，不用跨版本反射或修改绘制参数。
GameRenderer.render 的 HEAD/TAIL 分隔完成帧，GUI/Overlay、隐藏 HUD、摄像机/目标/尺寸变化及过期使样本失效。
输出只证明原生轮廓提交，GUI 中心只是参考，不是方块几何、材料、最终像素或可执行输入落点。

最终内容 Hook 改为 Renderpearl GpuSurface.blitFromTexture 之前：
游戏内容、证据 readback、Operator Chrome/Pointer、present 的顺序保持不变。
静态 Hook 数量和 Runtime manifest 按真实 Target 更新，不沿用旧 API 的数量。
圆球以本地 (0,0) 为对称中心，通过原生 pose 栈的小数平移定位。1.20.1/1.21.1 的
Operator pass 使用整数 GUI 宽高投影；26.1.2/26.3 的原生 GuiRenderer 使用 framebuffer
宽高除以 GUI scale 的浮点投影，而输入 GUI 宽高向上取整。只在绘制原点转换两者，
避免 2560×1440 / scale 6 等非整除尺寸的偏移，不改输入落点或其他 Chrome 几何。
真实人工 Esc、焦点、宿主光标和视觉质量仍需用户单独验收，自动 smoke 不替代这些结论。

## 原版 UI 原生区域

本地HUD文字在26.3位于client.gui.hud，旧三端位于client.gui；三种消息都通过各端原生setTitle/setSubtitle/setOverlayMessage设置纯文字。
完整Component内容守卫在1.20.1使用FriendlyByteBuf.writeComponent，其余使用TRUSTED_CONTEXT_FREE_STREAM_CODEC，并另外绑定完整解析文字的UTF-16单位。
原生样式Codec需真实Loader/注册表，相关断言在src/probe的Mixin内执行，避免伪造FML启动或让probe与产品产生JPMS split package。
投影/私有元数据隔离仍由普通JUnit检查；实际接口、Native内容守卫与最终制品分别验收，不因编译通过推定原生通过。

原生 Widget/Listener 图与 render-only 区域分开读取。26.1.2/26.3 的 AbstractScrollArea 提供统一滚动基础；
1.20.1/1.21.1 分别读取 AbstractSelectionList 与 AbstractScrollWidget，使用各自真正的滑块高度和范围。
旧标题页语言图标使用 narrator.button.language，新版页面使用 options.language，不改原版名称或靠坐标补偿。
空标签 Widget 可以读取原生 Tooltip；现代版本通过 WidgetTooltipHolder 的只读访问器取得它。

保存的服务器行在 26.x 用 entry.getContentX/getContentY 定位图标，1.x 使用列表实际行边界。
连接使用右半区，排序使用左侧上下半区；按原版悬停状态和首尾边界发布操作。
1.x/26.1.2 另遵循 touchscreen 选项，26.3 不继续假设该选项存在；鼠标坐标按版本使用原生 GUI 缩放。
行名称不用混入精确延迟和隐藏地址的 narration，排序后的子目标身份绑定新序号。
旧版添加/编辑页是 EditServerScreen，26.3 为 ManageServerScreen，外部语义选择器不依赖类名。

五端自绘菜单卷轴分别按当前 Minecraft 字节码核对：创造菜单读取 selectedTab、searchBox、scrollOffs 和 canScroll，
不把普通列表的像素滚动量套到它的 0–1 比例上。织布机/切石机按下区从 top+9 开始，
滑块绘制分别从 top+13/top+15 开始；语义滑块取绘制与按下范围的交集，拖动中允许自身裁剪高度变化。
交易卷轴保留原版按可见七条交易计算的离散滑块位置；复用 trade identity 拒绝滚动后的旧行。
1.20.1 的固定石头配方夹具不启用卷轴，不将此分支当作拖动验收；1.21.1 与 26.x 使用凝灰岩多行配方。

ui.scroll.set的Screen分支绑定实际Screen身份及四种已确认的track，单独版本化当前比例、布局和语义上下文。
创造模式同步scrollOffs与原生ItemPickerMenu.scrollTo；织布机按totalRowCount减去四行、切石机按getOffscreenRows计算原生显示起点；
交易按总数减去七条计算整数起点，不执行选择回调或发送包。无范围时只接受0；普通AbstractSelectionList仍使用像素，不混用同名字段的单位。

26.x 的 ActiveTextCollector 使用原生 prepared glyph active area；旧版用 Font advance 加原生 style hit test 确认区域。
进度图内部类型在 26.3 NeoForge 已公开，旧版本用精确只读 Accessor/Invoker；Fabric 不读取 NeoForge 的页码扩展。
1.20.1 进度画布仅声明原生拖拽，不虚构新版才有的滚轮处理。
初次 LoadingOverlay 重载期间不编译 Operator 文字管线，避免尚无 shader source 时崩溃；证据 readback 仍在 Operator 层之前。

## 依赖与旧 Target

窗口装饰在 Window.setTitle/setIcon 的原生调用处统一追加，不取消整个 setTitle。
旧端处理 GLFWImage 多尺寸 RGBA 数组；26.3 使用
[SDL_GetSurfaceImages](https://wiki.libsdl.org/SDL3/SDL_GetSurfaceImages) 保留完整图标尺寸链，
原始像素/format/pitch 用于还原，独立转换到 RGBA32 后合成圆球。
[SDL_AddSurfaceAlternateImage](https://wiki.libsdl.org/SDL3/SDL_AddSurfaceAlternateImage) 保留替代尺寸引用，
每次发布后释放自有 surface 与 buffer；读取返回的指针数组用 SDL_free 释放，不释放游戏拥有的 surface。
颜色转换、角标合成不修改游戏传入像素；关闭恢复发生在窗口销毁之前，不读取系统图标作为底图。

UI DEBUG 的外部合同在五端相同，原生列表滚动范围使用对应版本的 maxScrollAmount/getMaxScroll。
滑块使用各端 AbstractSliderButton.setValue，而非只写 value 字段；原生 applyValue/updateMessage 保持关联，
其设置回调可能写客户端选项。状态效果在 1.20.1 为直接 MobEffect，后续为 Holder；
Forge/NeoForge 额外遵循 IClientMobEffectExtensions.isVisibleInGui，不调用其绘制方法。
图标顺序为原版倒序，增益/减益分别排布；不把未显示等级/时长放进 USER 数据。
HUD 文字在 26.3 由 Hud 持有，旧版为 Gui；26.x ToastManager 与 1.x ToastComponent 的
可见队列、实例槽号和显隐插值字段不同，以各 Target 精确的只读 Accessor 适配。
1.x TutorialToast 是 title/message，26.x 为已排版行；SystemToast 在 26.3 使用 titleLines。
Advancement 在 1.20.1 是直接对象，后续为 Holder；只读取 display，不调用获奖逻辑。
Toast 文字在 1.x 的 render 返回后、26.x 的 extractRenderState 完成后采样；采用该次实际传入的时间和字体。
多行进度以 1500 ms 切换标题/名称；1.x 的原版 alpha OR 4 保留边界微弱文字，26.x 真正透明的帧不发布文字。
1.x 的 HIDE 返回可能发生在未绘制的提前出口，因此保守不采样该返回；不改 Vanilla 显隐或绘制流程。
样本绑定 GUI 尺寸并在一秒后失效，USER 仍要求提示完全展开；Recipe 只发布显示的两行文字，不推断全部配方/图标内容。
这些差异不放宽 USER 可见性、不增加 Toast 输入动作，也不复制实例或推定其他 Target 已通过。
PlayerTabOverlay 的 visible、header、footer 与只读 getPlayerInfos 在五端分别绑定；
26.3 经 Hud.getTabList，旧端经 Gui.getTabList。显示名走原生 getNameForDisplay，
由各版本处理队伍装饰、显式显示名、listed 筛选、列表排序与 80 人上限，不自行模拟排序。
不额外读取 profile 名称、UUID、原始延迟或列表分数，也不把 Tab 键状态当成实际可见性。

热栏选择在 1.x 是 Inventory.selected，26.x 使用 getSelectedSlot；26.3 的 team color 是 Optional，
旧版使用 ChatFormatting。1.21.1/26.x 侧栏支持 number format，必须使用实际显示字符串，不能返回隐藏原始分数。
1.20.1 原版侧栏先过滤隐藏分数，却按未过滤集合数量执行尾部裁剪；适配按实际可见行处理，
不把它改写为新版固定前 15 行算法。该版标题在行循环中绘制，空侧栏不发布 USER 标题；
新版本在循环外绘制标题，空侧栏仍可显示。此差异只影响读取，不修补 Vanilla 绘制或计分规则。

三条固定 Minecraft 线只更新兼容 Loader，不迁移 SDL；它们保留各自的 GLFW Hook。
Minecraft 自带 Netty、Mixin 与 LWJGL 由游戏/Loader 管理；补充 HTTP 模块与所属游戏版本对齐。
独立测试依赖版本由版本目录负责，不能用测试版本声称 Minecraft 内置库已经升级。
新 Target 的最低 Loader 要求从其自身事实生成，不沿用旧 Minecraft 的最低要求。
旧实例目录非破坏迁移；不要用新版本自动打开重要旧世界。

## 旁观者菜单输入适配

26.3 的 SpectatorGui 由 Hud 持有，其余三条固定版本由 Gui 持有；菜单、页码与当前键位只通过具名 Accessor 读取。
`getHotbarAlpha` 只读调用保留原版五秒超时和末段渐隐，不自行续期菜单。
26.3 键位值是 SDL scancode，由现有 NativeInputMapping 的对应关系反向得到 V0 GLFW 编号；
固定版本保持 KEYSYM/GLFW，不改变外部输入编号。Forge/NeoForge 的修饰键绑定必须显式拒绝，不能漏掉 modifier 仍发送主键。
图标位置遵循原生 20 像素间隔、16 像素图案及渐隐期间的纵向位置，只作为显示边界。
实际选择/执行继续调用 Runtime 原生键盘路径；中键打开继续走原生鼠标路径，不调用菜单或传送业务函数。
选择后的名称可以作为 USER 读取，未选中名称只属于 DEBUG；菜单与绑定变动共同使旧操作目标失效。

## 原生玩家命令边界

五端在 ClientPacketListener 方法入口和 Connection 最终公开 send 重载检查 Agent 命令；
不替换原版命令执行，不在 Screen 的崩溃包装回调内抛出协议异常。回调返回后由输入 Operation 报告拒绝。
TAKEOVER 外真人命令不受拦截，异步许可失效后不允许借用另一 Operation 或 Lease。

| Target | 文本链接入口 | 最终 send 参数 | 原生命令包 |
| --- | --- | --- | --- |
| 1.20.1 Forge | sendUnsignedCommand 返回 boolean | Packet、PacketSendListener | ServerboundChatCommandPacket |
| 1.21.1 NeoForge | sendUnsignedCommand 返回 boolean | Packet、PacketSendListener、flush | 普通与 Signed 两种 |
| 26.1.2 NeoForge | sendUnattendedCommand 带 Screen | Packet、ChannelFutureListener、flush | 普通与 Signed 两种 |
| 26.3 NeoForge/Fabric | sendUnattendedCommand 带 Screen | Packet、ChannelFutureListener、flush | 普通与 Signed 两种 |

1.x 的客户端 Dispatcher 泛型是 SharedSuggestionProvider，26.x 是 ClientSuggestionProvider。
本地命令发现使用独立ClientSuggestionProvider，不调用真人实例的customSuggestion，避免取消其单一服务器补全future。
1.x构造器为connection/client，26.x还需原生permissions；旧端玩家名候选方法名为getCustomTabSugggestions，现代端为getCustomTabSuggestions。
Forge映射构造器的两个参数元数据同名，子类使用显式构造器，不能生成匿名子类的同名参数。
服务器补全返回明确未实现/需求标记，不发送探测命令或抢占真人聊天请求。
NATIVE提交调用真实玩家连接的原生命令API，仍经过同一最终方法/包守卫；不构造原始包、不提升console source。
批次外层owner交接和内层逐项future均保持取消传播，mode handback不取消已独立准入的合法工作。
每端有界解析实际收到的命令树；别名子上下文可能从参数开始，必须使用 context.getRootNode，
不能把参数名称误分类成新命令。同一根的别名续段合并为完整 root+arguments，
避免设置命令被误判为无参数查询，或把查询别名误标为修改；原生上下文预算在合并前计数。
execute 返回命令树根的重定向继续解析终端根。
真实 Brigadier 测试分别覆盖直接命令、别名和嵌套别名；函数/调度内容未决时拒绝。
原生参数解析结束按 Brigadier 的 canRead 判定，不能无条件对 getRemaining 取子串。
1.20.1 的 ComponentArgument 会在有效 JSON 末尾把 reader 推进到输入长度后一位；
五端保持与原生 execute 一致的结束规则，不截断、改写命令或忽略仍可读取的非空后缀。
实际 ComponentArgument 回归另检查损坏 JSON 与尾随文本，不能用根命令分类代替完整解析。
26.3 的 time query 不再提供旧 daytime 分支；跨端测试使用实际均支持的 gametime，不放宽语法校验。
输入引擎在进入客户端队列前捕获短期 Operation 许可；发包时再检查当前连接、scope、模式和 Lease。
Lease 读取复用 ProtocolState 的同一个 volatile 记录，不在持有客户端控制锁时反向取得协议监视器，避免与退出竞争死锁。
新增的两处方法和一处发包取消注入均公开计入 Hook manifest；观察 Hook 仍不得取消流程。

## 有界客户端世界读取

首批世界语义动作：26.3/26.1.2 的 pick(float) 位于 Minecraft，1.20.1/1.21.1 通过 GameRenderer 原版 pick。
具名 Invoker 只刷新原生命中结果，不调用攻击/使用业务函数；实际按键仍走原生回调和 GAME_ROUTED 来源票据。
26.3 使用 SDL KEYBOARD 和 NativeInputMapping 反向转换；固定版本使用 KEYSYM/GLFW。
Forge/NeoForge 的额外 modifier 绑定明确拒绝，不能丢掉修饰键后误发主键；Fabric 没有该 Loader 绑定扩展。
1.x 只有 Y 轴反转，其余两代支持各自 X/Y 选项；灵敏度和反转仍由 Vanilla 处理，纯数学只计算相对误差。
对 LevelChunk.setBlockState 的客户端目标缓存记录单调修改序号；
两条 1.x 回调参数为 position/state/boolean，26.x 为 position/state/int。
RETURN 的非 null 旧状态才记为实际变更；不变确认包不误增，真实 A→B→A 仍两次失效。
同步 destroyBlock 范围内按原版破坏前流体状态核对替代，并记录有界邻居变化；
线程局部来源不能吸收其他线程或调用外更新。只追踪客户端弱 Chunk，不收集服务端块数据。
1.20.1 的 ItemStack.getUseDuration 无参数，其余传入玩家；副手持续使用仍交给普通 use 键与原版优先级，
不直接调用目标版本的 useItem 业务方法。客户端 usingHand/riding 读取沿各自原生 API。
原生分发拆出客户端同步实现，在同一授权 admission 中使用；不能嵌套异步请求再 join 等待客户端自己。
五端普通方块瞄准分别使用对应 Minecraft 的 BlockState.getShape、CollisionContext 和 OUTLINE clip；
候选来自真实盒体及面中心，不共享 Minecraft 源码或按原版 ID 特判。原生 pick 仍决定是否可交互。
工作台/铁砧联合场景复用各端原生 Screen 与菜单槽位；Shift 需真实路由按下/松开，不能只依赖鼠标 modifier。

五端 Jigsaw 配置都读取原生 ServerboundSetJigsawBlockPacket.getFinalState，生成包单独识别，不解析假命令占位。
1.20.1 的配置包构造参数没有 selection/placement priority，另外四端有这两个整数；
旧两端使用 ResourceLocation，26.x 使用 Identifier。生产路径均由原版 UI 构造包，不自研发送捷径。
Generate 在原生 onDone 保存配置并关闭 Screen 后才发送生成包；拒绝第二包必须保留第一包来源和未确认语义。
Structure 的四种 UpdateType 五端分别映射；1.20.1/1.21.1 的包没有 strict 布尔值，26.x 多一个 strict。
原生 handler 均先写参数再分派动作；扫描、文件保存、模板加载不能按同一类“配置完成”处理。

UI 物品守卫不使用 ItemStack 的普通 hashCode。1.21.1 与 26.x 在各自客户端注册表上调用原生
ItemStack.STREAM_CODEC，编码 count=1 的副本，数量另外校验；1.20.1 用 FriendlyByteBuf 编码客户端物品 ID
和现有 NBT tag，不复制整个 NBT。共享 core 只负责字节预算、密钥指纹和上下文令牌，不引用 Minecraft。
这只证明原版客户端物品数据；不承诺第三方自定义 capability/codec 自动完整适配。
拖动状态在各端原生鼠标回调返回后、同一客户端线程内推进；下一个输入之前仍校验外部更新，
耗时快照校验完成后再检查 originating input permit，取消不能因编码耗时而变成晚到输入。

五端 WorldClientSnapshot 只在客户端线程读取当前 level/player；Minecraft 相关适配不共享源码。
1.20.1 的 ChunkStatus 位于 chunk 包，其余位于 chunk.status；两条 1.x 使用 Inventory.selected，
26.x 使用 getSelectedSlot，维度键分别使用 location/identifier。缓存查询都传 FULL、required=false，
直接从返回的缓存 Chunk 读取 BlockState；实体迭代来自客户端 entitiesForRendering，不查询 ServerLevel。
纯 Java WorldClientModel 共享字段白名单、半径、扫描/时间/字节预算；不改变任何目标的输入路径或 UI USER。

实体反馈沿客户端同步事实：LivingEntity.isBaby、TamableAnimal.isTame/isInSittingPose。
Forge 1.20.1 的牵引方法在 Mob，其余四端在 Leashable；getLeashHolder 只解析当前 ClientLevel 的缓存实体。
持有者未知时不输出 leashHeldByPlayer，不能把延迟解析解释成其他所有者；不发布 UUID、love 或繁殖冷却。
原版 getLeashHolder 会惰性填充客户端缓存，必须先解析持有者再读取 isLeashed，避免同一响应矛盾。
村民测试物品 NBT 的数量键在 1.20.1 为 Count，其余为 count，生产交互仍走原版 use 与语义菜单，不使用 NBT 写入捷径。

命令载荷只读访问各端原生 BlockInput.tag；1.20.1 的 ItemInput.tag 与 1.21.1 的 components 通过精确 Accessor，
26.x 使用原生 record components()。现代 DataComponentPatch 在当前客户端 RegistryOps 上编码，
旧端仅捕获 BlockEntityTag/EntityTag；不构造物品、查询玩家库存或服务器对象来分类。
1.x CompoundTag/StringTag 使用 getAllKeys/getAsString，26.x 使用 keySet/value；
共同的预算、字段提取和效果聚合在 runtime_core，Minecraft 类型不跨 Target 共享。

全量区块快照均从原生 replaceWithPacketData 的 HEAD 记录整块失效：1.x 接收 FriendlyByteBuf、CompoundTag 与实体数据回调，
26.1.2 改为高度图 Map，26.3 改为 x/z 与 ClientboundLevelChunkPacketData。回调不消费或改写这些参数，
与 setBlockState 的位置级历史共存；各端测试核对实际方法唯一性、返回类型与生产注入元数据。
验证探针仅重发已加载 Chunk 的同一份原生数据，1.x ChunkPos 使用字段、26.x 使用 x()/z()。
Forge performCommand 返回 int，其余返回 void；测试 Mixin 分别采用 CallbackInfoReturnable 与 CallbackInfo。
旧 Forge 通过验证启动的 --mixin 参数早期登记探针，不能等到 Mod 构造时再登记；日常运行与发行 JAR 不加载探针。
其探针 AP 另产 refmap，但映射文件不进入产品 reobfJar 的输入；不让产品构建依赖测试映射，也不放宽发行探针排除检查。

Showcase的验证Toast使用原生API：1.21.1为getToasts()/ToastComponent及render返回Visibility；
26.1.2为getToastManager()/ToastManager，26.3为gui.toastManager()，两者由update维护有限显示时间。
1.21.1的TutorialToast构造器不接Font，26.x接Font；SystemToastId均可使用有界的long显示时间。
三个Neo的新增按钮由probe Mixin接入自有AutomationProbeScreen，仍需显式showcaseFixture，不能把测试依赖放入产品。
原生API差异依据当前编译构件核对；实际执行结果只看Showcase的绑定记录，不从此表推断五端Live通过。

## UI 文本编辑器

旧两端GameModeSwitcherScreen在render内发现F3已松开后才sendUnsignedCommand，26.x在keyReleased内同步发送原生游戏模式包。
因此旧端玩家输入请求必须涵盖随后一次真实菜单render，命令仍由原scope、Lease和可取消permit检查；不能因菜单已关闭推断服务器已切换模式。
不直接调用私有切换方法、不提前发命令，也不将授权ThreadLocal跨帧保留；取消时不得让菜单在READ下迟到提交。
延迟render入口刷新实际世界、玩家和连接：Forge同维度respawn可能换玩家却保留该Screen，不能只校验Screen对象和旧Core代次。

1.20.1/1.21.1的BookEditScreen使用pageEdit/titleEdit两个TextFieldHelper，26.1.2/26.3的页正文使用MultiLineEditBox，标题为独立EditBox。
旧两端滚动范围使用getMaxScroll，26.x使用maxScrollAmount；旧端没有TestInstanceBlockEditScreen，不引用不存在的管理类。
TextFieldHelper均提供selectAll/insertText、getCursorPos/getSelectionPos及setSelectionRange；通过Mixin读取其原生stringValidator，不自行复制行宽/页长算法。
AbstractSignEditScreen四行共享同一原生helper，typed修改非当前行时临时选择该行，完成后恢复原行与选择区；完整草稿绑定各行独立资源身份。
当前原版普通/悬挂告示牌最大行宽分别为90/60；生产代码不写死这两个值，仍使用对应原生validator。
旧两端BookEditScreen.titleKeyPressed只有Backspace与Enter分支，不调用titleEdit.keyPressed，不支持Ctrl+A或移动标题光标；
其USER节点声明backspace_from_end和完整值，通用Automation只发送有界真实退格，不能直接调用TextFieldHelper替代玩家。
26.x独立标题EditBox继续原生全选；通用输入在清空后核对真实文字，再决定是否键入替换值。
修改可能重建原生文字组件，恢复文字不承诺隐藏格式或服务器状态的事务回滚；结果和已知边界见Native协议。

## 世界强类型状态与效果

属性读取通过原生AttributeMap的已实例化map读取modifier，默认属性通过supplier getter取值，不调用getInstance创建对象。
1.20.1的map键为Attribute，1.21.1及以后为Holder；旧modifier使用getId/getAmount/getOperation，现代使用id/amount/operation。
玩家max_health别名在1.20.1映射实际generic.max_health，其他id直接使用原生注册值；修改校验native有效范围而不是默默clamp。
效果hiddenEffect通过只读Mixin accessor有界读取，1.20.1使用带FactorData Optional的八参数构造，调用该effect的createFactorData取得原生默认渐变；现代用七参数构造。
只恢复批准效果链字段，不复制渐变缓存。新增accessor只在客户端产品运行加载，不将独立服务端Runtime声明为已实现。
1.20.1的EquipmentSlot只有六项；1.21.1及以后按LivingEntity.canUseSlot发布nativeEquipmentSlots并验证适用性，不把body/saddle硬编码成所有Target通用位置。
flying通过既有native mayFly条件与普通abilities更新同步，noAi通过Mob原生标志；两者都是DEBUG路径，不模拟真人按键或授予权限。

实体创建的1.20.1/1.21.1原生工厂为EntityType.create(Level)，26.x为create(Level, EntitySpawnReason.COMMAND)；都不调用finalizeSpawn或读取任意NBT。
旧端moveTo及新端snapTo设置新对象小数位置与朝向，随后addFreshEntity；Chunk坐标旧端字段x/z、新端x()/z()，注册标识旧端ResourceLocation、新端Identifier。
成员读取使用native带数量上限的getEntities及getChunkNow，目的地和对象边界均不触发Chunk加载；移除统一使用discard而非伤害/kill。

## 世界环境

1.x的Overworld主数据提供实际可写dayTime，DerivedLevelData的其他维度setter为空，不发布伪独立时钟。
旧ServerLevel.setDayTime同时供自然tick与显式控制使用；只在tickTime中确切的那次自然调用设置局部标志并以finally恢复，其他具名调用照常递增控制版本，不阻止原生更新。
26.x在ServerClockManager.modifyClock后记录具名控制，普通tick不经过此入口。26.3提供公开ServerClockInstance；26.1.2实例与getter私有，使用只读Map/播放字段accessor识别实际实例，数值优先使用公开getTotalTicks/getPartialTick。
天气旧端来自Overworld的ServerLevelData，新端来自已载入的server WeatherData；具名setWeatherParameters计控制版本，自然倒计时不冒充外部编辑，当前raining/thundering仍进入版本判断。
旧GameRules经原生visitor及现有rule值读取，新端使用availableRules；ID不做猜测映射，均用实际argument解析与setter/callback。
1.x的原生整数argument使用完整int域，现代项可能有限制；发布真实minimum/maximum，不给旧版附加现代边界。时钟播放配置、累计gameTime与调度仅诊断读取，不新增模拟控制。

## 世界原生转移

1.20.1/1.21.1使用Entity.teleportTo的ServerLevel、坐标、相对字段集合及朝向七参数重载；26.x增加保留原生移动状态处理的boolean参数。
不调用teleportToWithTicket或复制玩家命令实现，也不手写跨维度对象替换；非玩家重建和玩家网络同步均由此原生入口负责。
既有源/目的包络先验证getChunkNow；同步调用复用LoadedChunkMutationScope，ServerChunkCache的具名getChunk入口只允许返回已加载对象。
各端Entity.dimension进入正式DEBUG投影和资源状态，外维度精确读取用各端实际ResourceLocation/Identifier及现有ServerLevel.getEntity。
玩家转移可关闭或重建客户端Screen；自动断言须先等待原生页面完成并通过真实Esc进入PauseScreen，再取稳定资源版本，不能放宽版本校验掩盖自然状态推进。

仅验证probe在固定副本准备往返区域；1.x用自有TicketType及addRegionTicket/removeRegionTicket，26.x用未设置FLAG_PERSIST的TicketType及addTicketWithRadius/removeTicketWithRadius。
票据由同一probe明确移除，不使用Vanilla FORCED或修改持久强加载数据；它不进入产品JAR，也不证明产品能够加载未知目的地。
对外字段及副作用见[转移合同](../reference/protocol-native_v0.md#debug-live-转移)。

## 原生相机帧与可重入输入

五端Minecraft.runTick的原生鼠标消费点由同一逻辑来源作用域委托，不移动宿主光标或签发键鼠回调许可。
1.20.1为MouseHandler.turnPlayer()，其余为handleAccumulatedMovement()；电影相机余量由各版本自身的SmoothDouble处理，强停止清空原生滤波器。
键与按钮held状态须在原生回调前登记；GUI回调可在同线程重入主循环并卸载世界，返回后不能再次登记已被清理的输入。
KeyEntry读取实际key/name/changeButton/resetButton，1.20.1的KeyBindsList位于screens.controls，其余位于screens.options.controls；不调用按钮业务回调。
