package nl.chrisb.sibas

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.supplier.EntitySupplier
import dev.kord.core.supplier.EntitySupplyStrategy
import mu.KotlinLogging
import nl.chrisb.sibas.extensions.IndexExtension
import nl.chrisb.sibas.extensions.PingExtension
import nl.chrisb.sibas.extensions.StatsExtension
import nl.chrisb.sibas.messages.Channels
import nl.chrisb.sibas.messages.Messages
import nl.chrisb.sibas.messages.Reactions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

val logger = KotlinLogging.logger { }

suspend fun main() {
    Database.connect(
        env("DB_URL"),
        driver = env("DB_DRIVER"),
        user = envOrNull("DB_USER") ?: "",
        password = envOrNull("DB_PASS") ?: ""
    )

    transaction {
        SchemaUtils.create(Channels, Messages, Reactions)
    }

    val bot = ExtensibleBot(env("TOKEN")) {
        cache {
            @Suppress("UNCHECKED_CAST")
            defaultStrategy = EntitySupplyStrategy.rest as EntitySupplyStrategy<EntitySupplier>
        }

        presence {
            competing("Sibas vs Selmon")
        }

        applicationCommands {
            envOrNull("DEFAULT_GUILD_ID")?.let {
                defaultGuild = Snowflake(it.toLong())
            }
        }

        errorResponse { message, type ->
            content = if (message.startsWith("**Error:**")) {
                message
            } else {
                type.error.printStackTrace()
                "**Error:** ${type.error.message ?: "Something happened."}"
            }
        }

        extensions {
            add(::PingExtension)
            add(::IndexExtension)
            add(::StatsExtension)
        }
    }

    bot.on<ReadyEvent> {
        logger.info { "Connected to Discord, ready to respond" }
    }

    logger.info { "Starting the Sibas bot..." }
    bot.start()
}
