# Companion 依赖

Node 版本要求、直接依赖版本及开发/生产分类以
[package.json](../../components/companion/package.json) 为权威；完整解析版本与完整性哈希由
[package-lock.json](../../components/companion/package-lock.json) 固定。使用 `npm ci --include=dev` 安装，
不手工改写锁文件，也不使用 `npm audit fix --force` 替代受控升级。

## MCP 客户端与服务端

`@modelcontextprotocol/client` 是开发/测试依赖，用于 Companion 单元测试和 Live conformance。
现有客户端通过 `StdioClientTransport` 与 Companion 通信，不使用 HTTP OAuth。
生产入口使用 `@modelcontextprotocol/server`；客户端与服务端分别声明版本，升级后须验证实际 stdio 互通。
当前两者固定为 2.3.1，Zod 固定为 4.6.5；Node 类型使用 24 LTS 代际，运行环境优先 Node 24 LTS。
这些 Node 依赖属于独立工具组件，不嵌入 Minecraft Mod JAR。
客户端及其新版 Core 的许可为 Apache-2.0，原文由 npm 软件包提供；不改变项目自身的许可。

客户端必须使用包含
[GHSA-6qxp-vccf-f47h 修复](https://github.com/advisories/GHSA-6qxp-vccf-f47h)的版本；
该公告的最低修复版本为 `2.2.0`。将来若引入 HTTP OAuth，仍须检查授权服务器身份与凭据绑定，
不能仅凭升级依赖推定任意 OAuth 配置安全。

## 验证与审计

```powershell
npm --prefix components/companion ci --include=dev
npm --prefix components/companion test
npm --prefix components/companion audit
npm --prefix components/companion audit --omit=dev
```

全量审计包含测试依赖；`--omit=dev` 只检查生产依赖，不能替代全量结果。
测试应验证客户端/服务端互通、静态 Tools/Resources、人工撤销结构字段与流式读取预算。
审计服务不可用不等于零漏洞；升级依赖不改变 Native 协议、Control Mode、权限或 Evidence 合同。
