package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.createdAt
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.ReactionEmoji
import dev.kord.rest.builder.message.create.embed
import kotlinx.datetime.toJavaInstant
import nl.chrisb.sibas.format
import nl.chrisb.sibas.longId
import nl.chrisb.sibas.messages.Channels
import nl.chrisb.sibas.messages.Messages
import nl.chrisb.sibas.messages.Reactions
import nl.chrisb.sibas.possessive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

class ProfileExtension : Extension() {
    override val name = "profile"

    override suspend fun setup() {
        publicSlashCommand(::ProfileArgs) {
            name = "profile"
            description = "View all kinds of interesting statistics about someone."

            check { anyGuild() }

            action {
                val member = (arguments.target ?: user.asUser()).asMember(guild!!.id)

                respond {
                    embed {
                        title = "${member.username.possessive()} profile"

                        thumbnail {
                            url = (member.avatar ?: member.defaultAvatar).url
                        }

                        member.premiumSince?.let {
                            val duration = Duration.between(it.toJavaInstant(), Instant.now())
                            description = "been wasting their money for ${duration.toDays()} days"
                        }

                        transaction {
                            field("Statistics") {
                                val messages = Messages
                                    .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                    .select { (Messages.user eq member.longId) and (Channels.guild eq guild!!.longId) }
                                    .count()
                                val reactions = Messages
                                    .join(Reactions, JoinType.INNER) { Messages.id eq Reactions.message }
                                    .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                    .select { (Messages.user eq member.longId) and (Channels.guild eq guild!!.longId) }
                                    .count()

                                """
                                    Account created: <t:${member.createdAt.epochSeconds}:D>
                                    Joined server: <t:${member.joinedAt.epochSeconds}:D>
                                    Total amount of messages: ${messages.format()}
                                    Reactions received: ${reactions.format()}
                                """.trimIndent()
                            }

                            field("Reactions received") {
                                Messages
                                    .join(Reactions, JoinType.INNER) { Messages.id eq Reactions.message }
                                    .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                    .slice(Reactions.emote, Reactions.name, Reactions.count.sum())
                                    .select { (Messages.user eq member.longId) and (Channels.guild eq guild!!.longId) }
                                    .groupBy(Reactions.emote, Reactions.name)
                                    .orderBy(Reactions.count.sum(), SortOrder.DESC_NULLS_LAST)
                                    .limit(15)
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
                                    }.ifEmpty {
                                        "${member.displayName} has not received any reactions yet."
                                    }
                            }

                            field("Messages sent") {
                                Messages
                                    .join(Channels, JoinType.INNER) { Messages.channel eq Channels.id }
                                    .slice(Messages.channel, Messages.id.count())
                                    .select { (Messages.user eq member.longId) and (Channels.guild eq guild!!.longId) }
                                    .groupBy(Messages.channel)
                                    .orderBy(Messages.id.count(), SortOrder.DESC)
                                    .limit(15)
                                    .joinToString("\n") {
                                        // TODO: Is there some sort of library function that can mention this for me?
                                        "<#${it[Messages.channel].value}>: ${it[Messages.id.count()].format()}"
                                    }.ifEmpty {
                                        "${member.displayName} has not sent any messages in any indexed channel yet."
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    inner class ProfileArgs : Arguments() {
        val target by optionalUser("target", "The person you want to check the profile of.")
    }
}
