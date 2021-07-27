package nl.chrisb.sibas

import dev.minn.jda.ktx.Embed
import dev.minn.jda.ktx.injectKTX
import dev.minn.jda.ktx.interactions.Option
import dev.minn.jda.ktx.listener
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageChannel
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

    jda.commands {
        command("index", "Index messages of a channel") {
            option(Option<MessageChannel>("channel", "The channel to index", required = true))

            executor {
                val channel = messageChannel("channel") ?: fail("No channel specified")
                message("Indexing <#${channel.id}>...")

                val count = Messages.index(channel, event)
                message("**DONE!** Indexed <#${channel.id}>. _($count messages)_")
            }
        }

        command("stats", "Show some general bot stats") {
            executor {
                val stats = Messages.stats()

                event.replyEmbeds(Embed {
                    field(name = "Messages", value = stats.messages.toString())
                    field(name = "Reactions", value = stats.reactions.toString())
                }).queue()
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
                    val count = preIndex()

                    val leaderboard = Messages.channelMessagesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_").and(
                        event.hook.editOriginalEmbeds(Embed {
                            title = "Most messages in channels"
                            description = leaderboard.joinToString("\n") {
                                "<#${it.first}>: ${it.second} messages"
                            }
                        })
                    ).queue()
                }
            }

            subCommand(group = "channel", name = "upvotes", description = "Top channels with the most upvotes") {
                executor {
                    val count = preIndex()

                    val leaderboard = Messages.channelUpvotesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most upvoted channels"
                            description = leaderboard.joinToString("\n") {
                                "<#${it.first}>: ${it.second} upvotes"
                            }
                        })).queue()
                }
            }

            subCommand(group = "user", name = "messages", description = "Top users with the most messages") {
                executor {
                    val count = preIndex()

                    val leaderboard = Messages.userMessagesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most messages by users"
                            description = leaderboard.joinToString("\n") {
                                "<@${it.first}>: ${it.second} messages"
                            }
                        })).queue()
                }
            }

            subCommand(group = "user", name = "upvotes", description = "Top users with the most upvotes") {
                executor {
                    val count = preIndex()

                    val leaderboard = Messages.userUpvoteLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most upvoted users"
                            description = leaderboard.joinToString("\n") {
                                "<@${it.first}>: ${it.second} upvotes"
                            }
                        })).queue()
                }
            }

            subCommand(group = "message", name = "upvotes", description = "Top messages with the most upvotes") {
                option(Option<MessageChannel>("channel", "Channel to scan"))

                executor {
                    val count = preIndex()

                    val channel = messageChannel("channel")
                    val leaderboard = Messages.messageUpvoteLeaderboard(channel)

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most upvoted messages${if (channel != null) " in #${channel.name}" else ""}"
                            description = leaderboard.withIndex().joinToString("\n") { (i, spot) ->
                                val message = spot.first
                                val upvotes = spot.second

                                val jdaMessage = (event.guild?.getGuildChannelById(message.channel) as? MessageChannel)
                                    ?.retrieveMessageById(message.id)
                                    ?.complete()

                                var out = "**${i + 1}.** " +
                                        (if (jdaMessage != null) "[Link](${jdaMessage.jumpUrl}) - " else "") +
                                        "<t:${message.timestamp.toLocalDateTime().toEpochSecond(ZoneOffset.UTC)}:D>, " +
                                        "<@${message.author}>" +
                                        (if (channel == null) " in <#${message.channel}>" else "") +
                                        " with $upvotes upvotes\n"

                                message.contents?.let {
                                    out += "> " + it.lines().joinToString("\n> ") + "\n"
                                }

                                out
                            }
                        })).queue()
                }
            }
        }
    }

    Messages
}

fun Member.isAdmin() = id == "120593086844895234" || roles.any { it.name == "Staff" }
