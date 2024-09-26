package aws.smithy.kotlin.runtime.smoketests

import kotlin.system.exitProcess

public actual fun exitProcess(status: Int): Nothing = exitProcess(status)
