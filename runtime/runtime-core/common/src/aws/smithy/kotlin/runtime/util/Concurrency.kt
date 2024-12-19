package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.runBlocking

@InternalApi
public fun runBlocking(block: suspend () -> Unit) {
    runBlocking {
        block()
    }
}
