/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.entities.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import java.time.Instant
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu

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

fun SlashOptions.toJda(): ArrayList<OptionData> {
    val optionsData = ArrayList<OptionData>()
    slashOptions.forEach { it ->
        val optionData = OptionData(
            OptionType.fromKey(it.optionType), it.optionName, it.optionDescription, it.required, it.autoComplete
        )

        if (it.choices.choices.isNotEmpty()) {
            val choices = ArrayList<Command.Choice>()
            it.choices.choices.forEach {
                choices.add(Command.Choice(it.name, it.value))
            }

            optionData.addChoices(choices)
        }

        optionsData.add(optionData)
    }

    return optionsData
}

fun Choices.toJda(): ArrayList<Command.Choice> {
    val choice = ArrayList<Command.Choice>()
    choices.forEach {
        choice.add(Command.Choice(it.name, it.value))
    }

    return choice
}

fun SlashGroup.toJdaExt(): SubcommandGroupData {
    return SubcommandGroupData(name!!, description!!).addSubcommands(buildSubCmds(subCommands))
}

fun SlashGroup.toJda(): ArrayList<SubcommandData> {
    return buildSubCmds(subCommands)
}

private fun buildSubCmds(subCommands: MutableList<SlashSubcommand>): ArrayList<SubcommandData> {
    val subCmds = ArrayList<SubcommandData>()
    subCommands.forEach {
        subCmds.add(SubcommandData(it.name, it.description).addOptions(it.slashOptions.toJda()))
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

fun SelectMenu.toJda(): net.dv8tion.jda.api.interactions.components.selections.SelectMenu {
    val menu = StringSelectMenu.create(customId).setPlaceholder(placeholder)
    selectOptions.forEach {
        menu.addOption(it.label, it.value)
    }

    return menu.build()
}

fun Buttons.toJda(): ArrayList<ActionRow> {
    val actionRows = ArrayList<ActionRow>()
    val buttonsList = ArrayList<ItemComponent>()
    buttons.forEach {
        if (it.label.isEmpty()) buttonsList.add(net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(it.id, Emoji.fromFormatted(it.emoji)))
        else buttonsList.add(net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(it.id, it.label).withEmoji(Emoji.fromFormatted(it.emoji)))
        if (buttonsList.size == 5) {
            actionRows.add(ActionRow.of(buttonsList))
            buttonsList.clear()
        } else if (buttons.last() == it) actionRows.add(ActionRow.of(buttonsList))
    }

    return actionRows
}