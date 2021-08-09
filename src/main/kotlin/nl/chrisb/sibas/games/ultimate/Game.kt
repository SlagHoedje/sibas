package nl.chrisb.sibas.games.ultimate

class Game {
    val board = UltimateBoard()
    var selected: Int? = null
    var turn = 0

    val selectedBoard: MutableBoard?
        get() = selected?.let { board.boards[it] }

    fun nextTurn() {
        turn += 1
        if (turn >= 2) {
            turn = 0
        }
    }

    fun symbol(index: Int) =
        when (index) {
            0 -> Symbol.X
            1 -> Symbol.O
            else -> Symbol.Empty
        }

    fun index(symbol: Symbol) = when (symbol) {
        Symbol.X -> 0
        Symbol.O -> 1
        Symbol.Empty -> -1
    }
}
