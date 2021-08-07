package nl.chrisb.sibas.commands

import dev.minn.jda.ktx.interactions.Subcommand
import dev.minn.jda.ktx.interactions.SubcommandGroup
import dev.minn.jda.ktx.interactions.command
import dev.minn.jda.ktx.interactions.updateCommands
import dev.minn.jda.ktx.listener
import dev.minn.jda.ktx.onCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.ActionRow
import nl.chrisb.sibas.Messages
import nl.chrisb.sibas.isAdmin
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class Command(val group: String = "", val name: String, val description: String = "No description.")

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class Executor

@Target(AnnotationTarget.VALUE_PARAMETER)
@MustBeDocumented
annotation class Description(val description: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
@MustBeDocumented
annotation class Choices(vararg val choices: String)

class CommandFail(message: String) : Exception(message)

class ExecutableCommand(private val executor: KFunction<*>) {
    val options = mutableListOf<OptionData>()

    init {
        executor.valueParameters.forEach { parameter ->
            val type = when (parameter.type.javaType) {
                String::class.java -> OptionType.STRING
                Long::class.java -> OptionType.INTEGER
                Boolean::class.java -> OptionType.BOOLEAN
                User::class.java -> OptionType.USER
                GuildChannel::class.java -> OptionType.CHANNEL
                Role::class.java -> OptionType.ROLE
                IMentionable::class.java -> OptionType.MENTIONABLE
                CommandHook::class.java -> return@forEach
                else -> {
                    println("Unknown option type at $executor: ${parameter.type}")
                    return@forEach
                }
            }

            val required = !parameter.type.isMarkedNullable
            val description = parameter.findAnnotation<Description>()?.description ?: "No description."
            val choices = parameter.findAnnotation<Choices>()
                ?.choices
                ?.map { net.dv8tion.jda.api.interactions.commands.Command.Choice(it, it) }

            val option = OptionData(type, parameter.name!!, description).setRequired(required)

            if (choices != null) {
                option.addChoices(choices)
            }

            options.add(option)
        }
    }

    suspend fun call(command: Any, hook: CommandHook, event: SlashCommandEvent) {
        val args = executor.valueParameters.map { parameter ->
            if (parameter.type.javaType == CommandHook::class.java) {
                return@map hook
            }

            options.find { it.name == parameter.name }?.let {
                val option = event.getOption(it.name) ?: return@map null

                return@map when (it.type) {
                    OptionType.STRING -> option.asString
                    OptionType.INTEGER -> option.asLong
                    OptionType.BOOLEAN -> option.asBoolean
                    OptionType.USER -> option.asUser
                    OptionType.CHANNEL -> option.asGuildChannel
                    OptionType.ROLE -> option.asRole
                    OptionType.MENTIONABLE -> option.asMentionable
                    else -> null
                }
            }

            null
        }

        executor.callSuspend(command, *args.toTypedArray())
    }
}

fun JDA.registerCommands(vararg commands: Any) {
    val registered = mutableListOf<String>()

    listener<GuildReadyEvent> { event ->
        event.guild.updateCommands {
            for (command in commands) {
                val annotation = command::class.findAnnotation<Command>()
                if (annotation == null) {
                    println("Tried to register command (${command::class.simpleName}) without the @Command annotation.")
                    continue
                }

                val subCommands = command::class.memberFunctions
                    .mapNotNull { it.findAnnotation<Command>()?.let { annotation -> it to annotation } }
                    .map { (executor, command) ->
                        val executableCommand = ExecutableCommand(executor)

                        val subCommand = Subcommand(command.name, command.description) {
                            if (executableCommand.options.isNotEmpty()) {
                                addOptions(executableCommand.options)
                            }
                        }

                        Triple(command.group, subCommand, executableCommand)
                    }

                val executor = command::class.memberFunctions.find { it.findAnnotation<Executor>() != null }
                val executableCommand = executor?.let { ExecutableCommand(it) }

                command(annotation.name, annotation.description) {
                    if (executableCommand?.options?.isNotEmpty() == true) {
                        addOptions(executableCommand.options)
                    }

                    if (subCommands.isNotEmpty()) {
                        val directSubCommands = subCommands.filter { it.first == "" }.map { it.second }
                        if (directSubCommands.isNotEmpty()) {
                            addSubcommands(directSubCommands)
                        }

                        val groups = mutableMapOf<String, MutableList<SubcommandData>>()
                        for (subCommand in subCommands) {
                            if (subCommand.first == "") {
                                continue
                            }

                            groups.getOrPut(subCommand.first) { mutableListOf() }.add(subCommand.second)
                        }

                        addSubcommandGroups(groups.map { (name, subCommands) ->
                            SubcommandGroup(name, "No description.") {
                                addSubcommands(subCommands)
                            }
                        })
                    }
                }

                if (!registered.contains(annotation.name)) {
                    registered.add(annotation.name)

                    onCommand(annotation.name) { slashCommandEvent ->
                        val hook = CommandHook(slashCommandEvent)

                        try {
                            (subCommands.filter { it.first == slashCommandEvent.subcommandGroup ?: "" }
                                .find { it.second.name == slashCommandEvent.subcommandName }
                                ?.third ?: executableCommand)
                                ?.call(command, hook, slashCommandEvent)
                                ?: run { hook.message("**ERROR!** This command is not executable.") }
                        } catch (e: InvocationTargetException) {
                            val ex = e.targetException

                            if (ex !is CommandFail) {
                                ex.printStackTrace()
                            }

                            hook.message("**ERROR!** ${ex.message}")
                        } catch (e: Throwable) {
                            if (e !is CommandFail) {
                                e.printStackTrace()
                            }

                            hook.message("**ERROR!** ${e.message}")
                        }
                    }
                }
            }

            queue()
        }
    }
}

class CommandHook(val event: SlashCommandEvent) {
    private var replyTime = Instant.now()
    private var secondMessage: Message? = null

    fun defer() {
        event.deferReply().queue()
    }

    suspend fun preIndex(): Int {
        message("Indexing all channels...")

        var count = 0
        event.guild?.textChannels?.forEach {
            count += Messages.index(it) { blocked, count ->
                if (blocked) {
                    message("Indexing <#${it.id}>... _(waiting for another thread to finish)_")
                } else {
                    message("Indexing <#${it.id}>... _($count messages)_")
                }
            }
        }

        return count
    }

    fun fail(message: String): Nothing = throw CommandFail(message)

    fun checkAdmin() {
        if (event.member?.isAdmin() != true) {
            fail("You're not allowed to do this.")
        }
    }

    fun messageEphemeral(message: String) {
        if (!event.interaction.isAcknowledged) {
            event.reply(message).setEphemeral(true).queue()
        } else {
            message(message)
        }
    }

    fun message(message: String, actions: Collection<ActionRow>? = null) {
        if (event.interaction.isAcknowledged) {
            if (Duration.between(replyTime, Instant.now()).toMinutes() >= 14) {
                if (secondMessage == null) {
                    event.channel.sendMessage(message).also {
                        if (actions != null) {
                            it.setActionRows(actions)
                        }
                    }.queue {
                        secondMessage = it
                    }
                } else {
                    secondMessage!!.editMessage(message).also {
                        if (actions != null) {
                            it.setActionRows(actions)
                        }
                    }.queue()
                }
            } else {
                event.hook.editOriginal(message).also {
                    if (actions != null) {
                        it.setActionRows(actions)
                    }
                }.queue()
            }
        } else {
            event.reply(message).also {
                if (actions != null) {
                    it.addActionRows(actions)
                }
            }.queue()
        }
    }

    fun embed(embed: MessageEmbed) {
        if (Duration.between(replyTime, Instant.now()).toMinutes() >= 14) {
            if (secondMessage == null) {
                event.channel.sendMessage(embed).queue {
                    secondMessage = it
                }
            } else {
                secondMessage!!.editMessage(embed).queue()
            }
        } else {
            if (event.interaction.isAcknowledged) {
                event.hook.editOriginalEmbeds(embed).queue()
            } else {
                event.replyEmbeds(embed).queue()
            }
        }
    }

    fun actions(vararg actions: ActionRow) {
        if (event.interaction.isAcknowledged) {
            event.hook.editOriginalComponents(*actions).queue()
        }
    }
}
