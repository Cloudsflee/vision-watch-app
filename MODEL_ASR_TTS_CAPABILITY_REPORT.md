# 模型/语音能力测试报告

- 测试时间：2026-03-08 21:40:40 +08:00
- 测试环境：`d:\04_projects\VisionWatchApp`（Windows PowerShell）
- 目标网关：`http://HOST/v1`
- 鉴权：`Bearer YOUR_VISION_API_KEY`

## 1. 测试目标

- 复核图文接口是否可用。
- 验证网关中“其他模型”文本/图文可用性。
- 验证语音转文字（STT）与文字转语音（TTS）接口是否在当前网关可用。

## 2. 测试方法

### 2.1 模型能力（Chat Completions）

- 先调用 `GET /v1/models` 获取模型列表。
- 对每个模型发两类请求：
  - 文本：`messages=[{type=text}]`
  - 图文：`messages=[{type=text},{type=image_url}]`（`data:image/...;base64,...`）
- 记录 HTTP 状态码、耗时、返回摘要。

### 2.2 语音能力（OpenAI 兼容音频端点探测）

- TTS 探测：`POST /v1/audio/speech`（ChronoStyleNet / Deepseek-V3）
- STT 探测：`POST /v1/audio/transcriptions`（上传 1 秒静音 wav）
- 记录状态码和返回体。

### 2.3 讯飞 WebSocket 可达性（网络层）

- `tts-api.xfyun.cn:443` 连接探测。
- `iat-api.xfyun.cn:443` 连接探测。

## 3. 关键结果

### 3.1 模型列表（/v1/models）

- 返回 HTTP 200，包含 12 个唯一模型ID。
- 典型模型：`ChronoStyleNet`、`Deepseek-V3`、`Qwen2_5_7B`、`Qwen2.5-VL-72B-Instruct` 等。

### 3.2 文本/图文批量测试结果


| 模型                                | 文本状态     | 文本耗时(ms) | 文本摘要      | 图文状态     | 图文耗时(ms) | 图文摘要    |
| --------------------------------- | -------- | -------- | --------- | -------- | -------- | ------- |
| ChronoStyleNet                    | HTTP 200 | 266      | OK.Trim() | HTTP 500 | 20       | .Trim() |
| Deepseek-V3                       | HTTP 500 | 55       | .Trim()   | HTTP 500 | 50       | .Trim() |
| Qwen2_5_7B                        | HTTP 500 | 57       | .Trim()   | HTTP 500 | 49       | .Trim() |
| qwen2_vl                          | HTTP 500 | 49       | .Trim()   | HTTP 500 | 49       | .Trim() |
| Qwen3-235B-A22B-Instruct-2507-FP8 | HTTP 500 | 49       | .Trim()   | HTTP 500 | 49       | .Trim() |
| Deepseek-R1-Qwen7B                | HTTP 500 | 49       | .Trim()   | HTTP 500 | 50       | .Trim() |
| Deepseek-1.5B                     | HTTP 500 | 52       | .Trim()   | HTTP 500 | 51       | .Trim() |
| Qwen2.5-VL-72B-Instruct           | HTTP 500 | 51       | .Trim()   | HTTP 500 | 51       | .Trim() |
| Qwen2.5-VL-32B-Instruct           | HTTP 500 | 153      | .Trim()   | HTTP 500 | 55       | .Trim() |
| CodeLlama-70b-Instruct-hf         | HTTP 404 | 54       | .Trim()   | HTTP 404 | 55       | .Trim() |
| Qwen2.5-72B-Instruct              | HTTP 500 | 51       | .Trim()   | HTTP 500 | 51       | .Trim() |
| Qwen2.5-32B-Instruct              | HTTP 500 | 52       | .Trim()   | HTTP 500 | 49       | .Trim() |


观察：

- 仅 `ChronoStyleNet` 文本请求稳定返回 `HTTP 200`。
- `ChronoStyleNet` 图文请求在本次主机侧测试中为 `HTTP 500`（响应体为空）。
- 其他模型大多 `HTTP 500`，`CodeLlama-70b-Instruct-hf` 为 `HTTP 404`。

### 3.3 语音端点（STT/TTS）探测结果


| 能力  | 请求                              | 模型             | 结果       | 备注                       |
| --- | ------------------------------- | -------------- | -------- | ------------------------ |
| TTS | `POST /v1/audio/speech`         | ChronoStyleNet | HTTP 404 | 端点不存在/未挂载                |
| TTS | `POST /v1/audio/speech`         | Deepseek-V3    | HTTP 404 | 端点不存在/未挂载                |
| STT | `POST /v1/audio/transcriptions` | ChronoStyleNet | HTTP 404 | 返回 `404: Page Not Found` |


### 3.4 讯飞服务网络可达性（仅网络层）

- `tts-api.xfyun.cn:443 reachable=True`
- `iat-api.xfyun.cn:443 reachable=True`

## 4. 结论

1. 当前 `HOST` 网关不是通用“多模型全可用”状态：除 `ChronoStyleNet` 文本外，其他模型基本不可用（500/404）。
2. 当前网关未提供 OpenAI 兼容 STT/TTS 端点（`/audio/speech`、`/audio/transcriptions` 均 404）。
3. 你反馈“眼镜端图文可用”与本次主机侧图文 500 存在差异，说明很可能是：
  - 眼镜端请求体与主机测试请求体不一致（字段/编码/图片格式）；
  - 或网关对来源/参数有分流策略。
4. 讯飞语音云主机可达（443可连），但是否鉴权成功/业务可用需走完整 WebSocket 鉴权流程才能下结论。

## 5. 建议的下一步（可执行）

1. 从眼镜抓一份真实成功请求（URL/headers/body），与主机请求逐字段对比。
2. 服务端为图文 500 增加错误体与 request-id 回传（目前为空，不利排障）。
3. 如果要在该网关测试“其他模型”，先由后端确认模型路由和上线状态，再做二次批测。
4. 语音能力建议直接走讯飞官方 WebSocket（ASR/TTS）链路，不依赖该网关的 `/audio/*`。

## 6. 附件文件

- `model_capability_test_results.json`：模型批测原始结果。
- `speech_capability_test_results.json`：如果后续补测脚本运行成功可更新该文件（本次主要依据端点探测结果）。


