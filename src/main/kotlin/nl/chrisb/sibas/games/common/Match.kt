package nl.chrisb.sibas.games.common

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ButtonInteraction

abstract class Match(val players: List<User>) {
    abstract val name: String

    abstract fun begin(channel: MessageChannel)
    abstract fun processButton(interaction: ButtonInteraction, user: User, id: String)
}
