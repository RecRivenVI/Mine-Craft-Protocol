# CC: Tweaked 适配

仅观察客户端当前打开的电脑终端：复用原生 TerminalWidget 身份与几何，补充显示文本、焦点和输入语义。
不直接调用电脑服务或执行 Lua。点击、拖拽、滚动和字符继续由正式 UI/input 接口产生。
终端不是可整体替换的文本框；type_text 必须使用 replace=false，提交使用正常 Enter。
五个 Target 的包声明不等于五端第三方业务已经实测；以平台验证结果为准。
