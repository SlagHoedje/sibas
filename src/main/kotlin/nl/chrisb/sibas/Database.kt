package nl.chrisb.sibas

import com.zaxxer.hikari.HikariDataSource
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp

object Database {
    private val ds = HikariDataSource().also {
        it.jdbcUrl = System.getenv("DB")
            ?: throw RuntimeException("Environment variable DB should contain the jdbc database URL")
        it.username = System.getenv("DB_USER")
            ?: throw RuntimeException("Environment variable DB_USER should contain the database username")
        it.password = System.getenv("DB_PASS")
            ?: throw RuntimeException("Environment variable DB_PASS should contain the database password")
        it.maximumPoolSize = 3
    }

    fun poolConnection() = ds.connection
}

fun Connection.prepare(@Language("SQL") sql: String) = StatementBuilder(this, sql)
class StatementBuilder(connection: Connection, @Language("SQL") sql: String) {
    private val statement = connection.prepareStatement(sql)
    private var batched = false

    fun args(vararg args: Any?): StatementBuilder {
        args.forEachIndexed { i, arg ->
            when (arg) {
                is String? -> statement.setString(i + 1, arg)
                is Int -> statement.setInt(i + 1, arg)
                is Long -> statement.setLong(i + 1, arg)
                is Timestamp? -> statement.setTimestamp(i + 1, arg)
                else -> throw RuntimeException("Unknown SQL statement argument type ${arg!!::class.simpleName}")
            }
        }

        return this
    }

    fun batched(): StatementBuilder {
        batched = true

        return this
    }

    fun batch(): StatementBuilder {
        if (!batched) {
            throw RuntimeException("Trying to add a batch to a non-batched statement")
        }

        statement.addBatch()

        return this
    }

    fun exec(): Sequence<ResultSet> {
        if (batched) {
            statement.executeBatch()
        } else {
            if (statement.execute()) {
                val results = statement.resultSet

                return sequence {
                    while (results.next()) {
                        yield(results)
                    }
                }
            }
        }

        return emptySequence()
    }
}

