package nl.chrisb.sibas.messages

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Channels : LongIdTable() {
    val guild = long("guild")
    val lastUpdatedMessage = long("last_updated_message").nullable()
}

object Messages : LongIdTable() {
    val channel = reference("channel", Channels)
    val user = long("user").nullable()
    val contents = text("contents")
    val timestamp = timestamp("timestamp")
}

object Reactions : IntIdTable() {
    val message = reference("message", Messages)
    val emote = long("emote").nullable()
    val name = text("name")
    val count = integer("count")
}
