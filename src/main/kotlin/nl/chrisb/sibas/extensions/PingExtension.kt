package nl.chrisb.sibas.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand

class PingExtension : Extension() {
    override val name = "ping"

    override suspend fun setup() {
        publicSlashCommand {
            name = "ping"
            description = "Ping the bot."

            initialResponse {
                content = "Pong!"
            }

            action {}
        }
    }
}
