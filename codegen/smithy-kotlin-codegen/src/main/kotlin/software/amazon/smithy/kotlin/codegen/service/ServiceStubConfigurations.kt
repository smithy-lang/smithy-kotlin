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

enum class LogLevel(val value: String) {
    INFO("INFO"),
    WARNING("WARN"),
    DEBUG("DEBUG"),
    ERROR("ERROR"),
    OFF("OFF"),
    ALL("ALL"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): LogLevel = when (value.lowercase()) {
            "info" -> INFO
            "warning" -> WARNING
            "debug" -> DEBUG
            "error" -> ERROR
            "off" -> OFF
            "all" -> ALL
            else -> throw IllegalArgumentException("$value is not a valid LogLevel value, expected one of these: $INFO, $WARNING, $DEBUG, $ERROR, $OFF, $ALL")
        }
    }
}
