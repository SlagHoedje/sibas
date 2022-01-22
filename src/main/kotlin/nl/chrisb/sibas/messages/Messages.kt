package nl.chrisb.sibas.messages

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import kotlinx.datetime.toJavaInstant
import nl.chrisb.sibas.chunked
import nl.chrisb.sibas.longId
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction

suspend fun index(textChannel: TextChannel): Int {
    val storedChannel = transaction {
        Channel.findById(textChannel.longId)
            ?: Channel.new(textChannel.longId) {
                guild = textChannel.guildId.value.toLong()
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

    return count
}
