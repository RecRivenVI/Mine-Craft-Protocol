# Minecraft 26.3 移植

26.3 NeoForge/Fabric 使用 SDL 输入与窗口后端及 Renderpearl GPU 接口。
本次只迁移既有行为，不调整控制模式、视觉设计、权限或证据分级。

## 输入与窗口

UI 原生容器在五端分别提取。1.20.1/1.21.1 用具名 Invoker 读取受保护列表行边界，
Accessor 读取世界行 summary，字符回调仍是 GLFW 参数；26.1.2/26.3 使用 CharacterEvent。
26.3 NeoForge 的字符入口公开，其他端经具名 Invoker，不用 Reflection。
Forge/NeoForge 创造标签只取当前 Loader 页内集合，Fabric 取原版可见 tabs；
起点使用各端原生 getter/Accessor。旧端 RecipeButton 尚无新版 display stack 接口，
不虚构物品标签；其节点与真实鼠标输入保留。

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

最终内容 Hook 改为 Renderpearl GpuSurface.blitFromTexture 之前：
游戏内容、证据 readback、Operator Chrome/Pointer、present 的顺序保持不变。
静态 Hook 数量和 Runtime manifest 按真实 Target 更新，不沿用旧 API 的数量。
圆球以本地 (0,0) 为对称中心，通过原生 pose 栈的小数平移定位。1.20.1/1.21.1 的
Operator pass 使用整数 GUI 宽高投影；26.1.2/26.3 的原生 GuiRenderer 使用 framebuffer
宽高除以 GUI scale 的浮点投影，而输入 GUI 宽高向上取整。只在绘制原点转换两者，
避免 2560×1440 / scale 6 等非整除尺寸的偏移，不改输入落点或其他 Chrome 几何。
真实人工 Esc、焦点、宿主光标和视觉质量仍需用户单独验收，自动 smoke 不替代这些结论。

## 原版 UI 原生区域

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
