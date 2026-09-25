# Vision Watch App · Engineering Review

本文件用于项目作品集与维护交接，记录当前工程证据和低风险优化边界。它不替代项目 README，也不虚构未完成的指标。

## 已确认的工程证据

Android、Rokid 眼镜端/移动端说明、ASR/TTS 能力报告。

## 本轮优化方向

补 SDK 缺失时的 mock 构建路径、运行截图和接口契约。

## 验证入口

```text
见 README、docs/BUILD_PREREQUISITES.md 与 build_apk.cmd
```

`VisionApi`/`MockVisionApi` 使 SDK 缺失时仍可编译契约层；真实 Rokid/SparkChain AAR 和
凭据只在本地提供，不进入作品集快照。

## 作品集写法

- 先写个人贡献和可复现入口，再写技术栈。
- 私有仓库补充脱敏截图或架构图；Fork 仓库标明个人贡献边界。
- 不把 fixture、构建产物或上游代码当作独立项目。
