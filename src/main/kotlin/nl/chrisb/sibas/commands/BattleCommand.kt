package nl.chrisb.sibas.commands

import net.dv8tion.jda.api.entities.User
import nl.chrisb.sibas.games.TicTacToe
import nl.chrisb.sibas.games.common.InviteManager
import nl.chrisb.sibas.games.ultimate.UltimateTicTacToe

@Command(name = "battle", description = "Challenge someone else to a battle.")
object BattleCommand {
    @Executor
    fun executor(
        hook: CommandHook,
        @Choices("Tic-Tac-Toe", "Ultimate Tic-Tac-Toe")
        @Description("The game you want to play") game: String,
        @Description("The user you want to play against") opponent: User
    ) {
        val match = when (game) {
            "Tic-Tac-Toe" -> TicTacToe(listOf(hook.event.user, opponent))
            "Ultimate Tic-Tac-Toe" -> UltimateTicTacToe(listOf(hook.event.user, opponent))
            else -> {
                hook.messageEphemeral("**ERROR!** Unrecognized game.")
                return
            }
        }

        InviteManager.invite(hook.event.interaction, hook.event.user, opponent, match)
    }
}
