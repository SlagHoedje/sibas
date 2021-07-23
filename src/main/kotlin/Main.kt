import dev.minn.jda.ktx.*
import dev.minn.jda.ktx.interactions.*
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.guild.GuildReadyEvent

// https://discord.com/oauth2/authorize?client_id=865179659591483403&scope=bot+applications.commands

fun main(args: Array<String>) {
    val jda = JDABuilder.createLight(args[0])
        .injectKTX()
        .setActivity(Activity.playing("Tetris"))
        .build()

    jda.listener<GuildReadyEvent> {
        it.guild.updateCommands {
            addCommands(
                Command("index", "Index messages of channel") {
                    option<MessageChannel>("channel", "Channel to index", required = true)
                },
                Command("indexall", "Index messages of all channels"),
                Command("stats", "Show bot stats"),
                Command("leaderboard", "Show various leaderboards") {
                    addSubcommandGroups(
                        SubcommandGroup("channel", "Channel related leaderboards") {
                            subcommand("messages", "Show a leaderboard of channels with the most messages")
                        },
                        SubcommandGroup("user", "User related leaderboards") {
                            subcommand("messages", "Show a leaderboard of users with the most messages")
                        }
                    )
                },
            )

            queue()
        }
    }

    jda.onCommand("stats") {
        val stats = Messages.stats()

        it.replyEmbeds(Embed {
            field(name = "Messages indexed", value = stats.messages.toString())
        }).queue()
    }

    jda.onCommand("index") { event ->
        val channel = event.getOptionsByName("channel")[0].asMessageChannel!!
        event.reply("Indexing <#${channel.id}>...").queue()

        Messages.index(channel, event).thenAccept {
            event.hook.editOriginal("**DONE!** Indexed <#${channel.id}>. _($it messages)_").queue()
        }
    }

    jda.onCommand("indexall") { event ->
        event.reply("Indexing all channels...").queue()

        var count = 0
        event.guild?.textChannels?.forEach {
            count += Messages.index(it, event).await()
        } ?: run { event.hook.editOriginal("**ERROR!** This command should be executed in a guild.").queue() }

        event.hook.editOriginal("**DONE!** Indexed all channels. _($count messages)_").queue()
    }

    jda.onCommand("leaderboard") { event ->
        event.reply("Indexing all channels...").queue()

        var count = 0
        event.guild?.textChannels?.forEach {
            count += Messages.index(it, event).await()
        } ?: run { event.hook.editOriginal("**ERROR!** This command should be executed in a guild.").queue() }

        when (event.subcommandGroup) {
            "channel" -> when (event.subcommandName) {
                "messages" -> {
                    val leaderboard = Messages.channelMessagesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Channel message leaderboard"
                            description = leaderboard.joinToString("\n") {
                                "<#${it.first}>: ${it.second} messages"
                            }
                        })).queue()
                }
                else -> event.reply("**ERROR!** Invalid subcommand.").queue()
            }
            "user" -> when (event.subcommandName) {
                "messages" -> {
                    val leaderboard = Messages.userMessagesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "User message leaderboard"
                            description = leaderboard.joinToString("\n") {
                                "<@${it.first}>: ${it.second} messages"
                            }
                        })).queue()
                }
                else -> event.reply("**ERROR!** Invalid subcommand.").queue()
            }
            else -> event.reply("**ERROR!** Invalid subcommand group.").queue()
        }
    }

    Messages
}
