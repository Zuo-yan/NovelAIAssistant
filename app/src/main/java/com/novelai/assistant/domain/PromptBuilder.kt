package com.novelai.assistant.domain

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.network.ChatMessage

/**
 * Prompt 工程构建器：
 * - 续写：前 3-5 章作为 Few-Shot 风格样本 + 用户指令
 * - What-if 分支：指定剧情点 + 假设条件
 * - 伴读：携带近期章节上下文与引文
 */
object PromptBuilder {

    private const val STYLE_SAMPLE_CHARS = 1200
    private const val CONTEXT_TAIL_CHARS = 2500

    fun continuationMessages(
        book: BookEntity,
        previousChapters: List<ChapterEntity>,
        instruction: String,
        useWorkshopCraft: Boolean = true
    ): List<ChatMessage> {
        val samples = previousChapters.takeLast(5).joinToString("\n\n") { ch ->
            "【《${book.title}》${ch.title}节选】\n" + ch.content.takeLast(STYLE_SAMPLE_CHARS)
        }
        val lastChapter = previousChapters.lastOrNull()
        val system = buildString {
            appendLine("你是资深小说作者，正在为《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}续写正文。")
            appendLine("请严格模仿以下原文片段的行文风格：叙事口吻、用词习惯、句式长短、对话风格、修辞与语气词。")
            appendLine()
            appendLine("=== 原文风格样本（最近章节结尾）===")
            appendLine(samples.ifBlank { "（无样本，请使用流畅的中文网文文风）" })
            appendLine("=== 样本结束 ===")
            appendLine()
            appendLine("续写要求：")
            appendLine("1. 直接衔接上文继续写下一章正文，不要复述或改写已有内容；")
            appendLine("2. 输出纯正文，不要任何解释、前言、标题标注或元评论；")
            appendLine("3. 保持人物性格与既定设定一致；")
            if (instruction.isNotBlank()) {
                appendLine("4. 作者补充要求：$instruction")
            } else {
                appendLine("4. 情节自然推进，留下恰当的悬念或转折。")
            }
            if (useWorkshopCraft) {
                appendLine()
                appendLine(AiSkills.NOVEL_WORKSHOP.systemPrompt)
            }
        }
        val user = buildString {
            if (lastChapter != null) {
                appendLine("以下是当前最后一章的结尾，请从这里自然衔接：")
                appendLine("【${lastChapter.title} 结尾】")
                append(lastChapter.content.takeLast(CONTEXT_TAIL_CHARS))
            } else {
                append("请开始创作本书的第一章。")
            }
        }
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    fun branchMessages(
        book: BookEntity,
        parentChapter: ChapterEntity,
        whatIf: String,
        useWorkshopCraft: Boolean = true
    ): List<ChatMessage> {
        val system = buildString {
            appendLine("你是资深小说作者。用户希望为《${book.title}》推演一个「平行宇宙」分支剧情。")
            appendLine("请模仿以下原文的行文风格（叙事口吻、用词、句式、对话风格）：")
            appendLine(parentChapter.content.takeLast(STYLE_SAMPLE_CHARS))
            appendLine()
            appendLine("要求：")
            appendLine("1. 从原作指定剧情点出发，按照「假设」重写后续走向，形成一条新的剧情线；")
            appendLine("2. 输出纯正文，不要解释、不要标注、不要复述原文；")
            appendLine("3. 人物性格保持一致，但剧情走向必须体现假设带来的改变。")
            if (useWorkshopCraft) {
                appendLine()
                appendLine(AiSkills.NOVEL_WORKSHOP.systemPrompt)
            }
        }
        val user = "假设（What-if）：$whatIf\n\n以下是需要改写的原章《${parentChapter.title}》结尾：\n" +
            parentChapter.content.takeLast(CONTEXT_TAIL_CHARS)
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    fun companionMessages(
        book: BookEntity,
        currentChapter: ChapterEntity?,
        history: List<ChatMessage>,
        question: String,
        quotedText: String? = null,
        retrievedChapters: List<com.novelai.assistant.data.db.ChapterEntity> = emptyList(),
        spoilerFree: Boolean = true,
        skillPrompt: String = ""
    ): List<ChatMessage> {
        val system = buildString {
            appendLine("你是《${book.title}》${if (book.author.isNotBlank()) "（作者：${book.author}）" else ""}的智能伴读助手。")
            if (retrievedChapters.isNotEmpty()) {
                appendLine("以下是按用户问题检索到的相关原文片段（供参考作答）：")
                for (ch in retrievedChapters) {
                    appendLine("——《${ch.title}》：${ch.content.take(600)}")
                }
            }
            if (currentChapter != null) {
                appendLine("用户当前正读到《${currentChapter.title}》，其结尾如下：")
                appendLine(currentChapter.content.takeLast(CONTEXT_TAIL_CHARS))
            }
            appendLine("回答要求：准确、简洁、有依据；引用原文时注明章节名。")
            if (spoilerFree) {
                appendLine("严格模式：用户尚未阅读之后的内容，禁止提及或暗示任何后续章节的情节发展。")
            } else {
                appendLine("全书回忆模式：允许引用全书内容回答，包括用户尚未读到的部分。")
            }
            if (skillPrompt.isNotBlank()) {
                appendLine()
                appendLine("【用户选择的技能，请按其要求作答】")
                appendLine(skillPrompt)
            }
        }
        val messages = mutableListOf(ChatMessage("system", system))
        messages.addAll(history.takeLast(10))
        val userContent = if (quotedText.isNullOrBlank()) question
        else "【用户划线选段】\n$quotedText\n\n【用户的问题】\n$question"
        messages.add(ChatMessage("user", userContent))
        return messages
    }

    /** 划线即问的快捷指令 */
    const val QUICK_ANALYZE = "请解析这段文字的深意、伏笔与表达技巧。"
    const val QUICK_EMOTION = "请总结这段文字的情绪基调，并分析人物当下的心理状态。"
    const val QUICK_ROAST = "请用轻松幽默的口吻吐槽这段文字，可以玩梗，但不要恶意。"
}
