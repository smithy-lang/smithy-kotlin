package software.aws.clientrt.time

import kotlin.test.*

class ConvertersTest {
    @Test
    fun convertInstantFromJvmToAwsSdk() {
        val jvmInstant = java.time.Instant.now()!!
        val awsSdkInstant = jvmInstant.toAwsSdkInstant()
        assertEquals(jvmInstant.epochSecond, awsSdkInstant.epochSeconds)
    }

    @Test
    fun convertInstantFromAwsSdkToJvm() {
        val awsSdkInstant = software.aws.clientrt.time.Instant.now()
        val jvmInstant = awsSdkInstant.toJvmInstant()
        assertEquals(awsSdkInstant.epochSeconds, jvmInstant.epochSecond)
    }
}
