package com.github.jaccek.infrastructure

import com.github.jaccek.application.InvalidRequestException
import com.github.jaccek.application.PhotoNotFoundException
import com.github.jaccek.application.PhotoService
import com.github.jaccek.application.UploadTooLargeException
import com.github.jaccek.domain.ApiError
import io.ktor.http.HttpStatusCode
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
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
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

    configureRouting(photoService)
}
