package nl.chrisb.sibas

import dev.minn.jda.ktx.await
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.time.delay
import net.dv8tion.jda.api.entities.*
import org.intellij.lang.annotations.Language
import java.sql.Timestamp
import java.time.*

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(DelicateCoroutinesApi::class)
object Messages {
    private val locks = mutableMapOf<Long, Mutex>()
    private val toUpdate = mutableListOf<MessageChannel>()

    init {
        initDB()

        GlobalScope.launch {
            while (true) {
                delay(Duration.ofMinutes(5))

                val channels = toUpdate.toList()
                toUpdate.clear()

                var count = 0
                channels.forEach {
                    count += index(it)
                }

                if (count != 0) {
                    println("Periodically indexed ${channels.size} channel(s) with $count messages.")
                }
            }
        }
    }

    private fun initDB() {
        Database.poolConnection().use { connection ->
            connection.prepare(
                "CREATE TABLE IF NOT EXISTS messages(" +
                        "id BIGINT NOT NULL, " +
                        "author BIGINT NOT NULL, " +
                        "channel BIGINT NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL, " +
                        "contents TEXT, " +
                        "PRIMARY KEY (id)" +
                        ")"
            ).exec()

            connection.prepare(
                "CREATE TABLE IF NOT EXISTS reactions(" +
                        "message BIGINT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "id BIGINT NOT NULL, " +
                        "count INT NOT NULL, " +
                        "PRIMARY KEY (message, id)" +
                        ")"
            ).exec()

            connection.prepare(
                "CREATE TABLE IF NOT EXISTS last_update(" +
                        "channel BIGINT NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL, " +
                        "PRIMARY KEY (channel)" +
                        ")"
            ).exec()
        }
    }

    fun clearDB() {
        Database.poolConnection().use { connection ->
            connection.prepare(
                "DROP TABLE IF EXISTS messages;" +
                        "DROP TABLE IF EXISTS reactions;" +
                        "DROP TABLE IF EXISTS last_update;"
            ).exec()
        }

        initDB()
    }

    fun scheduleIndex(channel: MessageChannel) {
        toUpdate.add(channel)
    }

    private fun insertMessages(messages: List<StoredMessage>) {
        Database.poolConnection().use { connection ->
            val statement = connection
                .prepare("INSERT INTO messages VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING")
                .batched()

            for (message in messages) {
                statement
                    .args(message.id, message.author, message.channel, message.timestamp, message.contents)
                    .batch()
            }

            statement.exec()
        }
    }

    private fun insertReactions(reactions: List<StoredReaction>) {
        Database.poolConnection().use { connection ->
            val statement = connection
                .prepare("INSERT INTO reactions VALUES (?, ?, ?, ?) ON CONFLICT (message, id) DO NOTHING")
                .batched()

            for (reaction in reactions) {
                statement
                    .args(reaction.message, reaction.name, reaction.id ?: -1, reaction.count)
                    .batch()
            }

            statement.exec()
        }
    }

