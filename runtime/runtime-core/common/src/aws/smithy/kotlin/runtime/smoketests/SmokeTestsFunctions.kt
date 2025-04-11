package aws.smithy.kotlin.runtime.smoketests

public expect fun exitProcess(status: Int): Nothing

/**
 * Prints an exceptions stack trace using test anything protocol (TAP) format e.g.
 *
 * #java.lang.ArithmeticException: / by zero
 * #	at FileKt.main(File.kt:3)
 * #	at FileKt.main(File.kt)
 * #	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 * #	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)
 * #	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)
 * #	at java.base/java.lang.reflect.Method.invoke(Unknown Source)
 * #	at executors.JavaRunnerExecutor$Companion.main(JavaRunnerExecutor.kt:27)
 * #	at executors.JavaRunnerExecutor.main(JavaRunnerExecutor.kt)
 */
@Deprecated(
    message = "No longer used, target for removal in 1.5",
    replaceWith = ReplaceWith("println(exception.stackTraceToString().prependIndent(\"#\"))"),
    level = DeprecationLevel.WARNING,
)
public fun printExceptionStackTrace(exception: Exception): Unit =
    println(exception.stackTraceToString().split("\n").joinToString("\n") { "#$it" })

public class SmokeTestsException(message: String) : Exception(message)

/**
 * An [Appendable] which can be used for printing test results to the console
 */
public val DefaultPrinter: Appendable = object : Appendable {
    override fun append(c: Char) = this.also { print(c) }
    override fun append(csq: CharSequence?) = this.also { print(csq) }
    override fun append(csq: CharSequence?, start: Int, end: Int) = this.also { print(csq?.subSequence(start, end)) }
}
