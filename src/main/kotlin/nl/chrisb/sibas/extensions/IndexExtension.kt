package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import dev.kord.common.entity.Permission
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.mapNotNull
import nl.chrisb.sibas.messages.Message
import nl.chrisb.sibas.messages.index
import org.jetbrains.exposed.sql.transactions.transaction

class IndexExtension : Extension() {
    override val name = "index"

    override suspend fun setup() {
        publicSlashCommand {
            name = "index"
            description = "Index all new messages since the last index"

            action {
                guild?.let { g ->
                    val textChannels = g.channels.mapNotNull { it as? TextChannel }
                    var total = 0

                    textChannels.collect { textChannel ->
                        if (!textChannel.botHasPermissions(Permission.ReadMessageHistory, Permission.ViewChannel)) {
                            return@collect
                        }

                        total += index(textChannel)
                    }

                    println("total=$total, sql_total=${transaction { Message.count() }}")
                } ?: throw Exception("You can't index here, as this is not a guild.")
            }
        }
    }
}
