package com.github.jaccek

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticResources
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

private const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        module()
    }.start(wait = true)
}

fun Application.module(
    dataDirectory: Path = Path.of(System.getenv("MEMORIES_DATA_DIR") ?: "./data"),
) {
    val photoDirectory = dataDirectory.resolve("photos")
    Files.createDirectories(photoDirectory)
    val repository = PhotoRepository(dataDirectory.resolve("memories.db"))

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
                val tag = call.request.queryParameters["tag"]?.let(::normalizeTag)
                call.respond(repository.listPhotos(tag))
            }

            post {
                val multipart = call.receiveMultipart()
                var uploadedFile: Path? = null
                var originalName: String? = null
                var mediaType: String? = null
                var tags = emptyList<String>()
                try {
                    multipart.forEachPart { part ->
                        try {
                            when (part) {
                                is io.ktor.http.content.PartData.FormItem -> {
                                    if (part.name == "tags") tags = parseTags(part.value)
                                }
                                is io.ktor.http.content.PartData.FileItem -> {
                                    if (uploadedFile != null) {
                                        throw InvalidRequestException("Upload exactly one file")
                                    }
                                    originalName = safeFilename(part.originalFileName)
                                    mediaType = part.contentType?.toString() ?: ContentType.Application.OctetStream.toString()
                                    val temporaryFile = Files.createTempFile(photoDirectory, "upload-", ".tmp")
                                    uploadedFile = temporaryFile
                                    part.provider().toInputStream().use { input ->
                                        Files.newOutputStream(temporaryFile).use { output ->
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            var totalBytes = 0L
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                totalBytes += count
                                                if (totalBytes > MAX_UPLOAD_BYTES) throw UploadTooLargeException()
                                                output.write(buffer, 0, count)
                                            }
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        } finally {
                            part.dispose()
                        }
                    }
                } catch (exception: Exception) {
                    uploadedFile?.let(Files::deleteIfExists)
                    throw exception
                }

                val temporaryFile = uploadedFile ?: throw InvalidRequestException("A file is required")
                val name = originalName ?: "untitled"
                val id = UUID.randomUUID().toString()
                val storedFile = photoDirectory.resolve(id)
                try {
                    val metadata = PhotoMetadata.read(temporaryFile)
                    Files.move(temporaryFile, storedFile, StandardCopyOption.ATOMIC_MOVE)
                    val photo = Photo(
                        id = id,
                        filename = name,
                        mediaType = mediaType ?: ContentType.Application.OctetStream.toString(),
                        size = Files.size(storedFile),
                        uploadedAt = Instant.now().toString(),
                        capturedAt = metadata.capturedAt,
                        latitude = metadata.latitude,
                        longitude = metadata.longitude,
                        cameraMake = metadata.cameraMake,
                        cameraModel = metadata.cameraModel,
                        tags = tags,
                    )
                    repository.insertPhoto(photo)
                    call.response.header(HttpHeaders.Location, "/api/photos/$id")
                    call.respond(HttpStatusCode.Created, photo)
                } catch (exception: Exception) {
                    Files.deleteIfExists(storedFile)
                    Files.deleteIfExists(temporaryFile)
                    throw exception
                }
            }

            get("/{id}") {
                call.respond(repository.getPhoto(requirePhotoId(call.parameters["id"])))
            }

            get("/{id}/file") {
                val photo = repository.getPhoto(requirePhotoId(call.parameters["id"]))
                val file = photoDirectory.resolve(photo.id)
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
                val id = requirePhotoId(call.parameters["id"])
                val request = call.receive<UpdateTagsRequest>()
                call.respond(repository.replaceTags(id, normalizeTags(request.tags)))
            }

            delete("/{id}") {
                val photo = repository.deletePhoto(requirePhotoId(call.parameters["id"]))
                Files.deleteIfExists(photoDirectory.resolve(photo.id))
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

private fun requirePhotoId(id: String?): String {
    val uuid = id?.let {
        try {
            UUID.fromString(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    } ?: throw InvalidRequestException("Photo id must be a UUID")
    return uuid.toString()
}

private fun safeFilename(filename: String?): String =
    filename.orEmpty()
        .replace('\\', '/')
        .substringAfterLast('/')
        .filterNot(Char::isISOControl)
        .take(255)
        .ifBlank { "untitled" }

private fun parseTags(value: String): List<String> = normalizeTags(value.split(','))

private fun normalizeTag(value: String): String = normalizeTags(listOf(value)).firstOrNull().orEmpty()

private fun normalizeTags(tags: List<String>): List<String> {
    if (tags.size > 30) throw InvalidRequestException("A photo can have at most 30 tags")
    return tags.map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .distinct()
        .also { normalized ->
            if (normalized.any { it.length > 50 }) {
                throw InvalidRequestException("Tags must be 50 characters or fewer")
            }
        }
}

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

@Serializable
data class UpdateTagsRequest(val tags: List<String>)

@Serializable
data class ApiError(val error: String)

class InvalidRequestException(message: String) : RuntimeException(message)
class PhotoNotFoundException : RuntimeException()
class UploadTooLargeException : RuntimeException()
