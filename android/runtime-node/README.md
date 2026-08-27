# Android Node runtime hard gate

本目录固定 Node.js `v22.23.2`、Android NDK `28.2.13676358` 与 API 29。Node 官方将 Android 标记为不受支持且没有 CI，因此这里的产物必须通过两种 ABI、16 KB ELF 对齐、真实设备生命周期、SQLite 和 Scriverse 系统旅程门禁，不能把“能够编译”视为可发布。

## 构建

```bash
./build-node.sh arm64-v8a
./build-node.sh x86_64
./verify-elf.sh prebuilt/arm64-v8a/libnode.so
```

脚本只把下载和中间产物放入 `runtime-node/work/`，最终本机产物放入被 Git 忽略的 `prebuilt/<abi>/libnode.so`。Release CI 必须从源码重建并校验哈希，不接受开发机上传的未知二进制。

## 阶段 0 通过条件

- 两种 ABI 均能启动固定 Node runtime，且所有 ELF LOAD segment 支持 16 KB 对齐。
- `node:sqlite` 外键、WAL、事务、崩溃恢复和完整性检查通过。
- crypto/scrypt/AES-GCM、AsyncLocalStorage、HTTP/SSE、zlib、DNS/SSRF、文件锁和 2 MB 以上请求通过。
- Scriverse 隔离数据目录完成 100 次冷启动/停止、空库初始化、迁移、health、登录、作品/正文、AI mock 流、图片上传；系统杀进程后数据库无损坏。
- 图片处理器覆盖 JPEG/PNG/WebP/GIF/AVIF 元数据、安全校验、裁剪、缩放与 DOCX/EPUB 输出。
