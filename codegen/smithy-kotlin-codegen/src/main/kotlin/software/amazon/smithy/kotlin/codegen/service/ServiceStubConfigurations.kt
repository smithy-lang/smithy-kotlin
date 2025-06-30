package software.amazon.smithy.kotlin.codegen.service

enum class ServiceFramework(val value: String) {
    KTOR("ktor"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ServiceFramework = when (value.lowercase()) {
            "ktor" -> KTOR
            else -> throw IllegalArgumentException("$value is not a valid ServerFramework value, expected $KTOR")
        }
    }
}

enum class ServiceEngine(val value: String) {
    NETTY("Netty"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ServiceEngine = when (value.lowercase()) {
            "netty" -> NETTY
            else -> throw IllegalArgumentException("$value is not a valid ServerFramework value, expected $NETTY")
        }
    }
}
