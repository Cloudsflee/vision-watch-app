# 构建前置与 Mock 路径

## SDK 可用时

`app/libs/` 需要放入项目使用的 Rokid/SparkChain `.aar`。这些私有二进制不进入公开
快照；`ApiConfig.kt` 只保留占位符。准备 Android SDK 34、JDK 17 和网络依赖后运行：

```powershell
.\gradlew.bat --offline assembleDebug
```

若依赖尚未缓存，去掉 `--offline` 让 Gradle 下载公开依赖。`build_apk.cmd` 会检查最终
APK 是否生成，但不会把 APK 提交到 Git。

## SDK 缺失时

可先编译纯 UI/契约层，并使用 `MockVisionApi` 验证页面状态：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

`VisionApi`、`VisionRequest` 和 `VisionResponse` 是真实 HTTP 适配器与 Mock 的共同契约。
Mock 不访问摄像头、ASR、TTS 或网络，只用于开发演示；接入真实 SDK 时替换实现即可。

## 运行前检查

- `local.properties` 指向 Android SDK
- JDK `17` 可执行
- `app/libs/*.aar` 已由 SDK 提供方单独放置（可选，缺失时走 Mock）
- `ApiConfig.kt` 中的地址和密钥仍为本地占位符
