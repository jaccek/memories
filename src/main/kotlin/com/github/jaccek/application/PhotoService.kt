package com.github.jaccek.application

import com.github.jaccek.domain.Photo
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

private const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024

class PhotoService(
    private val photoDirectory: Path,
    private val repository: PhotoRepository,
    private val metadataReader: PhotoMetadataReader,
) {
    init {
        Files.createDirectories(photoDirectory)
    }

    fun stageUpload(input: InputStream): StagedPhotoUpload {
        val temporaryFile = Files.createTempFile(photoDirectory, "upload-", ".tmp")
        try {
            input.use { source ->
                Files.newOutputStream(temporaryFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) {
                            break
                        }
                        totalBytes += count
                        if (totalBytes > MAX_UPLOAD_BYTES) {
                            throw UploadTooLargeException()
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return StagedPhotoUpload(temporaryFile)
        } catch (exception: Exception) {
            Files.deleteIfExists(temporaryFile)
            throw exception
        }
    }

    fun savePhoto(request: UploadPhotoRequest): Photo {
        try {
            val normalizedTags = normalizeTags(request.tags)
            val id = UUID.randomUUID().toString()
            val storedFile = photoDirectory.resolve(id)
            try {
                val metadata = metadataReader.read(request.upload.path)
                Files.move(request.upload.path, storedFile, StandardCopyOption.ATOMIC_MOVE)
                val photo = Photo(
                    id = id,
                    filename = safeFilename(request.filename),
                    mediaType = request.mediaType ?: "application/octet-stream",
                    size = Files.size(storedFile),
                    uploadedAt = Instant.now().toString(),
                    capturedAt = metadata.capturedAt,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude,
                    cameraMake = metadata.cameraMake,
                    cameraModel = metadata.cameraModel,
                    tags = normalizedTags,
                )
                repository.insertPhoto(photo)
                return photo
            } catch (exception: Exception) {
                Files.deleteIfExists(storedFile)
                throw exception
            }
        } finally {
            request.upload.close()
        }
    }

    fun listPhotos(tag: String?): List<Photo> = repository.listPhotos(tag?.let(::normalizeTag))

    fun getPhoto(id: String): Photo = repository.getPhoto(requirePhotoId(id))

    fun getPhotoFile(id: String): StoredPhotoFile {
        val photo = getPhoto(id)
        return StoredPhotoFile(photo, photoDirectory.resolve(photo.id))
    }

    fun replaceTags(id: String, tags: List<String>): Photo =
        repository.replaceTags(requirePhotoId(id), normalizeTags(tags))

    fun deletePhoto(id: String) {
        val photo = repository.deletePhoto(requirePhotoId(id))
        Files.deleteIfExists(photoDirectory.resolve(photo.id))
    }

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

    private fun normalizeTag(value: String): String = normalizeTags(listOf(value)).firstOrNull().orEmpty()

    private fun normalizeTags(tags: List<String>): List<String> {
        if (tags.size > 30) {
            throw InvalidRequestException("A photo can have at most 30 tags")
        }
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
}

data class StoredPhotoFile(val photo: Photo, val path: Path)

class StagedPhotoUpload internal constructor(internal val path: Path) : AutoCloseable {
    override fun close() {
        Files.deleteIfExists(path)
    }
}

data class UploadPhotoRequest(
    val upload: StagedPhotoUpload,
    val filename: String?,
    val mediaType: String?,
    val tags: List<String>,
)

class InvalidRequestException(message: String) : RuntimeException(message)
class PhotoNotFoundException : RuntimeException()
class UploadTooLargeException : RuntimeException()
