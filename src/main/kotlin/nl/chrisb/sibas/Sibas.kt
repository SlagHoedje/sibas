package nl.chrisb.sibas

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.Snowflake

suspend fun main() {
    val bot = ExtensibleBot(env("TOKEN")) {
        presence {
            competing("Sibas vs Selmon")
        }

        applicationCommands {
            defaultGuild = Snowflake(540214510833893377L)
        }

        extensions {
            add(::PingExtension)
        }
    }

    bot.start()
}
