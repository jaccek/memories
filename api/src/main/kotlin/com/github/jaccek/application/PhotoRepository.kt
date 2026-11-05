package com.github.jaccek.application

import com.github.jaccek.domain.Photo

interface PhotoRepository {
    fun insertPhoto(photo: Photo)
    fun listPhotos(tag: String? = null): List<Photo>
    fun getPhoto(id: String): Photo
    fun replaceTags(id: String, tags: List<String>): Photo
    fun deletePhoto(id: String): Photo
}
