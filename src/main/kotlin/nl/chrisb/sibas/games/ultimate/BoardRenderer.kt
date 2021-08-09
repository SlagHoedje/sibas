package nl.chrisb.sibas.games.ultimate

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val DIM = 1024
const val BOARD_GAP = 6
const val SLOT_GAP = 10
const val ROUNDING = 10
const val SYMBOL_GAP = 20
const val SYMBOL_WIDTH = 8.0f
const val LARGE_SYMBOL_WIDTH = 24.0f
const val LARGE_SYMBOL_GAP = 40

fun renderSmallBoard(dim: Int, board: Board): BufferedImage {
    val image = BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB)

    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    for (x in (0 until 3)) {
        for (y in (0 until 3)) {
            val drawX = x * dim / 3 + SLOT_GAP / 2
            val drawY = y * dim / 3 + SLOT_GAP / 2
            val slotDim = dim / 3 - SLOT_GAP

            val symbol = board.get(x, y)

            graphics.color = when (symbol) {
                Symbol.X -> Color(0x5865f2)
                Symbol.O -> Color(0xed4245)
                Symbol.Empty -> Color(0x4f545c)
            }

            graphics.fillRoundRect(drawX, drawY, slotDim, slotDim, ROUNDING, ROUNDING)

            graphics.stroke = BasicStroke(SYMBOL_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            graphics.color = Color.WHITE

            val symbolX = drawX + SYMBOL_GAP
            val symbolY = drawY + SYMBOL_GAP
            val symbolDim = slotDim - SYMBOL_GAP * 2

            when (symbol) {
                Symbol.X -> {
                    graphics.drawLine(symbolX, symbolY, symbolX + symbolDim, symbolY + symbolDim)
                    graphics.drawLine(symbolX + symbolDim, symbolY, symbolX, symbolY + symbolDim)
                }
                Symbol.O -> {
                    graphics.drawOval(symbolX, symbolY, symbolDim, symbolDim)
                }
                Symbol.Empty -> {
                }
            }
        }
    }

    return image
}

fun renderGame(game: Game): BufferedImage {
    val image = BufferedImage(DIM, DIM, BufferedImage.TYPE_INT_ARGB)

    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    for (x in (0 until 3)) {
        for (y in (0 until 3)) {
            val drawX = x * DIM / 3 + BOARD_GAP / 2
            val drawY = y * DIM / 3 + BOARD_GAP / 2
            val dim = DIM / 3 - BOARD_GAP

            val board = game.board.getBoard(x, y)
            val winner = board.winner
            val selected = game.selected == null || game.selected == y * 3 + x

            val opacity = if (winner == null && selected) {
                1.0f
            } else {
                0.25f
            }

            graphics.composite = AlphaComposite.SrcOver.derive(opacity)
            val smallImage = renderSmallBoard(dim, board)
            graphics.drawImage(smallImage, drawX, drawY, null)

            if (winner != null) {
                graphics.composite = AlphaComposite.SrcOver.derive(1.0f)
                graphics.stroke = BasicStroke(LARGE_SYMBOL_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                graphics.color = when (winner) {
                    Symbol.X -> Color(0x5865f2)
                    Symbol.O -> Color(0xed4245)
                    Symbol.Empty -> Color.WHITE
                }

                val symbolX = drawX + LARGE_SYMBOL_GAP
                val symbolY = drawY + LARGE_SYMBOL_GAP
                val symbolDim = dim - LARGE_SYMBOL_GAP * 2

                when (winner) {
                    Symbol.X -> {
                        graphics.drawLine(symbolX, symbolY, symbolX + symbolDim, symbolY + symbolDim)
                        graphics.drawLine(symbolX + symbolDim, symbolY, symbolX, symbolY + symbolDim)
                    }
                    Symbol.O -> {
                        graphics.drawOval(symbolX, symbolY, symbolDim, symbolDim)
                    }
                    Symbol.Empty -> {
                        graphics.drawLine(
                            symbolX,
                            symbolY + symbolDim / 2,
                            symbolX + symbolDim,
                            symbolY + symbolDim / 2
                        )
                    }
                }
            }
        }
    }

    graphics.dispose()
    return image
}
