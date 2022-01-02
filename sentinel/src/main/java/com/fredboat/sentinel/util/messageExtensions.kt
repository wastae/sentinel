/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.entities.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.Component
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import java.time.Instant

private val threadLocal: ThreadLocal<EmbedBuilder> = ThreadLocal.withInitial { EmbedBuilder() }

fun Embed.toJda(): MessageEmbed {
    val builder = threadLocal.get().clear()
    builder.setTitle(title, url)
    color?.let { builder.setColor(it) }
    builder.setDescription(description)
    builder.setTimestamp(timestamp?.let { Instant.ofEpochMilli(it.toLong()) })
    builder.setFooter(footer?.text, footer?.iconUrl)
    builder.setThumbnail(thumbnail)
    builder.setImage(image)
    builder.setAuthor(author?.name, author?.url, author?.iconUrl)
    fields.forEach {
        builder.addField(it.title, it.body, it.inline)
    }

    return builder.build()
}

fun SlashOptions.toJda(): ArrayList<OptionData> {
    val optionsData = ArrayList<OptionData>()
    slashOptions.forEach {
        optionsData.add(OptionData(OptionType.fromKey(it.optionType), it.optionName, it.optionDescription, it.required))
    }

    return optionsData
}

fun SlashGroup.toJdaExt(): SubcommandGroupData {
    return SubcommandGroupData(name!!, description!!).addSubcommands(buildSubCmds(subCommands))
}

fun SlashGroup.toJda(): ArrayList<SubcommandData> {
    return buildSubCmds(subCommands)
}

private fun buildSubCmds(subCommands: MutableList<SlashSubcommand>): ArrayList<SubcommandData> {
    val subCmds = ArrayList<SubcommandData>()
    val subCmdOptions = ArrayList<OptionData>()
    subCommands.forEach { it ->
        it.slashOptions.slashOptions.forEach {
            subCmdOptions.add(OptionData(OptionType.fromKey(it.optionType), it.optionName, it.optionDescription, it.required))
        }
        subCmds.add(SubcommandData(it.name, it.description).addOptions(subCmdOptions))
        subCmdOptions.clear()
    }

    return subCmds
}

fun SlashSubcommand.toJda(): SubcommandData {
    val optionsData = ArrayList<OptionData>()
    slashOptions.slashOptions.forEach {
        optionsData.add(OptionData(OptionType.fromKey(it.optionType), it.optionName, it.optionDescription, it.required))
    }

    return SubcommandData(name, description).addOptions(optionsData)
}

fun SelectMenu.toJda(): SelectionMenu {
    val menu = SelectionMenu.create(customId).setPlaceholder(placeholder)
    selectOptions.forEach {
        menu.addOption(it.label, it.value)
    }

    return menu.build()
}

fun Buttons.toJda(): ArrayList<ActionRow> {
    val actionRows = ArrayList<ActionRow>()
    val buttonsList = ArrayList<Component>()
    buttons.forEach {
        if (it.label.isEmpty()) buttonsList.add(Button.secondary(it.id, Emoji.fromMarkdown(it.emoji)))
        else buttonsList.add(Button.secondary(it.id, it.label).withEmoji(Emoji.fromMarkdown(it.emoji)))
        if (buttonsList.size == 5) {
            actionRows.add(ActionRow.of(buttonsList))
            buttonsList.clear()
        } else if (buttons.last() == it) actionRows.add(ActionRow.of(buttonsList))
    }

    return actionRows
}