package com.novelai.assistant.data.importer

import com.novelai.assistant.data.db.BookSourceType
import java.io.InputStream

interface BookImporter {
    fun sourceType(): BookSourceType
    suspend fun parse(inputStream: InputStream, fileName: String): ParsedBook
}
