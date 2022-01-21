package nl.chrisb.sibas

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.Snowflake
import nl.chrisb.sibas.extensions.PingExtension

suspend fun main() {
    val bot = ExtensibleBot(env("TOKEN")) {
        presence {
            competing("Sibas vs Selmon")
        }

        applicationCommands {
            defaultGuild = Snowflake(540214510833893377L)
        }

        errorResponse { _, type ->
            content = "**Error:** ${type.error.message ?: "Something happened."}"
        }

        extensions {
            add(::PingExtension)
            add(::IndexExtension)
        }
    }

    bot.start()
}
