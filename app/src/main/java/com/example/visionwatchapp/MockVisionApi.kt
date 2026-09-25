package com.example.visionwatchapp

/** Deterministic response for emulator/UI work when private SDKs or backend are absent. */
class MockVisionApi : VisionApi {
    override suspend fun analyze(request: VisionRequest): VisionResponse = VisionResponse(
        text = "Mock result: image received (${request.imageBase64.length} chars). Prompt: ${request.prompt}",
        requestId = "mock-local",
        source = "mock"
    )
}
