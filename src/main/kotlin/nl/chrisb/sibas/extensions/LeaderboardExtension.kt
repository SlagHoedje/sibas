package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.ReactionEmoji
import dev.kord.rest.builder.message.create.embed
import nl.chrisb.sibas.format
import nl.chrisb.sibas.longId
import nl.chrisb.sibas.messages.Channels
import nl.chrisb.sibas.messages.Messages
import nl.chrisb.sibas.messages.Reactions
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class LeaderboardExtension : Extension() {
    override val name = "leaderboard"

    override suspend fun setup() {
        publicSlashCommand {
            name = "leaderboard"
            description = "Show various interesting leaderboards."

            check { anyGuild() }

            group("messages") {
                description = "Show various leaderboards about amount of messages."

                publicSubCommand {
                    name = "channel"
                    description = "Show which channels have the most messages."

                    action {
                        respond {
                            embed {
                                title = "Channels with most messages"

                                transaction {
                                    description = Messages
                                        .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                        .slice(Messages.channel, Messages.id.count())
                                        .select { Channels.guild eq guild!!.longId }
                                        .groupBy(Messages.channel)
                                        .orderBy(Messages.id.count(), SortOrder.DESC)
                                        .limit(30)
                                        .joinToString("\n") {
                                            "<#${it[Messages.channel].value}>: ${it[Messages.id.count()].format()}"
                                        }.ifEmpty {
                                            "This server does not have any indexed messages yet."
                                        }
                                }
                            }
                        }
                    }
                }

                publicSubCommand {
                    name = "user"
                    description = "Show which users have the most messages."

                    action {
                        respond {
                            embed {
                                title = "Users with most messages"

                                transaction {
                                    description = Messages
                                        .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                        .slice(Messages.user, Messages.id.count())
                                        .select { Channels.guild eq guild!!.longId }
                                        .groupBy(Messages.user)
                                        .orderBy(Messages.id.count(), SortOrder.DESC)
                                        .limit(30)
                                        .joinToString("\n") {
                                            "<@${it[Messages.user]}>: ${it[Messages.id.count()].format()}"
                                        }.ifEmpty {
                                            "This server does not have any indexed messages yet."
                                        }
                                }
                            }
                        }
                    }
                }
            }

            publicSubCommand {
                name = "reactions"
                description = "Show which reactions have been most reacted with."

                action {
                    respond {
                        embed {
                            title = "Most used reactions"

                            transaction {
                                description = Reactions
                                    .join(Messages, JoinType.INNER) { Messages.id eq Reactions.message }
                                    .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                    .slice(Reactions.emote, Reactions.name, Reactions.count.sum())
                                    .select { Channels.guild eq guild!!.longId }
                                    .groupBy(Reactions.emote, Reactions.name)
                                    .orderBy(Reactions.count.sum(), SortOrder.DESC_NULLS_LAST)
                                    .limit(30)
                                    .joinToString("\n") { row ->
                                        val name = row[Reactions.name]
                                        val emote = row[Reactions.emote]
                                        val count = row[Reactions.count.sum()] ?: 0

                                        val emoji = emote
                                            ?.let { ReactionEmoji.Custom(Snowflake(it), name, false) }
                                            ?: ReactionEmoji.Unicode(name)

                                        if (emoji is ReactionEmoji.Unicode) {
                                            "${emoji.mention}: ${count.format()}"
                                        } else {
                                            "${emoji.mention} (${emoji.name}): ${count.format()}"
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}
