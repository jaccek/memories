package com.github.jaccek.infrastructure

import com.github.jaccek.application.InvalidRequestException
import com.github.jaccek.application.PhotoNotFoundException
import com.github.jaccek.application.PhotoService
import com.github.jaccek.application.StagedPhotoUpload
import com.github.jaccek.application.UploadTooLargeException
import com.github.jaccek.domain.ApiError
import com.github.jaccek.domain.Photo
import com.github.jaccek.domain.UpdateTagsRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        module()
    }.start(wait = true)
}

fun Application.module(
    dataDirectory: Path = Path.of(System.getenv("MEMORIES_DATA_DIR") ?: "./data"),
) {
    val photoService = PhotoService(
        photoDirectory = dataDirectory.resolve("photos"),
        repository = SqlitePhotoRepository(dataDirectory.resolve("memories.db")),
        metadataReader = ExifPhotoMetadataReader(),
    )

    install(CallLogging)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
    install(StatusPages) {
        exception<PhotoNotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ApiError("Photo not found"))
        }
        exception<InvalidRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError(cause.message ?: "Invalid request"))
        }
        exception<UploadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError("Uploads are limited to 50 MB"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
        }
    }

    routing {
        staticResources("/", "static")

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api/photos") {
            get {
                val tag = call.request.queryParameters["tag"]
                call.respond(photoService.listPhotos(tag))
            }

            post {
                var filename: String? = null
                var mediaType: String? = null
                var tags = emptyList<String>()
                var upload: StagedPhotoUpload? = null
                try {
                    call.receiveMultipart().forEachPart { part ->
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
                    val stagedUpload = upload ?: throw InvalidRequestException("A file is required")
                    val savedPhoto = photoService.savePhoto(stagedUpload, filename, mediaType, tags)
                    upload = null
                    call.response.header(HttpHeaders.Location, "/api/photos/${savedPhoto.id}")
                    call.respond(HttpStatusCode.Created, savedPhoto)
                } finally {
                    upload?.close()
                }
            }

            get("/{id}") {
                call.respond(photoService.getPhoto(call.parameters["id"].orEmpty()))
            }

            get("/{id}/file") {
                val (photo, file) = photoService.getPhotoFile(call.parameters["id"].orEmpty())
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
                    Files.newInputStream(file).use { it.copyTo(this) }
                }
            }

            put("/{id}/tags") {
                val request = call.receive<UpdateTagsRequest>()
                call.respond(photoService.replaceTags(call.parameters["id"].orEmpty(), request.tags))
            }

            delete("/{id}") {
                photoService.deletePhoto(call.parameters["id"].orEmpty())
                call.respond(HttpStatusCode.NoContent)
            }
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
