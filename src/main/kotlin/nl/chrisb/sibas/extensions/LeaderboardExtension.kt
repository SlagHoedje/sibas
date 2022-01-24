package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.rest.builder.message.create.embed
import nl.chrisb.sibas.format
import nl.chrisb.sibas.longId
import nl.chrisb.sibas.messages.Channels
import nl.chrisb.sibas.messages.Messages
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class LeaderboardExtension : Extension() {
    override val name = "leaderboard"

    override suspend fun setup() {
        publicSlashCommand {
            name = "leaderboard"
            description = "Show various interesting leaderboards."

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
        }
    }
}
