package com.github.jaccek.infrastructure

import com.github.jaccek.application.InvalidRequestException
import com.github.jaccek.application.PhotoService
import com.github.jaccek.application.StagedPhotoUpload
import com.github.jaccek.application.UploadPhotoRequest
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.jvm.javaio.toInputStream

suspend fun MultiPartData.toUploadPhotoRequest(photoService: PhotoService): UploadPhotoRequest {
    var filename: String? = null
    var mediaType: String? = null
    var tags = emptyList<String>()
    var upload: StagedPhotoUpload? = null

    try {
        forEachPart { part ->
            try {
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "tags") {
                            tags = part.value.split(',')
                        }
                    }
                    is PartData.FileItem -> {
                        if (upload != null) {
                            throw InvalidRequestException("Upload exactly one file")
                        }
                        filename = part.originalFileName
                        mediaType = part.contentType?.toString()
                        upload = photoService.stageUpload(part.provider().toInputStream())
                    }
                    else -> Unit
                }
            } finally {
                part.dispose()
            }
        }

        return UploadPhotoRequest(
            upload = upload ?: throw InvalidRequestException("A file is required"),
            filename = filename,
            mediaType = mediaType,
            tags = tags,
        )
    } catch (exception: Exception) {
        upload?.close()
        throw exception
    }
}
