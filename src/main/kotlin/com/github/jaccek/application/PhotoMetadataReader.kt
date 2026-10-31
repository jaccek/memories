package com.github.jaccek.application

import com.github.jaccek.domain.PhotoMetadata
import java.nio.file.Path

fun interface PhotoMetadataReader {
    fun read(file: Path): PhotoMetadata
}
