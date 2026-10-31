package com.github.jaccek.domain

import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val id: String,
    val filename: String,
    val mediaType: String,
    val size: Long,
    val uploadedAt: String,
    val capturedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val tags: List<String> = emptyList(),
)

data class PhotoMetadata(
    val capturedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
)

@Serializable
data class UpdateTagsRequest(val tags: List<String>)

@Serializable
data class ApiError(val error: String)
