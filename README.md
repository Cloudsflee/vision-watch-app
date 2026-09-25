# VisionWatchApp｜面向可穿戴终端的多模态交互链路

VisionWatchApp 将“所见画面、自然语言提问和语音反馈”串成一条可观测的端侧交互链路：
用户通过 Rokid/Android 终端采集视觉输入，以语音完成问题表达，经视觉模型推理后由
TTS 返回可听见的结果。项目关注点是设备能力与 AI 服务之间的工程衔接，而非单一页面
展示。

仓库保留 CameraX 采集、SparkChain ASR/TTS、OpenAI-compatible Vision API、AudioTrack
播放和 Debug 诊断实现。私有 SDK 二进制与真实凭据由使用者在本机提供，公开快照保留
可审查的接口边界和离线 Mock 路径。

## 项目亮点

- **完整多模态闭环**：从图像采集、语音识别、视觉推理到语音播报，覆盖设备侧关键状态。
- **可观测交互状态机**：以 pipeline stage、request id、HTTP 状态和耗时串联一次请求，
  让真机问题可以沿链路定位。
- **服务适配边界清晰**：`VisionApi` 抽象请求/响应，真实 HTTP 适配器与 `MockVisionApi`
  共用契约，便于在 SDK 或后端不可用时继续验证 UI。
- **面向穿戴设备的交互约束**：保留按键导航、权限处理、音频播放释放和超时 watchdog，
  适配小屏、语音优先和不稳定网络环境。

## 能力链路

```text
CameraX capture
      │ JPEG/Base64
      ▼
voice input ──► SparkChain ASR ──► Vision API (OpenAI-compatible)
                                      │ text response
                                      ▼
                               SparkChain TTS ──► AudioTrack
```

页面状态按 `idle → image_captured → asr_recording → asr_ready_for_submit →
model_success → tts_done` 推进；异常会保留 pipeline stage、request id、HTTP 状态、
耗时和响应摘要，便于在真机或眼镜端定位问题。调试诊断只在 Debug 包显示。

## 目录

| 路径 | 内容 |
|---|---|
| `app/src/main/java/com/example/visionwatchapp/MainActivity.kt` | 摄像头、语音、模型请求、TTS 和交互状态 |
| `app/src/main/java/com/example/visionwatchapp/ApiConfig.kt` | 本地服务地址、模型和 SDK 凭据占位符 |
| `app/src/main/java/com/example/visionwatchapp/VisionApi.kt` | 视觉请求/响应接口契约 |
| `app/src/main/java/com/example/visionwatchapp/MockVisionApi.kt` | 不联网的确定性 Mock 实现 |
| `app/src/main/res/layout/activity_main.xml` | 手机/眼镜共用的基础交互布局 |
| `rokidsdk移动端.md` | 移动端 SDK 接入记录 |
| `rokidsdk眼镜端.md` | Rokid 眼镜端能力与适配记录 |
| `MODEL_ASR_TTS_CAPABILITY_REPORT.md` | ASR/TTS 能力和限制核验 |
| `docs/BUILD_PREREQUISITES.md` | SDK 缺失时的构建前置、Mock 和排障入口 |

## 本地配置

公开快照中的 `ApiConfig.kt` 只包含占位符：

```kotlin
const val VISION_API_KEY = "YOUR_VISION_API_KEY"
const val VISION_API_URL = "https://api.example.com/v1"
const val VISION_MODEL = "ChronoStyleNet"
```

请在本地替换服务地址和凭据；不要把真实 key、私有 AAR、设备日志或 APK 提交到 Git。
模型接口使用 `/chat/completions` 兼容格式，响应解析同时兼容字符串、数组和直接
`output_text` 字段。

## 构建前置

- Android SDK 34
- JDK 17
- Gradle Wrapper 可用
- 公开依赖可从网络下载，或已缓存后使用 `--offline`
- 真实链路需要由 SDK 提供方单独放入 `app/libs/*.aar`

构建命令：

```powershell
.\gradlew.bat assembleDebug
```

也可以使用 `build_apk.cmd`，它会检查 `app/build/outputs/apk/debug/app-debug.apk` 是否
生成。详细前置检查见 [`docs/BUILD_PREREQUISITES.md`](docs/BUILD_PREREQUISITES.md)。

## SDK 缺失时的 Mock 路径

`VisionApi`、`VisionRequest` 和 `VisionResponse` 是真实 HTTP 适配器与离线实现的共同
契约。`MockVisionApi` 返回固定的 `mock-local` request id，不访问摄像头、ASR、TTS 或
网络，适合先验证 UI 状态、请求展示和结果渲染：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

接入真实服务时，只需把 Mock 实现替换为 HTTP 适配器，保持请求/响应字段不变。

## 真机与眼镜联调

1. 在 `ApiConfig.kt` 设置本地或测试环境的视觉服务地址。
2. 安装 Debug APK，授予 camera、record audio 和存储权限。
3. 点击拍摄，确认 `image_captured` 状态。
4. 长按语音按钮提问，确认 ASR 最终文本后提交。
5. 查看模型回答并确认 TTS 播放；失败时记录 Debug 诊断面板中的 stage 和 request id。

眼镜端需要额外确认 Rokid SDK 版本、设备权限和音频输出路由；相关差异记录在两份
`rokidsdk*.md` 文档中。没有私有 SDK 或设备时，以 Mock 和 Kotlin 编译作为可复现验收
边界。

## 已知边界

- 视觉服务、ASR 和 TTS 都依赖外部凭据或设备能力，离线环境只能验证契约与 UI。
- `localStorage`、相机图像和语音内容不进入仓库；测试日志需在分享前脱敏。
- 这是能力验证原型，尚未承诺生产级鉴权、服务端限流、离线模型或长期会话存储。

## 作品集验证

工程化审查见 [`ENGINEERING_REVIEW.md`](ENGINEERING_REVIEW.md)。它记录 SDK 缺失时的
Mock 构建路径、运行截图待补项和可复现验证边界。
