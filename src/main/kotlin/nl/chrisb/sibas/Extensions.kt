package nl.chrisb.sibas

import com.kotlindiscord.kord.extensions.checks.memberFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.checks.userFor
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.any
import dev.kord.core.entity.Entity
import dev.kord.core.event.Event
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

suspend fun <T : Event> CheckContext<T>.isAdmin() {
    if (!passed) {
        return
    }

    val user = userFor(event)
    val member = memberFor(event)?.asMember()

    if (!failIf(user?.longId != 120593086844895234
                && member?.roles?.any { it.name.lowercase().contains("staff") } != true
                && member?.hasPermission(Permission.ManageMessages) != true, "You can't do this")
    ) {
        pass()
    }
}
