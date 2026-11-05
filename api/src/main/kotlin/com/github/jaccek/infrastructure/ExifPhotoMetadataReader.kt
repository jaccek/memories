package com.github.jaccek.infrastructure

import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.ImageProcessingException
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.github.jaccek.application.PhotoMetadataReader
import com.github.jaccek.domain.PhotoMetadata
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.time.Instant

class ExifPhotoMetadataReader : PhotoMetadataReader {
    override fun read(file: Path): PhotoMetadata {
        try {
            val metadata = ImageMetadataReader.readMetadata(file.toFile())
            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            val geoLocation = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
                ?.geoLocation
            val captured = subIfd?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                ?: ifd0?.getDate(ExifIFD0Directory.TAG_DATETIME)

            return PhotoMetadata(
                capturedAt = captured?.toInstant()?.let(Instant::toString),
                latitude = geoLocation?.latitude,
                longitude = geoLocation?.longitude,
                cameraMake = ifd0?.getString(ExifIFD0Directory.TAG_MAKE),
                cameraModel = ifd0?.getString(ExifIFD0Directory.TAG_MODEL),
            )
        } catch (exception: ImageProcessingException) {
            logger.debug("No readable image metadata in {}: {}", file.fileName, exception.message)
        } catch (exception: IOException) {
            logger.warn("Could not read image metadata from {}", file.fileName, exception)
        }
        return PhotoMetadata()
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ExifPhotoMetadataReader::class.java)
    }
}
