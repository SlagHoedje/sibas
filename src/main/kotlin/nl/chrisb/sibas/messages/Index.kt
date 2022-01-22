package nl.chrisb.sibas.messages

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toJavaInstant
import nl.chrisb.sibas.chunked
import nl.chrisb.sibas.longId
import nl.chrisb.sibas.toLong
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.concurrent.fixedRateTimer

private val locks = mutableMapOf<Long, Mutex>()
private val scheduledToIndex = mutableSetOf<TextChannel>()

fun startCheckingForIndex() {
    fixedRateTimer("Periodic indexer", period = 1000 * 60) {
        runBlocking {
            if (scheduledToIndex.isNotEmpty()) {
                val total = scheduledToIndex.sumOf { index(it) }
                println("Periodically indexed ${scheduledToIndex.joinToString { "#${it.name}" }}. ($total new messages)")
                scheduledToIndex.clear()
            }
        }
    }
}

fun scheduleForIndex(textChannel: TextChannel) {
    scheduledToIndex.add(textChannel)
}

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
                    guild = textChannel.guildId.toLong()
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
                                emote = it.id?.toLong()
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
