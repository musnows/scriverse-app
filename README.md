# Scriverse App

Scriverse 的原生移动端仓库。Android 工程位于 `android/`，当前最低支持 Android 10（API 29），目标版本为 Android 16（API 36），使用 API 37 编译。

## 本地构建

```bash
cd android
./gradlew lint test assembleDebug
```

本地 Node runtime 的可行性构建与硬门禁见 `android/runtime-node/README.md`。
