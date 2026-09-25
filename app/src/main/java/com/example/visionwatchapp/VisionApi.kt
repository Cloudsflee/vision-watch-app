package com.example.visionwatchapp

/** Small contract shared by the real HTTP client and the offline demo client. */
data class VisionRequest(val imageBase64: String, val prompt: String)

data class VisionResponse(val text: String, val requestId: String, val source: String)

interface VisionApi {
    suspend fun analyze(request: VisionRequest): VisionResponse
}
