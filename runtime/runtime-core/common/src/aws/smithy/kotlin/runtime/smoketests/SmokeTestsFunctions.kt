package aws.smithy.kotlin.runtime.smoketests

public expect fun exitProcess(status: Int): Nothing

public class SmokeTestsException(message: String) : Exception(message)

/**
 * An [Appendable] which can be used for printing test results to the console
 */
public val DefaultPrinter: Appendable = object : Appendable {
    override fun append(c: Char) = this.also { print(c) }
    override fun append(csq: CharSequence?) = this.also { print(csq) }
    override fun append(csq: CharSequence?, start: Int, end: Int) = this.also { print(csq?.subSequence(start, end)) }
}
