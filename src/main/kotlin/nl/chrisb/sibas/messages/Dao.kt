package nl.chrisb.sibas.messages

import dev.kord.core.entity.ReactionEmoji
import nl.chrisb.sibas.toLong
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

class Channel(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Channel>(Channels)

    var guild by Channels.guild
    var lastUpdatedMessageId by Channels.lastUpdatedMessage

    val messages by Message referrersOn Messages.channel
}

class Message(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Message>(Messages)

    var channel by Channel referencedOn Messages.channel
    var user by Messages.user
    var contents by Messages.contents
    var timestamp by Messages.timestamp

    val reactions by Reaction referrersOn Reactions.message
}

class Reaction(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Reaction>(Reactions)

    var message by Message referencedOn Reactions.message
    var emote by Reactions.emote
    var name by Reactions.name
    var count by Reactions.count
}

fun IntEntityClass<Reaction>.findByEmoji(messageId: Long, emoji: ReactionEmoji) = find {
    (Reactions.message eq EntityID(messageId, Messages)) and
            (Reactions.name eq emoji.name) and
            (Reactions.emote eq (emoji as? ReactionEmoji.Custom)?.id?.toLong())
}.firstOrNull()
