package aws.smithy.kotlin.runtime.smoketests

import kotlin.system.exitProcess

public actual fun exitProcess(status: Int): Nothing = exitProcess(status)
public actual fun getEnv(name: String): String? = System.getenv(name)
