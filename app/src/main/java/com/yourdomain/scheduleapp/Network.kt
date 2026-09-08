package com.yourdomain.scheduleapp

import retrofit2.http.GET
import retrofit2.http.Query

data class VkEnvelope(val response: VkResponse? = null, val error: VkError? = null)
data class VkError(val error_msg: String = "Ошибка VK")
data class VkResponse(val items: List<VkComment> = emptyList())
data class VkComment(val text: String = "", val date: Long = 0, val attachments: List<VkAttachment> = emptyList())
data class VkAttachment(val type: String = "", val photo: VkPhoto? = null)
data class VkPhoto(val sizes: List<VkPhotoSize> = emptyList())
data class VkPhotoSize(val url: String = "", val type: String = "", val width: Int = 0, val height: Int = 0)

interface VkApiService {
    @GET("board.getComments") suspend fun comments(
        @Query("group_id") group: Long = 191933238,
        @Query("topic_id") topic: Long = 53045814,
        @Query("count") count: Int = 100,
        @Query("access_token") token: String,
        @Query("v") version: String = "5.199"
    ): VkEnvelope
}
