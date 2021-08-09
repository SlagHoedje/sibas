package nl.chrisb.sibas.games.ultimate

import kotlin.random.Random

enum class Symbol {
    X,
    O,
    Empty,
}

interface Board {
    val winner: Symbol?
        get() {
            for (symbol in arrayOf(Symbol.X, Symbol.O)) {
                for (x in (0 until 3)) {
                    var count = 0

                    for (y in 0 until 3) {
                        if (get(x, y) == symbol) {
                            count++
                        }
                    }

                    if (count == 3) {
                        return symbol
                    }
                }

                for (y in (0 until 3)) {
                    var count = 0

                    for (x in 0 until 3) {
                        if (get(x, y) == symbol) {
                            count += 1
                        }
                    }

                    if (count == 3) {
                        return symbol
                    }
                }

                var right = true
                var left = true
                for (i in 0 until 3) {
                    if (get(i, i) != symbol) {
                        left = false
                    }

                    if (get(2 - i, i) != symbol) {
                        right = false
                    }
                }

                if (left || right) {
                    return symbol
                }
            }

            if (!(0 until 3).flatMap { x -> (0 until 3).map { y -> get(x, y) } }.any { it == Symbol.Empty }) {
                return Symbol.Empty
            }

            return null
        }

    fun get(x: Int, y: Int): Symbol
}

class RandomBoard : Board {
    private val seed = Random.nextInt()
    private val full = Random.nextBoolean()
    override fun get(x: Int, y: Int): Symbol = Symbol.values()[Random(seed + x + y * 100).nextInt(if (full) 2 else 3)]
}

class MutableBoard : Board {
    private val field = Array(9) { Symbol.Empty }

    fun set(x: Int, y: Int, symbol: Symbol) {
        field[y * 3 + x] = symbol
    }

    override fun get(x: Int, y: Int): Symbol = field[y * 3 + x]
}

class UltimateBoard : Board {
    val boards = Array(9) { MutableBoard() }

    override fun get(x: Int, y: Int): Symbol = getBoard(x, y).winner ?: Symbol.Empty
    fun getBoard(x: Int, y: Int): MutableBoard = boards[y * 3 + x]
}
