package nl.chrisb.sibas.games.common

import dev.minn.jda.ktx.Embed
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonInteraction

const val TIMEOUT = 30000L

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(DelicateCoroutinesApi::class)
class Invite(val interaction: Interaction, val from: User, val to: User, val match: Match) {
    init {
        interaction.replyEmbeds(Embed {
            title = "${to.name}, ${from.name} has invited you to play ${match.name}!"
            description = "Will you accept the challenge?"
            footer("This invitation will expire in ${TIMEOUT / 1000} seconds.")
        }).addActionRow(
            Button.success("game:invite:accept", "Yes"),
            Button.danger("game:invite:deny", "No")
        ).queue {
            val invite = this
            GlobalScope.launch {
                delay(TIMEOUT)
                InviteManager.cancel(invite)
            }
        }
    }
}

object InviteManager {
    private val invites = mutableListOf<Invite>()

    private fun canInvite(user: User) = !invites.any { it.from == user || it.to == user } && !MatchManager.inMatch(user)

    fun invite(interaction: Interaction, from: User, to: User, match: Match) {
        if (!canInvite(from)) {
            interaction.reply("**ERROR!** You can't start a new game.").setEphemeral(true).queue()
            return
        }

        if (!canInvite(to)) {
            interaction.reply("**ERROR!** You can't currently invite <@${to.id}>.").setEphemeral(true).queue()
            return
        }

        invites.add(Invite(interaction, from, to, match))
    }

    fun cancel(invite: Invite) {
        if (invites.remove(invite)) {
            invite.interaction.hook.deleteOriginal().queue()
        }
    }

    fun respond(interaction: ButtonInteraction, user: User, accept: Boolean) {
        val invite = invites.find { it.from == user || it.to == user }

        if (invite == null) {
            interaction.reply("**ERROR!** That's not for you to decide.").setEphemeral(true).queue()
            return
        }

        if (!accept) {
            cancel(invite)
            return
        }

        if (user != invite.to) {
            interaction.reply("**ERROR!** That's not for you to decide.").setEphemeral(true).queue()
            return
        }

        cancel(invite)
        interaction.deferEdit().queue()
        MatchManager.startMatch(invite.match, invite.interaction.messageChannel)
    }
}
