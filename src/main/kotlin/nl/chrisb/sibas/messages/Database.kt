package nl.chrisb.sibas.messages

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Channels : LongIdTable() {
    val guild = long("guild")
    val lastUpdate = timestamp("last_update")
}

object Messages : LongIdTable() {
    val channel = reference("channel", Channels)
    val user = long("user")
    val contents = text("contents")
}

object Reactions : IntIdTable() {
    val message = reference("message", Messages)
    val emote = long("emote")
    val name = text("name")
    val count = integer("count")
}
