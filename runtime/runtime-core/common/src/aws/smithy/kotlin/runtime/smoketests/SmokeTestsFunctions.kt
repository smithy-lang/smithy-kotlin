package aws.smithy.kotlin.runtime.smoketests

public expect fun exitProcess(status: Int): Nothing
public expect fun getEnv(name: String): String?
