package nl.chrisb.sibas.commands

import dev.minn.jda.ktx.Embed
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import nl.chrisb.sibas.Messages
import java.time.ZoneOffset

@Command(name = "leaderboard")
object LeaderboardCommand {
    @Command(group = "channel", name = "messages", description = "Top channels with the most messages")
    fun channelMessages(hook: CommandHook) {
        hook.defer()

        val leaderboard = Messages.channelMessagesLeaderboard()

        hook.embed(Embed {
            title = "Most messages in channels"
            description = leaderboard.joinToString("\n") {
                "<#${it.first}>: ${it.second} messages"
            }
        })
    }

    @Command(group = "channel", name = "upvotes", description = "Top channels with the most upvotes")
    fun channelUpvotes(hook: CommandHook) {
        channelReactions(hook, "upvote")
    }

    @Command(group = "channel", name = "downvotes", description = "Top channels with the most downvotes")
    fun channelDownvotes(hook: CommandHook) {
        channelReactions(hook, "downvote")
    }

    private fun channelReactions(hook: CommandHook, reaction: String) {
        hook.defer()

        val leaderboard = Messages.channelReactionsLeaderboard(reaction)

        hook.embed(Embed {
            title = "Most ${reaction}d channels"
            description = leaderboard.joinToString("\n") {
                "<#${it.first}>: ${it.second} ${reaction}s"
            }
        })
    }

    @Command(group = "user", name = "messages", description = "Top users with the most messages")
    fun userMessages(hook: CommandHook) {
        hook.defer()

        val leaderboard = Messages.userMessagesLeaderboard()

        hook.embed(Embed {
            title = "Most messages by users"
            description = leaderboard.joinToString("\n") {
                "<@${it.first}>: ${it.second} messages"
            }
        })
    }

    @Command(group = "user", name = "upvotes", description = "Top users with the most upvotes")
    fun userUpvotes(hook: CommandHook) {
        userReactions(hook, "upvote")
    }

    @Command(group = "user", name = "downvotes", description = "Top users with the most downvotes")
    fun userDownvotes(hook: CommandHook) {
        userReactions(hook, "downvote")
    }

    private fun userReactions(hook: CommandHook, reaction: String) {
        hook.defer()

        val leaderboard = Messages.userReactionLeaderboard(reaction)

        hook.embed(Embed {
            title = "Most ${reaction}d users"
            description = leaderboard.joinToString("\n") {
                "<@${it.first}>: ${it.second} ${reaction}s"
            }
        })
    }

    @Command(group = "message", name = "upvotes", description = "Top messages with the most upvotes")
    fun messageUpvotes(hook: CommandHook, channel: GuildChannel?) {
        messageReactions(hook, channel, "upvote")
    }

    @Command(group = "message", name = "downvotes", description = "Top messages with the most downvotes")
    fun messageDownvotes(hook: CommandHook, channel: GuildChannel?) {
        messageReactions(hook, channel, "downvote")
    }

    private fun messageReactions(hook: CommandHook, channel: GuildChannel?, reaction: String) {
        hook.defer()

        if (channel != null && channel !is MessageChannel) {
            hook.fail("That's not a message channel.")
        }

        val messageChannel = channel as? MessageChannel
        val leaderboard = Messages.messageReactionLeaderboard(reaction, messageChannel)

        val spots = leaderboard.withIndex().map { (i, spot) ->
            val message = spot.first
            val reactions = spot.second

            val jdaMessage = (hook.event.guild?.getGuildChannelById(message.channel) as? MessageChannel)
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

        var firstTitle =
            "Most ${reaction}d messages${if (channel != null) " in #${channel.name}" else ""}"
        var currentDescription = ""

        val embeds = mutableListOf<MessageEmbed>()

        for (spot in spots) {
            if (currentDescription.length + spot.length >= 2047) {
                embeds.add(Embed {
                    title = firstTitle
                    description = currentDescription
                })

                currentDescription = ""
                firstTitle = " "
            }

            currentDescription += spot + "\n"
        }

        embeds.add(Embed {
            title = firstTitle
            description = currentDescription
        })

        hook.event.hook.editOriginalEmbeds(embeds).queue()
    }
}
