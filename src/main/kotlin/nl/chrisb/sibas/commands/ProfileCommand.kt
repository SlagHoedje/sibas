package nl.chrisb.sibas.commands

import dev.minn.jda.ktx.Embed
import dev.minn.jda.ktx.await
import net.dv8tion.jda.api.entities.User
import nl.chrisb.sibas.Messages
import java.time.ZoneOffset

@Command(name = "profile", description = "View stats about a user.")
object ProfileCommand {
    @Executor
    suspend fun executor(hook: CommandHook, @Description("The user to view the profile of.") user: User?) {
        hook.defer()

        val userArg = user ?: hook.event.user
        val member = hook.event.guild?.retrieveMember(userArg)?.await()

        val profile = Messages.profile(
            member,
            userArg
        )

        hook.embed(Embed {
            title = "${profile.name}'${if (profile.name.endsWith('s', true)) "" else "s"} profile"
            thumbnail = profile.avatar

            if (member?.timeBoosted != null) {
                description = "server booster pog"
            }

            val created = profile.created.toLocalDateTime().toEpochSecond(ZoneOffset.UTC)
            val joined = profile.joined?.toLocalDateTime()?.toEpochSecond(ZoneOffset.UTC)

            field(
                "Statistics",
                (if (joined != null) "Joined server: <t:${joined}:D>\n" else "") +
                        """
                            Account created: <t:${created}:D>
                            Total reactions received: ${profile.totalReactions}
                            Total messages sent: ${profile.totalMessages}
                        """.trimIndent(),
                inline = false
            )

            field(
                "Reactions received",
                profile.reactions.joinToString("\n") { "${it.first} ${it.second}" },
                inline = false
            )

            field(
                "Messages sent",
                profile.channelMessages.joinToString("\n") { "<#${it.first}>: ${it.second} messages" },
                inline = false
            )
        })
    }
}