    suspend fun index(
        channel: MessageChannel,
        progressCallback: ((blocked: Boolean, count: Int) -> Unit)? = null
    ): Int {
        val lock = locks.getOrPut(channel.idLong) { Mutex() }

        if (!lock.tryLock()) {
            progressCallback?.invoke(true, 0)

            lock.lock()
            lock.unlock()

            return 0
        }

        try {
            progressCallback?.invoke(false, 0)

            var count = 0
            val messages = mutableListOf<StoredMessage>()
            val reactions = mutableListOf<StoredReaction>()

            val limit = lastIndexTimestamp(channel).toInstant()
            val now = Instant.now(Clock.systemUTC())

            var history = listOf(
                channel.retrieveMessageById(channel.latestMessageId).await(),
                *channel.getHistoryBefore(channel.latestMessageId, 100).await().retrievedHistory.toTypedArray()
            )

            while (true) {
                for (message in history) {
                    val timeUTC = message.timeCreated.atZoneSimilarLocal(ZoneId.of("UTC")).toInstant()

                    if (timeUTC.isAfter(now)) {
                        continue
                    }

                    if (!timeUTC.isAfter(limit)) {
                        history = listOf()
                        break
                    }

                    messages.add(message.toStoredMessage())
                    reactions.addAll(message.reactions.map { it.toStoredReaction() })

                    if (messages.size >= 500) {
                        count += messages.size

                        insertMessages(messages)
                        insertReactions(reactions)
                        messages.clear()
                        reactions.clear()

                        progressCallback?.invoke(false, count)
                    }
                }

                if (history.size < 100) {
                    count += messages.size

                    insertMessages(messages)
                    insertReactions(reactions)

                    setLastIndexTimestamp(channel, now.atOffset(ZoneOffset.UTC))

                    lock.unlock()

                    return count
                }

                history = channel.getHistoryBefore(history.last().id, 100).await().retrievedHistory
            }
        } catch (e: Throwable) {
            println("Error while indexing #${channel.name}: ${e.message}")
            lock.unlock()
            return 0
        }
    }

    fun stats(): Stats {
        Database.poolConnection().use { connection ->
            val messages = connection.prepare("SELECT COUNT(*) FROM messages").exec().firstOrNull()?.getInt(1) ?: -1
            val reactions = connection.prepare("SELECT SUM(count) FROM reactions").exec().firstOrNull()?.getInt(1) ?: -1

            return Stats(messages, reactions)
        }
    }

    private fun setLastIndexTimestamp(channel: MessageChannel, time: OffsetDateTime) {
        Database.poolConnection().use { connection ->
            val timestamp = Timestamp.valueOf(time.atZoneSimilarLocal(ZoneId.of("UTC")).toLocalDateTime())

            connection
                .prepare("INSERT INTO last_update VALUES (?, ?) ON CONFLICT (channel) DO UPDATE SET timestamp = ?")
                .args(channel.idLong, timestamp, timestamp)
                .exec()
        }
    }

    private fun lastIndexTimestamp(channel: MessageChannel): OffsetDateTime =
        Database.poolConnection().use { connection ->
            connection.prepare("SELECT timestamp FROM last_update WHERE channel = ?")
                .args(channel.idLong)
                .exec()
                .firstOrNull()
                ?.getTimestamp(1)
                ?.toLocalDateTime()
                ?.atOffset(ZoneOffset.UTC)
                ?: OffsetDateTime.MIN
        }

    private fun getLeaderboard(@Language("SQL") sql: String): List<Pair<Long, Int>> =
        Database.poolConnection().use { connection ->
            connection.prepare(sql)
                .exec()
                .map { it.getLong(1) to it.getInt(2) }
                .toList()
        }


    private fun getReactionLeaderboard(@Language("SQL") sql: String, reaction: String): List<Pair<Long, Int>> =
        Database.poolConnection().use { connection ->
            connection.prepare(sql)
                .args(reaction)
                .exec()
                .map { it.getLong(1) to it.getInt(2) }
                .toList()
        }

    fun channelMessagesLeaderboard() =
        getLeaderboard("SELECT channel, COUNT(*) as count FROM messages GROUP BY channel ORDER BY count DESC")

    fun channelReactionsLeaderboard(reaction: String) =
        getReactionLeaderboard(
            "SELECT m.channel, SUM(r.count) AS count " +
                    "FROM messages m, reactions r " +
                    "WHERE m.id = r.message " +
                    "  AND r.name = ? " +
                    "GROUP BY m.channel " +
                    "ORDER BY count DESC " +
                    "LIMIT 30", reaction
        )

    fun userMessagesLeaderboard() =
        getLeaderboard("SELECT author, COUNT(*) as count FROM messages GROUP BY author ORDER BY count DESC LIMIT 30")

