/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

@file:Suppress("unused")

package com.fredboat.sentinel.entities

import java.awt.Color

// Embed builder originally by Frostbyte, but is heavily modified
data class Embed(
        var content: String = "",
        var title: String? = null,
        var url: String? = null,
        var description: String? = null,
        var color: Int? = null,
        var timestamp: Long? = null,
        var footer: Footer? = null,
        var thumbnail: String? = null,
        var image: String? = null,
        var author: Author? = null,
        var fields: MutableList<Field> = mutableListOf()
)

data class Field(
        var title: String = "",
        var body: String = "",
        var inline: Boolean = false
)

data class Footer(
        var iconUrl: String? = null,
        var text: String? = null
)

data class Author(
        var name: String = "",
        var url: String? = null,
        var iconUrl: String? = null
)

data class SelectOpt(
        var customId: String = "",
        var placeholder: String = "",
        var selectOpt: MutableList<Option> = mutableListOf()
)

data class Option(
        var label: String = "",
        var value: String = ""
)

inline fun menu(block: SelectOpt.() -> Unit): SelectOpt = SelectOpt().apply(block)

inline fun embed(block: Embed.() -> Unit): Embed = Embed().apply(block)

val FREDBOAT_COLOR = Color(28, 191, 226).rgb //#1CBFE2

/** Like [embed] but with FredBoat's color */
inline fun coloredEmbed(block: Embed.() -> Unit): Embed {
    val embed = Embed()
    embed.color = FREDBOAT_COLOR
    return embed.apply(block)
}

inline fun Embed.footer(block: Footer.() -> Unit) {
    footer = Footer().apply(block)
}

inline fun Embed.author(block: Author.() -> Unit) {
    author = Author().apply(block)
}

inline fun Embed.field(block: Field.() -> Unit) {
    fields.add(Field().apply(block))
}
fun Embed.field(title: String, body: String, inline: Boolean = false) {
    fields.add(Field(title, body, inline))
}

inline fun SelectOpt.option(block: Option.() -> Unit) {
    selectOpt.add(Option().apply(block))
}
fun SelectOpt.option(label: String, value: String) {
    selectOpt.add(Option(label, value))
}