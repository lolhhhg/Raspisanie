package com.yourdomain.scheduleapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "schedule", indices = [Index(value = ["dayOfWeek", "pairNumber"], unique = true)])
data class ScheduleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: Int, val pairNumber: Int,
    val numeratorSubject: String = "", val denominatorSubject: String = "",
    val numeratorRoom: String = "", val denominatorRoom: String = "",
    val numeratorHomework: String = "", val denominatorHomework: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "vk_images")
data class VkPostImageEntity(@PrimaryKey val url: String, val postDate: String, val caption: String, val downloadedAt: Long = System.currentTimeMillis())

@Dao interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ScheduleItemEntity>)
    @Query("SELECT * FROM schedule WHERE dayOfWeek=:day ORDER BY pairNumber") fun observeDay(day: Int): Flow<List<ScheduleItemEntity>>
    @Query("SELECT * FROM schedule ORDER BY dayOfWeek,pairNumber") fun observeAll(): Flow<List<ScheduleItemEntity>>
    @Query("SELECT * FROM schedule ORDER BY dayOfWeek,pairNumber") suspend fun getAllNow(): List<ScheduleItemEntity>
    @Query("SELECT * FROM schedule WHERE dayOfWeek=:day AND pairNumber=:pair LIMIT 1") suspend fun get(day: Int, pair: Int): ScheduleItemEntity?
    @Query("DELETE FROM schedule") suspend fun clearAll()
}

@Dao interface VkImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertImages(images: List<VkPostImageEntity>)
    @Query("SELECT * FROM vk_images ORDER BY postDate DESC") fun observeAll(): Flow<List<VkPostImageEntity>>
    @Query("DELETE FROM vk_images") suspend fun clearAll()
}

@Database(entities = [ScheduleItemEntity::class, VkPostImageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() { abstract fun scheduleDao(): ScheduleDao; abstract fun vkImageDao(): VkImageDao }
