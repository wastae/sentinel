/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.entities.Buttons
import com.fredboat.sentinel.entities.Embed
import com.fredboat.sentinel.entities.SelectMenu
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import java.time.Instant

private val threadLocal: ThreadLocal<EmbedBuilder> = ThreadLocal.withInitial { EmbedBuilder() }

fun Embed.toJda(): MessageEmbed {
    val builder = threadLocal.get().clear()
    builder.setTitle(title, url)
    color?.let { builder.setColor(it) }
    builder.setDescription(description)
    builder.setTimestamp(timestamp?.let { Instant.ofEpochMilli(it) })
    builder.setFooter(footer?.text, footer?.iconUrl)
    builder.setThumbnail(thumbnail)
    builder.setImage(image)
    builder.setAuthor(author?.name, author?.url, author?.iconUrl)
    fields.forEach {
        builder.addField(it.title, it.body, it.inline)
    }

    return builder.build()
}

fun SelectMenu.toJda(): SelectionMenu {
    val menu = SelectionMenu.create(customId).setPlaceholder(placeholder)
    selectOptions.forEach {
        menu.addOption(it.label, it.value)
    }

    return menu.build()
}

fun Buttons.toJda(): MutableList<Button> {
    val buttonList = mutableListOf<Button>()
    buttons.forEach {
        buttonList.add(Button.secondary(it.id, it.label).withEmoji(Emoji.fromMarkdown(it.emoji)))
    }

    return buttonList
}