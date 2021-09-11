package nl.chrisb.sibas.games

import dev.minn.jda.ktx.Embed
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonInteraction
import nl.chrisb.sibas.games.common.Match
import nl.chrisb.sibas.games.common.MatchManager

enum class Symbol {
    X,
    O,
    Empty,
}

class TicTacToe(players: List<User>) : Match(players) {
    override val name: String = "Tic-Tac-Toe"

    lateinit var message: Message

    private var field = Array(9) { Symbol.Empty }
    private var turn = 0

    override fun begin(channel: MessageChannel) {
        channel.sendMessageEmbeds(Embed {
            title = "Tic-Tac-Toe"
            description = message()
        }).setActionRows((0 until 3).map { y ->
            ActionRow.of((0 until 3).map { x ->
                val i = y * 3 + x
                Button.secondary("game:action:${i}", " ")
            })
        }).queue {
            message = it
        }
    }

    override fun processButton(interaction: ButtonInteraction, user: User, id: String) {
        if (players[turn] != user) {
            interaction.reply("**ERROR!** It's not your turn.").setEphemeral(true).queue()
            return
        }

        if (interaction.button?.label != " ") {
            interaction.reply("**ERROR!** This field has already been played.").setEphemeral(true).queue()
            return
        }

        val index = id.toIntOrNull()
        if (index == null || !(0 until 9).contains(index)) {
            interaction.reply("**ERROR!** This is not a valid field for a Tic-Tac-Toe game.").setEphemeral(true).queue()
            return
        }

        field[index] = symbol(turn)
        interaction.editButton(
            when (symbol(turn)) {
                Symbol.X -> Button.primary("game:action:${index}", "X")
                Symbol.O -> Button.danger("game:action:${index}", "O")
                Symbol.Empty -> Button.secondary("game:action:${index}", " ")
            }
        ).queue {
            nextTurn()
        }
    }

    private fun winner(previousTurn: Int): Boolean {
        val symbol = symbol(previousTurn)

        for (x in (0 until 3)) {
            var count = 0

            for (y in 0 until 3) {
                if (field[y * 3 + x] == symbol) {
                    count++
                }
            }

            if (count == 3) {
                return true
            }
        }

        for (y in (0 until 3)) {
            var count = 0

            for (x in 0 until 3) {
                if (field[y * 3 + x] == symbol) {
                    count += 1
                }
            }

            if (count == 3) {
                return true
            }
        }

        var right = true
        var left = true
        for (i in 0 until 3) {
            if (field[i * 3 + i] != symbol) {
                left = false
            }

            if (field[i * 3 + (2 - i)] != symbol) {
                right = false
            }
        }

        return left || right
    }

    private fun symbol(index: Int) =
        when (index) {
            0 -> Symbol.X
            1 -> Symbol.O
            else -> Symbol.Empty
        }

    private fun nextTurn() {
        val previousTurn = turn

        turn += 1
        if (turn >= players.size) {
            turn = 0
        }

        if (winner(previousTurn)) {
            message.editMessageEmbeds(Embed {
                title = "Tic-Tac-Toe"
                description = "<@${players[previousTurn].id}> (`${symbol(previousTurn)}`) won!"
            }).queue()
            MatchManager.endMatch(this)
        } else if (field.all { it != Symbol.Empty }) {
            message.editMessageEmbeds(Embed {
                title = "Tic-Tac-Toe"
                description = "It's a draw!"
            }).queue()
            MatchManager.endMatch(this)
        } else {
            message.editMessageEmbeds(Embed {
                title = "Tic-Tac-Toe"
                description = message()
            }).queue()
        }
    }

    private fun message() = """
        `${symbol(0)}`: <@${players[0].id}>
        `${symbol(1)}`: <@${players[1].id}>
        
        It's <@${players[turn].id}>'s turn!
        """.trimIndent()
}
