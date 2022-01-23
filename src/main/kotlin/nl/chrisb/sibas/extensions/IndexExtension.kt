package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.edit
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.*
import kotlinx.coroutines.flow.mapNotNull
import mu.KotlinLogging
import nl.chrisb.sibas.isAdmin
import nl.chrisb.sibas.messages.*
import nl.chrisb.sibas.toLong
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

val iLogger = KotlinLogging.logger { }

class IndexExtension : Extension() {
    override val name = "index"

    override suspend fun setup() {
        startCheckingForIndex()
        registerIndexEvents()

        publicSlashCommand {
            name = "index"
            description = "Index all new messages since the last index"

            check { isAdmin() }

            initialResponse { content = "Indexing all channels..." }

            action {
                iLogger.info { "Manually indexing all channels..." }

                guild?.let { g ->
                    val textChannels = g.channels.mapNotNull { it as? TextChannel }
                    var total = 0

                    textChannels.collect { textChannel ->
                        if (!textChannel.botHasPermissions(Permission.ReadMessageHistory, Permission.ViewChannel)) {
                            return@collect
                        }

                        edit { content = "Indexing ${textChannel.mention}... _(0 messages so far, $total total)_" }

                        total += index(textChannel, chunkSize = 500) {
                            edit {
                                content =
                                    "Indexing ${textChannel.mention}... _($it messages so far, ${total + it} total)_"
                            }
                        }
                    }

                    edit { content = "Finished indexing all channels. _($total new messages indexed)_" }
                } ?: throw Exception("You can't index here, as this is not a guild.")
            }
        }
    }

    private suspend fun registerIndexEvents() {
        event<MessageCreateEvent> {
            check { channelType(ChannelType.GuildText) }
            action {
                scheduleForIndex(event.message.getChannel() as TextChannel)
            }
        }

        event<MessageDeleteEvent> {
            action {
                val event = event
                transaction {
                    Message.findById(event.messageId.toLong())?.delete()
                }
            }
        }

        event<MessageBulkDeleteEvent> {
            action {
                val event = event
                transaction {
                    event.messageIds.forEach {
                        Message.findById(it.toLong())?.delete()
                    }
                }
            }
        }

        event<MessageUpdateEvent> {
            action {
                val event = event
                newSuspendedTransaction {
                    Message.findById(event.messageId.toLong())?.let {
                        it.contents = event.message.asMessage().content
                    }
                }
            }
        }

        event<ReactionAddEvent> {
            action {
                val event = event
                transaction {
                    Reaction.findByEmoji(event.messageId.toLong(), event.emoji)
                        ?.let { it.count += 1 }
                        ?: Message.findById(event.messageId.toLong())?.let {
                            Reaction.new {
                                message = it
                                emote = (event.emoji as? ReactionEmoji.Custom)?.id?.toLong()
                                name = event.emoji.name
                                count = 1
                            }
                        }
                }
            }
        }

        event<ReactionRemoveEvent> {
            action {
                val event = event
                transaction {
                    Reaction.findByEmoji(event.messageId.toLong(), event.emoji)
                        ?.let {
                            if (it.count == 1) {
                                it.delete()
                            } else {
                                it.count -= 1
                            }
                        }
                }
            }
        }

        event<ReactionRemoveAllEvent> {
            action {
                val event = event
                transaction {
                    Reaction.find { Reactions.message eq EntityID(event.messageId.toLong(), Messages) }
                        .forEach { it.delete() }
                }
            }
        }

        event<ReactionRemoveEmojiEvent> {
            action {
                val event = event
                transaction {
                    Reaction.findByEmoji(event.messageId.toLong(), event.emoji)?.delete()
                }
            }
        }
    }
}
