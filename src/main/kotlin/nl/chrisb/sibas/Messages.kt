package nl.chrisb.sibas

import com.zaxxer.hikari.HikariDataSource
import dev.minn.jda.ktx.await
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.time.delay
import net.dv8tion.jda.api.entities.*
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.*

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(DelicateCoroutinesApi::class)
object Messages {
    private val locks = mutableMapOf<Long, Mutex>()
    private val toUpdate = mutableListOf<MessageChannel>()

    private val ds = HikariDataSource().also {
        it.jdbcUrl = System.getenv("DB")
            ?: throw RuntimeException("Environment variable DB should contain the jdbc database URL")
        it.username = System.getenv("DB_USER")
            ?: throw RuntimeException("Environment variable DB_USER should contain the database username")
        it.password = System.getenv("DB_PASS")
            ?: throw RuntimeException("Environment variable DB_PASS should contain the database password")
        it.maximumPoolSize = 3
    }

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

    fun initDB() {
        ds.connection.use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages(" +
                        "id BIGINT NOT NULL, " +
                        "author BIGINT NOT NULL, " +
                        "channel BIGINT NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL, " +
                        "contents TEXT, " +
                        "PRIMARY KEY (id)" +
                        ")"
            )

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS reactions(" +
                        "message BIGINT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "id BIGINT NOT NULL, " +
                        "count INT NOT NULL, " +
                        "PRIMARY KEY (message, id)" +
                        ")"
            )

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS last_update(" +
                        "channel BIGINT NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL, " +
                        "PRIMARY KEY (channel)" +
                        ")"
            )
        }
    }

    fun clearDB() {
        ds.connection.use { connection ->
            val statement = connection.createStatement()
            statement.execute(
                "DROP TABLE IF EXISTS messages;" +
                        "DROP TABLE IF EXISTS reactions;" +
                        "DROP TABLE IF EXISTS last_update;"
            )
        }

        initDB()
    }

    fun scheduleIndex(channel: MessageChannel) {
        toUpdate.add(channel)
    }

    fun insertMessages(messages: List<StoredMessage>) {
        ds.connection.use { connection ->
            val statement =
                connection.prepareStatement("INSERT INTO messages VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING")
            for (message in messages) {
                statement.setLong(1, message.id)
                statement.setLong(2, message.author)
                statement.setLong(3, message.channel)
                statement.setTimestamp(4, message.timestamp)
                statement.setString(5, message.contents)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun insertReactions(reactions: List<StoredReaction>) {
        ds.connection.use { connection ->
            val statement =
                connection.prepareStatement("INSERT INTO reactions VALUES (?, ?, ?, ?) ON CONFLICT (message, id) DO NOTHING")
            for (reaction in reactions) {
                statement.setLong(1, reaction.message)
                statement.setString(2, reaction.name)
                statement.setLong(3, reaction.id ?: -1)
                statement.setInt(4, reaction.count)
                statement.addBatch()
            }

            statement.executeBatch()
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
        ds.connection.use { connection ->
            val statement = connection.createStatement()

            statement.execute("SELECT COUNT(*) FROM messages m")
            val messages = statement.nextResult()?.getInt(1) ?: -1

            statement.execute("SELECT SUM(count) FROM reactions")
            val reactions = statement.nextResult()?.getInt(1) ?: -1

            return Stats(messages, reactions)
        }
    }

    fun setLastIndexTimestamp(channel: MessageChannel, time: OffsetDateTime) {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement(
                "INSERT INTO last_update VALUES (?, ?) ON CONFLICT (channel) DO UPDATE SET timestamp = ?"
            )

            val timestamp = Timestamp.valueOf(time.atZoneSimilarLocal(ZoneId.of("UTC")).toLocalDateTime())

            statement.setLong(1, channel.idLong)
            statement.setTimestamp(2, timestamp)
            statement.setTimestamp(3, timestamp)

            statement.execute()
        }
    }

    fun lastIndexTimestamp(channel: MessageChannel): OffsetDateTime {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement("SELECT timestamp FROM last_update WHERE channel = ?")
            statement.setLong(1, channel.idLong)
            statement.execute()

            return statement.nextResult()
                ?.getTimestamp(1)
                ?.toLocalDateTime()
                ?.atOffset(ZoneOffset.UTC)
                ?: OffsetDateTime.MIN
        }
    }

    private fun getLeaderboard(sql: String): List<Pair<Long, Int>> {
        ds.connection.use { connection ->
            val statement = connection.createStatement()
            statement.execute(sql)

            val leaderboard = mutableListOf<Pair<Long, Int>>()
            statement.forEachResult {
                leaderboard.add(it.getLong(1) to it.getInt(2))
            }

            return leaderboard
        }
    }

    private fun getReactionLeaderboard(sql: String, reaction: String): List<Pair<Long, Int>> {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement(sql)
            statement.setString(1, reaction)
            statement.execute()

            val leaderboard = mutableListOf<Pair<Long, Int>>()
            statement.forEachResult {
                leaderboard.add(it.getLong(1) to it.getInt(2))
            }

            return leaderboard
        }
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

    fun userReactionMessageRatioLeaderboard(reaction: String): List<Pair<Long, Float>> {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement(
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
                        "ORDER BY ratio DESC\n" +
                        "LIMIT 20;"
            )

            statement.setString(1, reaction)
            statement.execute()

            val leaderboard = mutableListOf<Pair<Long, Float>>()
            statement.forEachResult {
                leaderboard.add(it.getLong(1) to it.getFloat(2))
            }

            return leaderboard
        }
    }

    fun userUpvoteDownvoteRatioLeaderboard(): List<Pair<Long, Float>> {
        ds.connection.use { connection ->
            val statement = connection.createStatement()
            statement.execute(
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

            val leaderboard = mutableListOf<Pair<Long, Float>>()
            statement.forEachResult {
                leaderboard.add(it.getLong(1) to it.getFloat(2))
            }

            return leaderboard
        }
    }

    fun messageReactionLeaderboard(reaction: String, channel: MessageChannel? = null): List<Pair<StoredMessage, Int>> {
        ds.connection.use { connection ->
            val statement = if (channel != null) {
                val statement = connection.prepareStatement(
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

                statement.setString(1, reaction)
                statement.setLong(2, channel.idLong)
                statement.execute()

                statement
            } else {
                val statement = connection.prepareStatement(
                    "SELECT m.*, MAX(r.count) as count\n" +
                            "FROM messages m,\n" +
                            "     reactions r\n" +
                            "WHERE m.id = r.message\n" +
                            "  AND r.name = ?\n" +
                            "GROUP BY m.id\n" +
                            "ORDER BY count DESC\n" +
                            "LIMIT 10;\n"
                )
                statement.setString(1, reaction)
                statement.execute()

                statement
            }

            val leaderboard = mutableListOf<Pair<StoredMessage, Int>>()
            statement.forEachResult {
                val message = StoredMessage(
                    it.getLong(1),
                    it.getLong(2),
                    it.getLong(3),
                    it.getTimestamp(4),
                    it.getString(5)
                )

                leaderboard.add(message to it.getInt(6))
            }

            return leaderboard
        }
    }

    fun updateMessage(message: Message) {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement(
                "DELETE FROM messages WHERE id = ?;" +
                        "DELETE FROM reactions WHERE message = ?;"
            )
            statement.setLong(1, message.idLong)
            statement.setLong(2, message.idLong)
            statement.execute()
        }

        insertMessages(listOf(message.toStoredMessage()))
        insertReactions(message.reactions.map { it.toStoredReaction() })
    }

    private fun userReactions(user: User): List<Pair<String, Int>> {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement(
                "SELECT r.name, r.id, SUM(r.count) AS count\n" +
                        "FROM reactions r,\n" +
                        "     messages m\n" +
                        "WHERE m.author = ?\n" +
                        "AND m.id = r.message\n" +
                        "GROUP BY r.name, r.id\n" +
                        "ORDER BY count DESC\n" +
                        "LIMIT 15;"
            )

            statement.setLong(1, user.idLong)
            statement.execute()

            val leaderboard = mutableListOf<Pair<String, Int>>()
            statement.forEachResult {
                val name = it.getString(1)
                val id = it.getLong(2)
                val count = it.getInt(3)

                leaderboard.add((if (id == -1L) name else "<:$name:$id>") to count)
            }

            return leaderboard
        }
    }

    private fun userChannelMessages(user: User): List<Pair<Long, Int>> {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement(
                "SELECT channel, COUNT(id) AS count\n" +
                        "FROM messages\n" +
                        "WHERE author = ?\n" +
                        "GROUP BY channel\n" +
                        "ORDER BY count DESC\n" +
                        "LIMIT 15;\n"
            )

            statement.setLong(1, user.idLong)
            statement.execute()

            val leaderboard = mutableListOf<Pair<Long, Int>>()
            statement.forEachResult {
                leaderboard.add(it.getLong(1) to it.getInt(2))
            }

            return leaderboard
        }
    }

    fun profile(member: Member?, user: User): Profile {
        return Profile(
            user.name,
            user.avatarUrl ?: user.defaultAvatarUrl,
            user.timeCreated,
            member?.timeJoined,
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

fun Statement.nextResult() = if (!resultSet.next()) {
    null
} else {
    resultSet
}

fun Statement.forEachResult(consumer: (ResultSet) -> Unit) {
    while (resultSet.next()) {
        consumer(resultSet)
    }
}
