package nl.chrisb.sibas

import dev.minn.jda.ktx.await
import dev.minn.jda.ktx.interactions.Subcommand
import dev.minn.jda.ktx.interactions.SubcommandGroup
import dev.minn.jda.ktx.interactions.command
import dev.minn.jda.ktx.interactions.updateCommands
import dev.minn.jda.ktx.listener
import dev.minn.jda.ktx.onCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.OptionData

fun JDA.commands(builder: CommandsBuilder.() -> Unit) {
    val commandsBuilder = CommandsBuilder().apply(builder)

    listener<GuildReadyEvent> { event ->
        event.guild.updateCommands {
            for (command in commandsBuilder.commands) {
                command(command.name, command.description) {
                    if (command.options.isNotEmpty()) {
                        addOptions(command.options)
                    }

                    command.subCommands[""]?.let { subCommands ->
                        if (subCommands.isNotEmpty()) {
                            addSubcommands(subCommands.map {
                                Subcommand(it.name, it.description) {
                                    addOptions(it.options)
                                }
                            })
                        }
                    }

                    val groups = command.subCommands.filterKeys { it != "" }
                    if (groups.isNotEmpty()) {
                        addSubcommandGroups(groups.map { (group, subCommands) ->
                            SubcommandGroup(group, "No description.") {
                                addSubcommands(subCommands.map {
                                    Subcommand(it.name, it.description) {
                                        addOptions(it.options)
                                    }
                                })
                            }
                        })
                    }
                }

                onCommand(command.name) {
                    val context = CommandContext(it)

                    try {
                        command.subCommands[it.subcommandGroup ?: ""]?.find { subCommand ->
                            subCommand.name == it.subcommandName
                        }?.exec?.invoke(context) ?: run {
                            try {
                                command.exec?.invoke(context)
                            } catch (e: Throwable) {
                                context.message("**ERROR!** ${e.message}")
                            }
                        }
                    } catch (e: Throwable) {
                        context.message("**ERROR!** ${e.message}")
                    }
                }

            }

            queue()
        }
    }
}

class CommandFail(reason: String) : Exception(reason)

class CommandsBuilder {
    val commands = mutableListOf<Command>()

    fun command(
        name: String,
        description: String = "No description.",
        builder: Command.() -> Unit
    ) {
        commands.add(Command(name, description).apply(builder))
    }
}

class Command(val name: String, val description: String) {
    val options = mutableListOf<OptionData>()
    val subCommands = mutableMapOf<String, MutableList<Command>>()
    var exec: (suspend CommandContext.() -> Unit)? = null

    fun option(option: OptionData) {
        options.add(option)
    }

    fun subCommand(
        name: String,
        description: String = "No description.",
        group: String = "",
        builder: Command.() -> Unit
    ) {
        subCommands.getOrPut(group) { mutableListOf() }.add(Command(name, description).apply(builder))
    }

    fun executor(executor: suspend CommandContext.() -> Unit) {
        exec = executor
    }
}

class CommandContext(val event: SlashCommandEvent) {
    suspend fun preIndex(): Int {
        message("Indexing all channels...")

        var count = 0
        event.guild?.textChannels?.forEach {
            count += Messages.index(it, event).await()
        }

        return count
    }

    fun fail(message: String): Nothing = throw CommandFail(message)

    fun checkAdmin() {
        if (event.member?.isAdmin() != true) {
            fail("You're not allowed to do this.")
        }
    }

    fun message(message: String) {
        if (event.interaction.isAcknowledged) {
            event.hook.editOriginal(message).queue()
        } else {
            event.reply(message).queue()
        }
    }

    fun messageChannel(name: String) = event.getOption(name)?.asMessageChannel
}
