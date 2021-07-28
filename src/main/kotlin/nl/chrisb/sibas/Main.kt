package nl.chrisb.sibas

import dev.minn.jda.ktx.Embed
import dev.minn.jda.ktx.injectKTX
import dev.minn.jda.ktx.interactions.Option
import dev.minn.jda.ktx.listener
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import java.time.ZoneOffset

fun main() {
    val jda = JDABuilder.createLight(
        System.getenv("DISCORD_TOKEN")
            ?: throw RuntimeException("Environment variable DISCORD_TOKEN should contain the discord bot token")
    )
        .injectKTX()
        .setActivity(Activity.playing("Tetris"))
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
    }

    jda.commands {
        command("index", "Index messages of a channel") {
            option(Option<MessageChannel>("channel", "The channel to index", required = true))

            executor {
                val channel = messageChannel("channel") ?: fail("No channel specified")
                message("Indexing <#${channel.id}>...")

                val count = Messages.index(channel) { blocked, count ->
                    if (blocked) {
                        message("Indexing <#${channel.id}>... _(waiting for another thread to finish)_")
                    } else {
                        message("Indexing <#${channel.id}>... _($count messages)_")
                    }
                }
                message("**DONE!** Indexed <#${channel.id}>. _($count messages)_")
            }
        }

        command("stats", "Show some general bot stats") {
            executor {
                val stats = Messages.stats()

                embed(Embed {
                    field(name = "Messages", value = stats.messages.toString())
                    field(name = "Reactions", value = stats.reactions.toString())
                })
            }
        }

        command("indexall", "Index messages of all channels") {
            executor {
                val count = preIndex()
                message("**DONE!** Indexed all channels. _($count messages)_")
            }
        }

        command("cleardb", "Clear all data from the database") {
            executor {
                checkAdmin()

                message("Clearing the database...")
                Messages.clearDB()
                message("**DONE!** Cleared the database.")
            }
        }

        command("reindexall", "Clear all data from the database and reindex all messages") {
            executor {
                checkAdmin()

                message("Clearing the database...")
                Messages.clearDB()

                val count = preIndex()
                message("**DONE!** Indexed all channels. _($count messages)_")
            }
        }

        command("leaderboard") {
            subCommand(group = "channel", name = "messages", description = "Top channels with the most messages") {
                executor {
                    event.deferReply().queue()

                    val leaderboard = Messages.channelMessagesLeaderboard()

                    embed(Embed {
                        title = "Most messages in channels"
                        description = leaderboard.joinToString("\n") {
                            "<#${it.first}>: ${it.second} messages"
                        }
                    })
                }
            }

            subCommand(group = "user", name = "messages", description = "Top users with the most messages") {
                executor {
                    event.deferReply().queue()

                    val leaderboard = Messages.userMessagesLeaderboard()

                    embed(Embed {
                        title = "Most messages by users"
                        description = leaderboard.joinToString("\n") {
                            "<@${it.first}>: ${it.second} messages"
                        }
                    })
                }
            }

            for (reaction in listOf("upvote", "downvote")) {
                subCommand(
                    group = "channel",
                    name = "${reaction}s",
                    description = "Top channels with the most ${reaction}s"
                ) {
                    executor {
                        event.deferReply().queue()

                        val leaderboard = Messages.channelReactionsLeaderboard(reaction)

                        embed(Embed {
                            title = "Most ${reaction}d channels"
                            description = leaderboard.joinToString("\n") {
                                "<#${it.first}>: ${it.second} ${reaction}s"
                            }
                        })
                    }
                }

                subCommand(
                    group = "user",
                    name = "${reaction}s",
                    description = "Top users with the most ${reaction}s"
                ) {
                    executor {
                        event.deferReply().queue()

                        val leaderboard = Messages.userReactionLeaderboard(reaction)

                        embed(Embed {
                            title = "Most ${reaction}d users"
                            description = leaderboard.joinToString("\n") {
                                "<@${it.first}>: ${it.second} ${reaction}s"
                            }
                        })
                    }
                }

                subCommand(
                    group = "message",
                    name = "${reaction}s",
                    description = "Top messages with the most ${reaction}s"
                ) {
                    option(Option<MessageChannel>("channel", "Channel to scan"))

                    executor {
                        event.deferReply().queue()

                        val channel = messageChannel("channel")
                        val leaderboard = Messages.messageReactionLeaderboard(reaction, channel)

                        embed(Embed {
                            title = "Most ${reaction}d messages${if (channel != null) " in #${channel.name}" else ""}"
                            description = leaderboard.withIndex().joinToString("\n") { (i, spot) ->
                                val message = spot.first
                                val reactions = spot.second

                                val jdaMessage = (event.guild?.getGuildChannelById(message.channel) as? MessageChannel)
                                    ?.retrieveMessageById(message.id)
                                    ?.complete()

                                var out = "**${i + 1}.** " +
                                        (if (jdaMessage != null) "[Link](${jdaMessage.jumpUrl}) - " else "") +
                                        "<t:${message.timestamp.toLocalDateTime().toEpochSecond(ZoneOffset.UTC)}:D>, " +
                                        "<@${message.author}>" +
                                        (if (channel == null) " in <#${message.channel}>" else "") +
                                        " with $reactions ${reaction}s\n"

                                message.contents?.let {
                                    out += "> " + it.lines().joinToString("\n> ") + "\n"
                                }

                                out
                            }
                        })
                    }
                }
            }
        }
    }

    Messages
}

fun Member.isAdmin() = id == "120593086844895234" || roles.any { it.name == "Staff" }
