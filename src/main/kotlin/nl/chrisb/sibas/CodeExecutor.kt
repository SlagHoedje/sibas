package nl.chrisb.sibas

import com.github.codeboy.piston4j.api.ExecutionResult
import com.github.codeboy.piston4j.api.Piston

data class Code(val language: String, val code: String) {
    fun run(): ExecutionResult {
        val api = Piston.getDefaultApi()
        val runtime = api.getRuntimeUnsafe(language) ?: throw Exception("Unknown language.")
        return runtime.execute(code)
    }
}

fun parseRunCommand(content: String): Code {
    val parts = content.split(Regex("\\s"), 3)

    if (parts.size != 3) {
        throw Exception("Invalid command usage")
    }

    val language = parts[1]
    val code = parts[2].replace(Regex("(^`(`{2}(\\S*\\s)?)?|`(`{2})?\$)"), "")

    return Code(language, code)
}
