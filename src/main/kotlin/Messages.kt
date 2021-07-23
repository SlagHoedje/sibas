import com.zaxxer.hikari.HikariDataSource
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import java.sql.Timestamp
import java.time.*
import java.util.concurrent.CompletableFuture

object Messages {
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
        }
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
                statement.setLong(3, reaction.id)
                statement.setInt(4, reaction.count)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun index(channel: MessageChannel, event: SlashCommandEvent? = null): CompletableFuture<Int> {
        var count = 0
        val messages = mutableListOf<StoredMessage>()
        val reactions = mutableListOf<StoredReaction>()

        val limit = lastIndexTimestamp(channel).toInstant()
        val now = Instant.now(Clock.systemUTC())

        println("Indexing #${channel.name} from $limit to $now...")

        var updating = event == null

        return channel.iterableHistory.forEachRemainingAsync { message ->
            val timeUTC = message.timeCreated.atZoneSimilarLocal(ZoneId.of("UTC")).toInstant()

            if (timeUTC.isAfter(now)) {
                return@forEachRemainingAsync true
            }

            if (!timeUTC.isAfter(limit)) {
                return@forEachRemainingAsync false
            }

            messages.add(message.toStoredMessage())
            reactions.addAll(message.reactions.map { it.toStoredReaction() })

            if (messages.size >= 500) {
                count += messages.size

                insertMessages(messages)
                insertReactions(reactions)
                messages.clear()
                reactions.clear()

                if (!updating) {
                    updating = true
                    event!!.hook.editOriginal("Indexing <#${channel.id}>... _($count messages)_")
                        .queue {
                            updating = false
                        }
                }
            }

            true
        }.thenApply {
            count += messages.size

            insertMessages(messages)
            insertReactions(reactions)

            count
        }
    }

    fun stats(): Stats {
        ds.connection.use { connection ->
            val statement = connection.createStatement()
            statement.execute("SELECT COUNT(*) FROM messages m")

            var results = statement.resultSet
            results.next()
            val messages = results.getInt(1)

            statement.execute("SELECT SUM(count) FROM reactions")

            results = statement.resultSet
            results.next()
            val reactions = results.getInt(1)

            return Stats(messages, reactions)
        }
    }

    fun lastIndexTimestamp(channel: MessageChannel): OffsetDateTime {
        ds.connection.use { connection ->
            val statement = connection.prepareStatement("SELECT MAX(timestamp) FROM messages WHERE channel = ?")
            statement.setLong(1, channel.idLong)
            statement.execute()

            val results = statement.resultSet
            results.next()

            return results.getTimestamp(1)?.toLocalDateTime()?.atOffset(ZoneOffset.UTC)
                ?: OffsetDateTime.MIN
        }
    }

    private fun getLeaderboard(sql: String): List<Pair<Long, Int>> {
        ds.connection.use { connection ->
            val statement = connection.createStatement()
            statement.execute(sql)

            val results = statement.resultSet

            val leaderboard = mutableListOf<Pair<Long, Int>>()
            while (results.next()) {
                leaderboard.add(results.getLong(1) to results.getInt(2))
            }

            return leaderboard
        }
    }

    fun channelMessagesLeaderboard() =
        getLeaderboard("SELECT channel, COUNT(*) as count FROM messages GROUP BY channel ORDER BY count DESC")

    fun userMessagesLeaderboard() =
        getLeaderboard("SELECT author, COUNT(*) as count FROM messages GROUP BY author ORDER BY count DESC LIMIT 30")

    // TODO: This shouldn't rely on the reaction name, rather on a user-set emote.
    fun userUpvoteLeaderboard() =
        getLeaderboard(
            "SELECT m.author, SUM(r.count) AS count\n" +
                    "FROM messages m, reactions r\n" +
                    "WHERE m.id = r.message\n" +
                    "  AND r.name = 'upvote'\n" +
                    "GROUP BY m.author\n" +
                    "ORDER BY count DESC\n" +
                    "LIMIT 30\n"
        )
}

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
    val id: Long,
    val count: Int,
)

fun MessageReaction.toStoredReaction() = StoredReaction(
    messageIdLong,
    reactionEmote.name,
    reactionEmote.idLong,
    count
)
