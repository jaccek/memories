package com.github.jaccek.infrastructure

import com.github.jaccek.application.StoredPhotoFile
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import java.nio.file.Files

object PhotoFileResponse {
    suspend fun respond(call: ApplicationCall, storedFile: StoredPhotoFile) {
        val photo = storedFile.photo
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header(
            HttpHeaders.ContentDisposition,
            if (photo.mediaType.lowercase() in INLINE_IMAGE_TYPES) {
                ContentDisposition.Inline.toString()
            } else {
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    photo.filename.replace("\"", ""),
                ).toString()
            },
        )
        call.respondOutputStream(
            contentType = ContentType.parse(photo.mediaType),
            contentLength = photo.size,
        ) {
            Files.newInputStream(storedFile.path).use { it.copyTo(this) }
        }
    }
}

private val INLINE_IMAGE_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/avif",
    "image/bmp",
)
