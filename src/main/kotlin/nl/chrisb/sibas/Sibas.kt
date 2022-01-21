package nl.chrisb.sibas

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake
import nl.chrisb.sibas.extensions.IndexExtension
import nl.chrisb.sibas.extensions.PingExtension
import nl.chrisb.sibas.messages.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

suspend fun main() {
    Database.connect(
        env("DB_URL"),
        driver = env("DB_DRIVER"),
        user = envOrNull("DB_USER") ?: "",
        password = envOrNull("DB_PASS") ?: ""
    )

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(Channels, Messages, Reactions)
    }

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
