package com.novelai.assistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun bookSourceTypeToString(value: BookSourceType): String = value.name

    @TypeConverter
    fun stringToBookSourceType(value: String): BookSourceType =
        BookSourceType.entries.firstOrNull { it.name == value } ?: BookSourceType.LOCAL_TXT

    @TypeConverter
    fun chapterOriginTypeToString(value: ChapterOriginType): String = value.name

    @TypeConverter
    fun stringToChapterOriginType(value: String): ChapterOriginType =
        ChapterOriginType.entries.firstOrNull { it.name == value } ?: ChapterOriginType.ORIGINAL
}

@Database(
    entities = [BookEntity::class, ChapterEntity::class, AiChatRecordEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chatDao(): ChatDao
    abstract fun categoryDao(): CategoryDao
}
