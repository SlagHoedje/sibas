package nl.chrisb.sibas

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

val Entity.longId
    get() = id.toLong()

fun Snowflake.toLong() = value.toLong()

fun <T> Flow<T>.chunked(size: Int) = flow<List<T>> {
    val accumulator = mutableListOf<T>()
    var counter = 0

    this@chunked.collect {
        accumulator.add(it)
        counter += 1

        if (counter == size) {
            emit(accumulator)
            accumulator.clear()
            counter = 0
        }
    }

    if (accumulator.isNotEmpty()) {
        emit(accumulator)
    }
}
