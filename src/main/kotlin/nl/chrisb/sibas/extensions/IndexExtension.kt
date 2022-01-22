package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.edit
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import dev.kord.common.entity.Permission
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.mapNotNull
import nl.chrisb.sibas.messages.index

class IndexExtension : Extension() {
    override val name = "index"

    override suspend fun setup() {
        publicSlashCommand {
            name = "index"
            description = "Index all new messages since the last index"

            initialResponse { content = "Indexing all channels..." }

            action {
                guild?.let { g ->
                    val textChannels = g.channels.mapNotNull { it as? TextChannel }
                    var total = 0

                    textChannels.collect { textChannel ->
                        if (!textChannel.botHasPermissions(Permission.ReadMessageHistory, Permission.ViewChannel)) {
                            return@collect
                        }

                        edit { content = "Indexing ${textChannel.mention}... _(0 messages so far, $total total)_" }

                        total += index(textChannel, chunkSize = 500) {
                            edit {
                                content =
                                    "Indexing ${textChannel.mention}... _($it messages so far, ${total + it} total)_"
                            }
                        }
                    }

                    edit { content = "Finished indexing all channels. _($total new messages indexed)_" }
                } ?: throw Exception("You can't index here, as this is not a guild.")
            }
        }
    }
}
