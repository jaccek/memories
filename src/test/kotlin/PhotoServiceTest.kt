package com.github.jaccek

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoServiceTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `upload tag retrieve and delete photo`() = testApplication {
        application { module(tempDirectory) }
        val originalBytes = "not really an image".toByteArray()
        val upload = client.post("/api/photos") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("tags", "Family, summer")
                        append(
                            "file",
                            originalBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.File.withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        "note.txt",
                                    ).toString(),
                                )
                            },
                        )
                    },
                ),
            )
        }

        assertEquals(201, upload.status.value)
        val photo = Json.decodeFromString<Photo>(upload.bodyAsText())
        assertEquals("note.txt", photo.filename)
        assertEquals(listOf("family", "summer"), photo.tags)

        val filtered = client.get("/api/photos?tag=FAMILY")
        val photos = Json.decodeFromString<List<Photo>>(filtered.bodyAsText())
        assertEquals(listOf(photo.id), photos.map(Photo::id))

        val downloaded = client.get("/api/photos/${photo.id}/file")
        assertContentEquals(originalBytes, downloaded.bodyAsBytes())

        val updated = client.put("/api/photos/${photo.id}/tags") {
            setBody(Json.encodeToString(UpdateTagsRequest(listOf("archive"))))
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        assertEquals(listOf("archive"), Json.decodeFromString<Photo>(updated.bodyAsText()).tags)

        assertEquals(204, client.delete("/api/photos/${photo.id}").status.value)
        assertEquals(404, client.get("/api/photos/${photo.id}").status.value)
    }

    @Test
    fun `gallery page is served at root`() = testApplication {
        application { module(tempDirectory) }
        val response = client.get("/")

        assertTrue(response.status.isSuccess())
        assertTrue(response.bodyAsText().contains("Your self-hosted photo library"))
    }
}
