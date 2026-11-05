package com.github.jaccek.infrastructure

import com.github.jaccek.application.PhotoService
import com.github.jaccek.domain.UpdateTagsRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting(photoService: PhotoService) {
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
                val request = call.receiveMultipart().toUploadPhotoRequest(photoService)
                val photo = photoService.savePhoto(request)
                call.response.header(HttpHeaders.Location, "/api/photos/${photo.id}")
                call.respond(HttpStatusCode.Created, photo)
            }

            get("/{id}") {
                call.respond(photoService.getPhoto(call.parameters["id"].orEmpty()))
            }

            get("/{id}/file") {
                PhotoFileResponse.respond(call, photoService.getPhotoFile(call.parameters["id"].orEmpty()))
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