    fun userReactionLeaderboard(reaction: String) =
        getReactionLeaderboard(
            "SELECT m.author, SUM(r.count) AS count\n" +
                    "FROM messages m, reactions r\n" +
                    "WHERE m.id = r.message\n" +
                    "  AND r.name = ?\n" +
                    "GROUP BY m.author\n" +
                    "ORDER BY count DESC\n" +
                    "LIMIT 30\n", reaction
        )

    fun userReactionMessageRatioLeaderboard(reaction: String): List<Pair<Long, Float>> =
        Database.poolConnection().use { connection ->
            connection.prepare(
                "SELECT messages.author, (CAST(reactions.count AS FLOAT) / CAST(messages.count AS FLOAT)) AS ratio\n" +
                        "FROM (SELECT m.author, COUNT(m.id) AS count\n" +
                        "      FROM messages m\n" +
                        "      GROUP BY m.author) AS messages,\n" +
                        "     (SELECT m.author, SUM(r.count) AS count\n" +
                        "      FROM messages m, reactions r\n" +
                        "      WHERE m.id = r.message\n" +
                        "        AND r.name = ?\n" +
                        "      GROUP BY m.author) AS reactions\n" +
                        "WHERE messages.author = reactions.author\n" +
                        "  AND messages.count >= 100\n" +
                        "ORDER BY ratio DESC\n" +
                        "LIMIT 20;"
            )
                .args(reaction)
                .exec()
                .map { it.getLong(1) to it.getFloat(2) }
                .toList()
        }

    fun userUpvoteDownvoteRatioLeaderboard(): List<Pair<Long, Float>> =
        Database.poolConnection().use { connection ->
            connection.prepare(
                "SELECT upvotes.author, (CAST(upvotes.count AS FLOAT) / CAST(downvotes.count AS FLOAT)) AS ratio\n" +
                        "FROM (SELECT m.author, SUM(r.count) AS count\n" +
                        "      FROM messages m, reactions r\n" +
                        "      WHERE m.id = r.message\n" +
                        "        AND r.name = 'upvote'\n" +
                        "      GROUP BY m.author) AS upvotes,\n" +
                        "     (SELECT m.author, SUM(r.count) AS count\n" +
                        "      FROM messages m, reactions r\n" +
                        "      WHERE m.id = r.message\n" +
                        "        AND r.name = 'downvote'\n" +
                        "      GROUP BY m.author) AS downvotes\n" +
                        "WHERE upvotes.author = downvotes.author\n" +
                        "ORDER BY ratio DESC\n" +
                        "LIMIT 20;"
            )
                .exec()
                .map { it.getLong(1) to it.getFloat(2) }
                .toList()
        }

    fun messageReactionLeaderboard(reaction: String, channel: MessageChannel? = null): List<Pair<StoredMessage, Int>> =
        Database.poolConnection().use { connection ->
            val statement = if (channel != null) {
                connection.prepare(
                    "SELECT m.*, MAX(r.count) as count\n" +
                            "FROM messages m,\n" +
                            "     reactions r\n" +
                            "WHERE m.id = r.message\n" +
                            "  AND r.name = ?\n" +
                            "  AND m.channel = ?\n" +
                            "GROUP BY m.id\n" +
                            "ORDER BY count DESC\n" +
                            "LIMIT 10;\n"
                )
                    .args(reaction, channel.idLong)
            } else {
                connection.prepare(
                    "SELECT m.*, MAX(r.count) as count\n" +
                            "FROM messages m,\n" +
                            "     reactions r\n" +
                            "WHERE m.id = r.message\n" +
                            "  AND r.name = ?\n" +
                            "GROUP BY m.id\n" +
                            "ORDER BY count DESC\n" +
                            "LIMIT 10;\n"
                )
                    .args(reaction)
            }

            statement
                .exec()
                .map {
                    val message = StoredMessage(
                        it.getLong(1),
                        it.getLong(2),
                        it.getLong(3),
                        it.getTimestamp(4),
                        it.getString(5)
                    )

                    message to it.getInt(6)
                }
                .toList()
        }

