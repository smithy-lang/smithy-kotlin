package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformJVMTest {

    private val fileContent = "This is the file content.  ⥳⏗⣱⧲⅏⻃Ⱊ⅘"
    private lateinit var tempFile: Path

    @BeforeTest
    fun writeTempFile() {
        tempFile = Files.createTempFile("prefix", "postfix")
        tempFile.writeText(fileContent)
    }

    @Test
    fun itReadsFiles() = runBlocking {
        val actual = Platform.readFileOrNull(tempFile.absolutePathString())

        assertNotNull(actual)
        assertEquals(fileContent, actual.decodeToString())
    }
}