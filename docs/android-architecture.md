# Android architecture

## Product boundary

Android 客户端采用纯原生 Compose 工作台，不嵌入 WebView。所有用户操作通过同一组类型化领域模型进入 transport：远端 transport 连接 HTTPS Scriverse Server，本机 transport 连接 App 私有进程中绑定 `127.0.0.1` 的固定 Scriverse runtime。界面层不区分两套业务逻辑。

## Navigation and interaction

- 一级导航为书架、写作、设定、AI、我的；同步状态位于全局顶栏。
- 手机使用底部导航、单章全屏编辑与全屏表单；平板使用导航 rail、目录/编辑器 list-detail。
- 工作区通过顶栏 chip 切换。关键启动、兼容、离线、冲突与恢复状态使用持久页面，不以 toast 作为唯一反馈。
- 原生编辑器承接中文 IME、选区、撤销重做、查找替换和长文本。AI 使用原生流式消息、上下文、图片、工具与分析任务组件。

## Security boundary

- 远端只接受规范化 HTTPS origin；仅 loopback runtime 可使用 HTTP。禁止 URL 凭据、路径、跨 origin 跳转和证书错误绕过。
- Bearer 与本地 AI Key 使用 Android Keystore 中的 AES-GCM 密钥加密。密码不落盘，日志统一脱敏。
- 本机 runtime 在独立 Android process 中，通过私有 AIDL 控制。它只监听 loopback，并要求每次启动随机能力值。
- 业务权限、对象归属、管理员能力、版本与审计继续由 Scriverse Server 判定；客户端隐藏控件不是授权措施。
- 自动云备份和设备迁移全部关闭，手稿、数据库、附件和凭据不会进入系统备份。

## Data and sync

- SQLite 缓存的全部主键包含 `profileId + userId`，profile 删除依靠外键级联且受待同步/冲突/拒绝修改保护。
- 正文与 outbox 在同一事务写入。下载快照全部完成并校验后再单事务替换正式副本。
- 离线可编辑类型固定为 `chapter` 和 `setting`。同步先拉取后幂等推送，处理 `Retry-After`、指数退避、权限撤销和三方冲突。
- runtime 更新前备份业务库，更新后执行 `integrity_check` 与 `foreign_key_check`；失败进入只读恢复而不是清库。

## Runtime release gate

Node 22 Android 不是官方支持平台。发布必须从固定源码、补丁和 NDK 重建，不接受来源未知的二进制。`arm64-v8a` 与 `x86_64` 都必须通过 16 KB ELF、核心模块、SQLite、100 次生命周期和完整 Scriverse 作者旅程。任何失败都会把本机工作区标记为不可发布，不能切换到 WebView 或 Kotlin 复刻后端掩盖失败。