    fun updateMessage(message: Message) {
        Database.poolConnection().use { connection ->
            connection.prepare(
                "DELETE FROM messages WHERE id = ?;" +
                        "DELETE FROM reactions WHERE message = ?;"
            )
                .args(message.idLong, message.idLong)
                .exec()
        }

        insertMessages(listOf(message.toStoredMessage()))
        insertReactions(message.reactions.map { it.toStoredReaction() })
    }

    private fun userReactions(user: User): List<Pair<String, Int>> =
        Database.poolConnection().use { connection ->
            connection.prepare(
                "SELECT r.name, r.id, SUM(r.count) AS count\n" +
                        "FROM reactions r,\n" +
                        "     messages m\n" +
                        "WHERE m.author = ?\n" +
                        "AND m.id = r.message\n" +
                        "GROUP BY r.name, r.id\n" +
                        "ORDER BY count DESC\n" +
                        "LIMIT 15;"
            )
                .args(user.idLong)
                .exec()
                .map {
                    val name = it.getString(1)
                    val id = it.getLong(2)
                    val count = it.getInt(3)

                    (if (id == -1L) name else "<:$name:$id>") to count
                }
                .toList()
        }

    private fun userChannelMessages(user: User): List<Pair<Long, Int>> =
        Database.poolConnection().use { connection ->
            connection.prepare(
                "SELECT channel, COUNT(id) AS count\n" +
                        "FROM messages\n" +
                        "WHERE author = ?\n" +
                        "GROUP BY channel\n" +
                        "ORDER BY count DESC\n" +
                        "LIMIT 15;\n"
            )
                .args(user.idLong)
                .exec()
                .map { it.getLong(1) to it.getInt(2) }
                .toList()
        }

    private fun totalUserMessages(user: User) = Database.poolConnection().use { connection ->
        connection.prepare("SELECT COUNT(id) FROM messages WHERE author = ?")
            .args(user.idLong)
            .exec()
            .firstOrNull()
            ?.getInt(1)
            ?: 0
    }

    private fun totalUserReactions(user: User) = Database.poolConnection().use { connection ->
        connection.prepare(
            "SELECT SUM(r.count)\n" +
                    "FROM reactions r,\n" +
                    "     messages m\n" +
                    "WHERE m.author = ?\n" +
                    "AND m.id = r.message\n"
        )
            .args(user.idLong)
            .exec()
            .firstOrNull()
            ?.getInt(1)
            ?: 0
    }

    fun profile(member: Member?, user: User): Profile {
        return Profile(
            user.name,
            user.avatarUrl ?: user.defaultAvatarUrl,
            user.timeCreated,
            member?.timeJoined,
            totalUserMessages(user),
            totalUserReactions(user),
            userReactions(user),
            userChannelMessages(user)
        )
    }
}

data class Profile(
    val name: String,
    val avatar: String,
    val created: OffsetDateTime,
    val joined: OffsetDateTime?,
    val totalMessages: Int,
    val totalReactions: Int,
    val reactions: List<Pair<String, Int>>,
    val channelMessages: List<Pair<Long, Int>>
)

data class Stats(val messages: Int, val reactions: Int)

data class StoredMessage(
    val id: Long,
    val author: Long,
    val channel: Long,
    val timestamp: Timestamp,
    val contents: String?
)

fun Message.toStoredMessage() = StoredMessage(
    idLong,
    author.idLong,
    channel.idLong,
    Timestamp.valueOf(timeCreated.atZoneSimilarLocal(ZoneId.of("UTC")).toLocalDateTime()),
    if (type == MessageType.DEFAULT) contentRaw else null
)

data class StoredReaction(
    val message: Long,
    val name: String,
    val id: Long?,
    val count: Int,
)

fun MessageReaction.toStoredReaction() = StoredReaction(
    messageIdLong,
    reactionEmote.name,
    if (reactionEmote.isEmoji) null else reactionEmote.idLong,
    count
)
