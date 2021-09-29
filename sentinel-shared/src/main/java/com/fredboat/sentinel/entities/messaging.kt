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

data class SlashOptions(
        var slashOptions: MutableList<SlashOption> = mutableListOf()
)

data class SlashOption(
        var optionType: Int = 0,
        var optionName: String = "",
        var optionDescription: String = "",
        var required: Boolean = false
)

/**
 * Components
 */

data class Buttons(
        var buttons: MutableList<Button> = mutableListOf()
)

data class Button(
        var id: String = "",
        var label: String = "",
        var emoji: String = ""
)

data class SelectMenu(
        var customId: String = "",
        var placeholder: String = "",
        var selectOptions: MutableList<Option> = mutableListOf()
)

data class Option(
        var label: String = "",
        var value: String = ""
)

inline fun slashOptions(block: SlashOptions.() -> Unit): SlashOptions = SlashOptions().apply(block)

inline fun buttons(block: Buttons.() -> Unit): Buttons = Buttons().apply(block)

inline fun menu(block: SelectMenu.() -> Unit): SelectMenu = SelectMenu().apply(block)

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

inline fun SlashOptions.option(block: SlashOption.() -> Unit) {
    slashOptions.add(SlashOption().apply(block))
}
fun SlashOptions.option(optionType: Int, optionName: String, optionDescription: String, required: Boolean) {
    slashOptions.add(SlashOption(optionType, optionName, optionDescription, required))
}

/**
 * Components
 */

inline fun Buttons.button(block: Button.() -> Unit) {
    buttons.add(Button().apply(block))
}
fun Buttons.button(label: String, value: String) {
    buttons.add(Button(label, value))
}

inline fun SelectMenu.option(block: Option.() -> Unit) {
    selectOptions.add(Option().apply(block))
}
fun SelectMenu.option(label: String, value: String) {
    selectOptions.add(Option(label, value))
}