package nl.chrisb.sibas.messages

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toJavaInstant
import mu.KotlinLogging
import nl.chrisb.sibas.chunked
import nl.chrisb.sibas.longId
import nl.chrisb.sibas.toLong
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.concurrent.fixedRateTimer

private val locks = mutableMapOf<Long, Mutex>()
private val scheduledToIndex = mutableSetOf<TextChannel>()

val logger = KotlinLogging.logger { }

fun startCheckingForIndex() {
    fixedRateTimer("Periodic indexer", period = 1000 * 60) {
        runBlocking {
            val channels = transaction {
                scheduledToIndex.filter { Channel.findById(it.longId) != null }
            }

            if (channels.isNotEmpty()) {
                val total = channels.sumOf { index(it) }
                logger.debug { "Periodically indexed ${channels.joinToString { "#${it.name}" }}. ($total new messages)" }
            }

            scheduledToIndex.clear()
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

    var messageCount = 0

    lock.withLock {
        val storedChannel = transaction {
            Channel.findById(textChannel.longId)
                ?: Channel.new(textChannel.longId) {
                    guild = textChannel.guildId.toLong()
                }
        }

        val messageFlow = storedChannel.lastUpdatedMessage
            ?.let { textChannel.getMessagesAfter(Snowflake(it)) }
            ?: textChannel.messages

        messageFlow
            .chunked(chunkSize)
            .collect { messages ->
                messageCount += messages.size

                transaction {
                    messages.forEach {
                        val storedMessage = Message.new(it.longId) {
                            channel = storedChannel
                            user = it.author?.longId ?: it.webhookId?.toLong()
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

                    storedChannel.lastUpdatedMessage = messages.maxByOrNull { it.timestamp }!!.longId
                }

                progressCallback(messageCount)
            }

        return messageCount
    }
}
