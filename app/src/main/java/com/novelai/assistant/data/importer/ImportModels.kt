package com.novelai.assistant.data.importer

import com.novelai.assistant.data.db.BookSourceType

data class ParsedChapter(val title: String, val content: String)

data class ParsedBook(
    val title: String,
    val author: String,
    val coverBytes: ByteArray? = null,
    val chapters: List<ParsedChapter>,
    val sourceType: BookSourceType,
    val sourcePath: String
)
