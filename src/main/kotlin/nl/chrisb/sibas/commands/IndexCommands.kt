package nl.chrisb.sibas.commands

import dev.minn.jda.ktx.Embed
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.MessageChannel
import nl.chrisb.sibas.Messages

@Command(name = "index", description = "Index messages of a channel")
object IndexCommand {
    @Executor
    suspend fun executor(hook: CommandHook, @Description("The channel to index") channel: GuildChannel) {
        val messageChannel = channel as? MessageChannel ?: hook.fail("That's not a message channel!")
        hook.message("Indexing <#${messageChannel.id}>...")

        val count = Messages.index(messageChannel) { blocked, count ->
            if (blocked) {
                hook.message("Indexing <#${messageChannel.id}>... _(waiting for another thread to finish)_")
            } else {
                hook.message("Indexing <#${messageChannel.id}>... _($count messages)_")
            }
        }

        hook.message("**DONE!** Indexed <#${messageChannel.id}>. _($count messages)_")
    }
}

@Command(name = "indexall", description = "Index messages of all channels")
object IndexAllCommand {
    @Executor
    suspend fun executor(hook: CommandHook) {
        val count = hook.preIndex()
        hook.message("**DONE!** Indexed all channels. _($count messages)_")
    }
}

@Command(name = "reindexall", description = "Clear all data from the database and reindex all messages")
object ReindexAllCommand {
    @Executor
    suspend fun executor(hook: CommandHook) {
        hook.checkAdmin()

        hook.message("Clearing the database...")
        Messages.clearDB()

        val count = hook.preIndex()
        hook.message("**DONE!** Indexed all channels. _($count messages)_")
    }
}

@Command(name = "cleardb", description = "Clear all data from the database")
object ClearDbCommand {
    @Executor
    fun executor(hook: CommandHook) {
        hook.checkAdmin()

        hook.message("Clearing the database...")
        Messages.clearDB()
        hook.message("**DONE!** Cleared the database.")
    }
}

@Command(name = "stats")
object StatsCommand {
    @Executor
    fun executor(hook: CommandHook) {
        val stats = Messages.stats()

        hook.embed(Embed {
            field(name = "Messages", value = stats.messages.toString())
            field(name = "Reactions", value = stats.reactions.toString())
        })
    }
}
