package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.rest.builder.message.create.embed
import nl.chrisb.sibas.messages.Channel
import nl.chrisb.sibas.messages.Message
import nl.chrisb.sibas.messages.Reactions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction

class StatsExtension : Extension() {
    override val name = "stats"

    override suspend fun setup() {
        publicSlashCommand {
            name = "stats"
            description = "Display a bunch of cool bot statistics."

            action {
                respond {
                    embed {
                        title = "Statistics"

                        transaction {
                            field("Indexed channels", true) { Channel.count().toString() }
                            field("Amount of messages", true) { Message.count().toString() }
                            field("Amount of reactions", true) {
                                (Reactions.slice(Reactions.count.sum()).selectAll()
                                    .first()[Reactions.count.sum()] ?: 0).toString()
                            }
                        }
                    }
                }
            }
        }
    }
}
