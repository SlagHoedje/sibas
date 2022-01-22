package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.toJavaInstant
import nl.chrisb.sibas.chunked
import nl.chrisb.sibas.longId
import nl.chrisb.sibas.messages.Channel
import nl.chrisb.sibas.messages.Message
import nl.chrisb.sibas.messages.Messages
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction

class IndexExtension : Extension() {
    override val name = "index"

    override suspend fun setup() {
        publicSlashCommand {
            name = "index"
            description = "Index all new messages since the last index"

            action {
                guild?.let { g ->
                    val textChannels = g.channels.mapNotNull { it as? TextChannel }
                    var total = 0

                    textChannels.collect { textChannel ->
                        if (!textChannel.botHasPermissions(Permission.ReadMessageHistory, Permission.ViewChannel)) {
                            return@collect
                        }

                        val storedChannel = transaction {
                            Channel.findById(textChannel.longId)
                                ?: Channel.new(textChannel.longId) {
                                    guild = g.longId
                                }
                        }

                        val messageFlow = storedChannel.lastUpdatedMessageId
                            ?.let { textChannel.getMessagesAfter(Snowflake(it.value)) }
                            ?: textChannel.messages
                        var count = 0

                        messageFlow
                            .chunked(100)
                            .collect { messages ->
                                count += messages.size

                                transaction {
                                    messages.forEach {
                                        Message.new(it.longId) {
                                            channel = storedChannel
                                            user = it.author?.longId ?: -1
                                            contents = it.content
                                            timestamp = it.timestamp.toJavaInstant()
                                        }
                                    }

                                    storedChannel.lastUpdatedMessageId = EntityID(messages.last().longId, Messages)
                                }
                            }

                        total += count
                    }

                    println("total=$total, sql_total=${transaction { Message.count() }}")
                } ?: throw Exception("You can't index here, as this is not a guild.")
            }
        }
    }
}
