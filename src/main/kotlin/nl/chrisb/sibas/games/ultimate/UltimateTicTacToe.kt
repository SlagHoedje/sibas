package nl.chrisb.sibas.games.ultimate

import dev.minn.jda.ktx.Embed
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonInteraction
import nl.chrisb.sibas.games.common.Match
import nl.chrisb.sibas.games.common.MatchManager
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(DelicateCoroutinesApi::class)
class UltimateTicTacToe(players: List<User>) : Match(players) {
    override val name: String = "Ultimate Tic-Tac-Toe"

    private val game = Game()
    lateinit var message: Message
    private var timer = 120

    override fun begin(channel: MessageChannel) {
        channel.sendMessage(Embed {
            title = name
        }).queue {
            message = it
            updateMessage(null)
        }

        GlobalScope.launch {
            while (timer > 0) {
                delay(1000)
                timer--
            }

            updateMessage(game.symbol((game.turn + 1) % 2))
        }
    }

    override fun processButton(interaction: ButtonInteraction, user: User, id: String) {
        val index = id.toIntOrNull()
        if (index == null || !(0 until 9).contains(index)) {
            interaction.reply("**ERROR!** This is not a valid field for a Tic-Tac-Toe game.").setEphemeral(true).queue()
            return
        }

        if (user != players[game.turn]) {
            interaction.reply("**ERROR!** It's not your turn.").setEphemeral(true).queue()
            return
        }

        val x = index % 3
        val y = index / 3

        val selectedBoard = game.selectedBoard

        if (selectedBoard == null) {
            val board = game.board.getBoard(x, y)
            if (board.winner != null) {
                interaction.reply("**ERROR!** This board already has a winner.").setEphemeral(true).queue()
                return
            }

            game.selected = index

            interaction.deferEdit().queue()
            updateMessage(null)
        } else {
            if (selectedBoard.get(x, y) != Symbol.Empty) {
                interaction.reply("**ERROR!** This field has already been played.").setEphemeral(true).queue()
                return
            }

            selectedBoard.set(x, y, game.symbol(game.turn))

            val nextBoard = game.board.getBoard(x, y)
            if (nextBoard.winner != null) {
                game.selected = null
            } else {
                game.selected = index
            }

            game.nextTurn()
            timer = 120

            interaction.deferEdit().queue()
            updateMessage(null)
        }
    }

    private fun updateMessage(timeout: Symbol?) {
        val boardImage = renderGame(game)
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(boardImage, "png", outputStream)

        val winner = game.board.winner ?: timeout

        val actionRows = if (winner != null) {
            MatchManager.endMatch(this)

            listOf()
        } else {
            (0 until 3).map { y ->
                ActionRow.of((0 until 3).map { x ->
                    val index = y * 3 + x
                    val selectedBoard = game.selectedBoard
                    if (selectedBoard == null) {
                        val board = game.board.getBoard(x, y)

                        when (board.winner) {
                            Symbol.X -> Button.primary("game:action:$index", "${index + 1}")
                            Symbol.O -> Button.danger("game:action:$index", "${index + 1}")
                            else -> Button.secondary("game:action:$index", "${index + 1}")
                        }
                    } else {
                        when (selectedBoard.get(x, y)) {
                            Symbol.X -> Button.primary("game:action:$index", "X")
                            Symbol.O -> Button.danger("game:action:$index", "O")
                            Symbol.Empty -> Button.secondary("game:action:$index", "_")
                        }
                    }
                })
            }
        }

        val turnPlayer = players[game.turn]

        val text = """
            `${game.symbol(0)}`: <@${players[0].id}>
            `${game.symbol(1)}`: <@${players[1].id}>
        """.trimIndent() + "\n\n" + when {
            timeout != null ->"<@${players[game.index(timeout)].id}> won due to timeout!"
            winner != null -> "<@${players[game.index(winner)].id}> won!"
            game.selected == null -> "<@${turnPlayer.id}>, select the board you want to play."
            else -> "It's your turn, <@${turnPlayer.id}>."
        }

        message.editMessage(Embed {
            title = name
            description = text

            if (winner == null) {
                footer("You have 2 minutes to make a move.")
            }
        }).retainFiles(listOf())
            .addFile(outputStream.toByteArray(), "board.png")
            .setActionRows(actionRows)
            .queue()
    }
}
