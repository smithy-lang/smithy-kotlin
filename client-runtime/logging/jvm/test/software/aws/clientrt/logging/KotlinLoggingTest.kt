package software.aws.clientrt.logging

import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.LoggerFactory

private val logger = KotlinLogging.logger { }
private val loggerFromSlf4j = KotlinLogging.logger(LoggerFactory.getLogger("software.aws.clientrt.logging.slf4jLogger"))
private val loggerFromSlf4jExtension = LoggerFactory.getLogger("software.aws.clientrt.logging.slf4jLoggerExtension").toKLogger()

class ForKotlinLoggingTest {
    val loggerInClass = KotlinLogging.logger { }

    companion object {
        val loggerInCompanion = KotlinLogging.logger { }
    }
}

class KotlinLoggingTest {

    @Test
    fun testLoggerName() {
        assertEquals("software.aws.clientrt.logging.KotlinLoggingTest", logger.name)
        assertEquals("software.aws.clientrt.logging.ForKotlinLoggingTest", ForKotlinLoggingTest().loggerInClass.name)
        assertEquals("software.aws.clientrt.logging.ForKotlinLoggingTest", ForKotlinLoggingTest.loggerInCompanion.name)
        assertEquals("software.aws.clientrt.logging.slf4jLogger", loggerFromSlf4j.name)
        assertEquals("software.aws.clientrt.logging.slf4jLoggerExtension", loggerFromSlf4jExtension.name)
    }
}
