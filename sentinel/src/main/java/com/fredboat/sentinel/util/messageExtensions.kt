/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.entities.Embed
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.MessageEmbed
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