# Minecraft 26.3 移植

26.3 NeoForge/Fabric 使用 SDL 输入与窗口后端及 Renderpearl GPU 接口。
本次只迁移既有行为，不调整控制模式、视觉设计、权限或证据分级。

## 输入与窗口

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

## 依赖与旧 Target

三条固定 Minecraft 线只更新兼容 Loader，不迁移 SDL；它们保留各自的 GLFW Hook。
Minecraft 自带 Netty、Mixin 与 LWJGL 由游戏/Loader 管理；补充 HTTP 模块与所属游戏版本对齐。
独立测试依赖版本由版本目录负责，不能用测试版本声称 Minecraft 内置库已经升级。
新 Target 的最低 Loader 要求从其自身事实生成，不沿用旧 Minecraft 的最低要求。
旧实例目录非破坏迁移；不要用新版本自动打开重要旧世界。
