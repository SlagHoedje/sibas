package nl.chrisb.sibas.messages

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toJavaInstant
import nl.chrisb.sibas.chunked
import nl.chrisb.sibas.longId
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction

val locks = mutableMapOf<Long, Mutex>()

suspend fun index(
    textChannel: TextChannel,
    chunkSize: Int = 500,
    progressCallback: suspend (count: Int) -> Unit = {}
): Int {
    val lock = locks.getOrPut(textChannel.longId) { Mutex() }

    if (lock.isLocked) {
        return 0
    }

    lock.withLock {
        val storedChannel = transaction {
            Channel.findById(textChannel.longId)
                ?: Channel.new(textChannel.longId) {
                    guild = textChannel.guildId.value.toLong()
                }
        }

        val messageFlow = storedChannel.lastUpdatedMessageId
            ?.let { textChannel.getMessagesAfter(Snowflake(it.value)) }
            ?: textChannel.messages
        var messageCount = 0

        messageFlow
            .chunked(chunkSize)
            .collect { messages ->
                messageCount += messages.size

                transaction {
                    messages.forEach {
                        val storedMessage = Message.new(it.longId) {
                            channel = storedChannel
                            user = it.author?.longId
                            contents = it.content
                            timestamp = it.timestamp.toJavaInstant()
                        }

                        it.reactions.forEach {
                            Reaction.new {
                                message = storedMessage
                                emote = it.id?.value?.toLong()
                                name = it.emoji.name
                                count = it.count
                            }
                        }
                    }

                    storedChannel.lastUpdatedMessageId =
                        EntityID(messages.maxByOrNull { it.timestamp }!!.longId, Messages)
                }

                progressCallback(messageCount)
            }

        return messageCount
    }
}
