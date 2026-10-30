package com.github.jaccek

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class PhotoRepository(databaseFile: Path) {
    private val jdbcUrl: String

    init {
        Files.createDirectories(databaseFile.toAbsolutePath().parent)
        jdbcUrl = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode = WAL")
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS photos (
                        id TEXT PRIMARY KEY,
                        filename TEXT NOT NULL,
                        media_type TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        uploaded_at TEXT NOT NULL,
                        captured_at TEXT,
                        latitude REAL,
                        longitude REAL,
                        camera_make TEXT,
                        camera_model TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS tags (
                        name TEXT PRIMARY KEY
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS photo_tags (
                        photo_id TEXT NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
                        tag_name TEXT NOT NULL REFERENCES tags(name) ON DELETE CASCADE,
                        PRIMARY KEY (photo_id, tag_name)
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE INDEX IF NOT EXISTS photo_tags_by_tag ON photo_tags(tag_name)")
                statement.execute("CREATE INDEX IF NOT EXISTS photos_by_uploaded_at ON photos(uploaded_at)")
            }
        }
    }

    fun insertPhoto(photo: Photo) {
        connect().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO photos (
                        id, filename, media_type, size, uploaded_at, captured_at,
                        latitude, longitude, camera_make, camera_model
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, photo.id)
                    statement.setString(2, photo.filename)
                    statement.setString(3, photo.mediaType)
                    statement.setLong(4, photo.size)
                    statement.setString(5, photo.uploadedAt)
                    statement.setString(6, photo.capturedAt)
                    statement.setObject(7, photo.latitude)
                    statement.setObject(8, photo.longitude)
                    statement.setString(9, photo.cameraMake)
                    statement.setString(10, photo.cameraModel)
                    statement.executeUpdate()
                }
                setTags(connection, photo.id, photo.tags)
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
    }

    fun listPhotos(tag: String? = null): List<Photo> = connect().use { connection ->
        val sql = if (tag == null) {
            "SELECT * FROM photos ORDER BY uploaded_at DESC"
        } else {
            """
            SELECT photos.* FROM photos
            WHERE EXISTS (
                SELECT 1 FROM photo_tags
                WHERE photo_tags.photo_id = photos.id AND photo_tags.tag_name = ?
            )
            ORDER BY uploaded_at DESC
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { statement ->
            if (tag != null) statement.setString(1, tag)
            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) add(results.toPhoto(connection))
                }
            }
        }
    }

    fun getPhoto(id: String): Photo = connect().use { connection ->
        connection.prepareStatement("SELECT * FROM photos WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results ->
                if (!results.next()) throw PhotoNotFoundException()
                results.toPhoto(connection)
            }
        }
    }

    fun replaceTags(id: String, tags: List<String>): Photo = connect().use { connection ->
        connection.autoCommit = false
        try {
            connection.prepareStatement("SELECT id FROM photos WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { results ->
                    if (!results.next()) throw PhotoNotFoundException()
                }
            }
            setTags(connection, id, tags)
            connection.commit()
            getPhoto(id)
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

    fun deletePhoto(id: String): Photo = connect().use { connection ->
        connection.autoCommit = false
        try {
            val photo = connection.prepareStatement("SELECT * FROM photos WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { results ->
                    if (!results.next()) throw PhotoNotFoundException()
                    results.toPhoto(connection)
                }
            }
            connection.prepareStatement("DELETE FROM photos WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeUpdate()
            }
            connection.commit()
            photo
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

    private fun setTags(connection: Connection, photoId: String, tags: List<String>) {
        connection.prepareStatement("DELETE FROM photo_tags WHERE photo_id = ?").use { statement ->
            statement.setString(1, photoId)
            statement.executeUpdate()
        }
        for (tag in tags) {
            connection.prepareStatement("INSERT INTO tags(name) VALUES (?) ON CONFLICT(name) DO NOTHING").use { statement ->
                statement.setString(1, tag)
                statement.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO photo_tags(photo_id, tag_name) VALUES (?, ?)").use { statement ->
                statement.setString(1, photoId)
                statement.setString(2, tag)
                statement.executeUpdate()
            }
        }
    }

    private fun connect(): Connection = DriverManager.getConnection(jdbcUrl).also { connection ->
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        connection.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
    }

    private fun ResultSet.toPhoto(connection: Connection): Photo {
        val photoId = getString("id")
        val tags = connection.prepareStatement(
            "SELECT tag_name FROM photo_tags WHERE photo_id = ? ORDER BY tag_name",
        ).use { statement ->
            statement.setString(1, photoId)
            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) add(results.getString("tag_name"))
                }
            }
        }
        return Photo(
            id = photoId,
            filename = getString("filename"),
            mediaType = getString("media_type"),
            size = getLong("size"),
            uploadedAt = getString("uploaded_at"),
            capturedAt = getString("captured_at"),
            latitude = getObject("latitude")?.let { getDouble("latitude") },
            longitude = getObject("longitude")?.let { getDouble("longitude") },
            cameraMake = getString("camera_make"),
            cameraModel = getString("camera_model"),
            tags = tags,
        )
    }
}
