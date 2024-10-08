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
public fun printExceptionStackTrace(exception: Exception): Unit =
    println(exception.stackTraceToString().split("\n").joinToString("\n") { "#$it" })
