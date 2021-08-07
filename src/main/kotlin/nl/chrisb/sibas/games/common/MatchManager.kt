package nl.chrisb.sibas.games.common

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ButtonInteraction

object MatchManager {
    private val matches = mutableListOf<Match>()

    fun inMatch(user: User) = matches.any { it.players.contains(user) }

    fun startMatch(match: Match, channel: MessageChannel) {
        matches.add(match)
        match.begin(channel)
    }

    fun endMatch(match: Match) {
        matches.remove(match)
    }

    fun processButton(interaction: ButtonInteraction, user: User, id: String) {
        val match = matches.find { it.players.contains(user) }
        if (match == null) {
            interaction.reply("**ERROR!** You can't play in this match.").setEphemeral(true).queue()
            return
        }

        match.processButton(interaction, user, id)
    }
}
