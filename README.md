# Memories

Memories is a self-hosted photo and file library. The Ktor service stores original files on disk and keeps searchable tags and extracted image metadata in SQLite. It includes a small browser gallery and a JSON API intended to be reusable by a future mobile app.

Authentication is intentionally not included yet. Do not expose this service to an untrusted network until authentication and authorization have been added.

## Code structure

The Kotlin source is organized into three packages:

- `domain` contains photo metadata and API data structures.
- `application` contains photo use cases and the repository/metadata-reader interfaces those use cases depend on.
- `infrastructure` contains Ktor routing and the SQLite/EXIF adapters that implement those interfaces.

## Run locally

Requires JDK 21.

```sh
./gradlew run
```

Open <http://localhost:8080>. The default data directory is `./data`; set `MEMORIES_DATA_DIR` to use another directory and `PORT` to change the listening port. The service binds to `0.0.0.0` for self-hosting.

## API

- `POST /api/photos` — upload one multipart `file` and optional comma-separated `tags`; returns the saved photo as JSON.
- `GET /api/photos` — list photos, newest first. Add `?tag=travel` to filter by tag.
- `GET /api/photos/{id}` — get one photo and its metadata.
- `GET /api/photos/{id}/file` — retrieve the stored file.
- `PUT /api/photos/{id}/tags` — replace tags with JSON such as `{"tags":["travel","summer"]}`.
- `DELETE /api/photos/{id}` — remove a photo and its stored file.
- `GET /health` — basic health check.

Uploads are limited to 50 MB. Tags are case-insensitive, stored in lowercase, and limited to 30 tags of 50 characters each. EXIF metadata is extracted when available: capture date, GPS coordinates, camera make, and camera model. Files without readable image metadata are still stored.

## Data and deployment

The configured data directory contains `memories.db` and the `photos/` directory. Back up both while the service is stopped, or use a consistent SQLite-aware backup process.

For a container build:

```sh
docker build -t memories .
docker run --name memories -p 8080:8080 -v memories-data:/data \
  -e MEMORIES_DATA_DIR=/data memories
```
