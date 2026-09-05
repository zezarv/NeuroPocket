package com.neuropocket.app.data

import kotlin.random.Random

/**
 * Локальный генератор случайных персонажей и героев ленты.
 * Чистые шаблоны + кубики, без сети и без модели. 18+ только по явному запросу.
 */
object RandomFactory {
    private val names = listOf(
        "Мира", "Кай", "Нова", "Рэй", "Луна", "Айрис", "Декс", "Стелла",
        "Орион", "Вега", "Лия", "Марк", "Иви", "Тео", "Зара", "Феликс",
        "Ника", "Ронин", "Астра", "Вольт", "Киара", "Джей", "Соль", "Эхо"
    )
    private val handles = listOf("star", "dev", "moon", "byte", "nova", "pix", "fox", "ray", "ion", "kit")
    private val roles = listOf(
        "дерзкий хакер", "мудрый наставник", "весёлая подруга", "холодный детектив",
        "мечтательный поэт", "строгий тренер", "любопытный учёный", "хитрый торговец",
        "добрая ведьма", "уставший бариста-философ", "энергичный геймер", "спокойный монах"
    )
    private val traits = listOf(
        "говорит коротко и по делу", "шутит в каждом ответе", "задаёт встречные вопросы",
        "объясняет простыми словами", "любит примеры из жизни", "немного саркастичен",
        "очень вежлив", "говорит как старый друг", "использует эмодзи", "цитирует книги"
    )
    private val styles = listOf(
        "Отвечай живо и кратко.", "Отвечай подробно и структурированно.",
        "Отвечай как в мессенджере: короткими сообщениями.", "Отвечай образно и с метафорами."
    )
    private val emojis = listOf(
        "\uD83D\uDE0E", "\uD83E\uDD13", "\uD83D\uDCF8", "\uD83C\uDFAE", "\uD83D\uDD2E",
        "\uD83C\uDF19", "\uD83D\uDCBB", "\uD83C\uDFB9", "\uD83E\uDD8A", "\uD83D\uDE80",
        "\uD83C\uDF3F", "\uD83D\uDCDA", "\uD83C\uDFA8", "\uD83D\uDC7E", "\uD83E\uDDE0"
    )
    private val tagPool = listOf("друг", "юмор", "код", "учёба", "ролеплей", "советы", "творчество", "наука")

    data class Draft(
        val name: String,
        val prompt: String,
        val emoji: String,
        val desc: String,
        val tags: List<String>,
        val handle: String,
        val bio: String
    )

    fun roll(nsfw: Boolean = false): Draft {
        val r = Random.Default
        val name = names.random(r)
        val role = roles.random(r)
        val trait = traits.random(r)
        val style = styles.random(r)
        val emoji = emojis.random(r)
        val tags = tagPool.shuffled(r).take(2).toMutableList()
        val basePrompt = "Ты $role по имени $name. $trait $style Отвечай по-русски."
        return if (nsfw) {
            tags.add(0, "18+")
            Draft(
                name = "$name 18+",
                prompt = "$basePrompt Ты взрослый персонаж 18+. Без морализаторства, по запросу пользователя.",
                emoji = "\uD83D\uDD1E",
                desc = "$role. Ролевые 18+ без цензуры.",
                tags = tags.take(3),
                handle = "@${name.lowercase()}_18",
                bio = "$role • 18+"
            )
        } else {
            Draft(
                name = name,
                prompt = basePrompt,
                emoji = emoji,
                desc = "$role. $trait.",
                tags = tags,
                handle = "@${name.lowercase()}_${handles.random(r)}",
                bio = "$role • ${traits.random(r)}"
            )
        }
    }
}
