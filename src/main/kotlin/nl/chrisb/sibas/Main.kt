package nl.chrisb.sibas

import dev.minn.jda.ktx.injectKTX
import dev.minn.jda.ktx.listener
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import nl.chrisb.sibas.commands.*
import nl.chrisb.sibas.games.common.InviteManager
import nl.chrisb.sibas.games.common.MatchManager

fun main() {
    val jda = JDABuilder.createLight(
        System.getenv("DISCORD_TOKEN")
            ?: throw RuntimeException("Environment variable DISCORD_TOKEN should contain the discord bot token")
    )
        .injectKTX()
        .setActivity(Activity.playing("Peggle"))
        .build()



    jda.listener<MessageReactionAddEvent> { event ->
        event.retrieveMessage().queue {
            Messages.updateMessage(it)
        }
    }

    jda.listener<MessageReactionRemoveEvent> { event ->
        event.retrieveMessage().queue {
            Messages.updateMessage(it)
        }
    }

    jda.listener<MessageUpdateEvent> { event ->
        Messages.updateMessage(event.message)
    }

    jda.listener<MessageReceivedEvent> { event ->
        Messages.scheduleIndex(event.channel)

        val content = event.message.contentRaw
        if (content.startsWith("+run ")) {
            try {
                val code = parseRunCommand(content)

                val result = code.run()

                val message = if (result.compileOutput?.code ?: 0 != 0) {
                    "**ERROR!**\n" +
                            "```\n${
                                result.compileOutput.output.replace("`", "`\u200b").trim()
                            }\n```"
                } else {
                    "```\n${
                        result.output.output.replace("`", "`\u200b").trim()
                    }\n```"
                }

                event.message.reply(message).queue()
            } catch (e: Throwable) {
                event.message.reply("**ERROR!** ${e.message}").queue()
            }
        }
    }

    jda.listener<ButtonClickEvent> { event ->
        val id = event.button?.id

        when {
            id == "game:invite:accept" -> InviteManager.respond(event.interaction, event.user, true)
            id == "game:invite:deny" -> InviteManager.respond(event.interaction, event.user, false)
            id?.startsWith("game:action:") == true -> {
                MatchManager.processButton(event.interaction, event.user, id.removePrefix("game:action:"))
            }
        }
    }

    jda.registerCommands(
        IndexCommand,
        IndexAllCommand,
        ReindexAllCommand,
        ClearDbCommand,
        StatsCommand,

        ProfileCommand,
        LeaderboardCommand,

        BattleCommand
    )

    Messages
}

fun Member.isAdmin() = id == "120593086844895234" || roles.any { it.name == "Staff" }
