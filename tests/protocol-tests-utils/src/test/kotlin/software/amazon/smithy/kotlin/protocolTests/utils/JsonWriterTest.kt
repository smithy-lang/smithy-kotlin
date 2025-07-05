package software.amazon.smithy.kotlin.protocolTests.utils

import org.junit.jupiter.api.Assertions.*
import java.io.StringWriter
import kotlin.test.Test

class JsonWriterTest {

    @Test
    fun `write empty array`() {
        var result = setup {
            startArray()
            endArray()
        }

        assertEquals("[]", result)
    }

    @Test
    fun `write one element array`() {
        var result = setup {
            startArray()
            writeValue(123)
            endArray()
        }

        assertEquals("[123]", result)
    }

    @Test
    fun `write two elements array`() {
        var result = setup {
            startArray()
            writeValue(123)
            writeValue(456)
            endArray()
        }

        assertEquals("[123,456]", result)
    }

    @Test
    fun `write nested empty array`() {
        var result = setup {
            startArray()
            startArray()
            endArray()
            endArray()
        }

        assertEquals("[[]]", result)
    }

    @Test
    fun `write nested one element array`() {
        var result = setup {
            startArray()
            startArray()
            writeValue("foo bar")
            endArray()
            endArray()
        }

        assertEquals("[[\"foo bar\"]]", result)
    }

    @Test
    fun `write nested two element array`() {
        var result = setup {
            startArray()
            startArray()
            writeValue("foo bar")
            writeValue(123)
            endArray()
            endArray()
        }
        assertEquals("[[\"foo bar\",123]]", result)
    }

    @Test
    fun `write nested empty object array`() {
        var result = setup {
            startArray()
            startObject()
            endObject()
            endArray()
        }
        assertEquals("[{}]", result)
    }

    @Test
    fun `write nested objects array`() {
        var result = setup {
            startArray()
            startObject()
            endObject()
            startObject()
            writeKey("name")
            writeValue("Joe Dow")
            endObject()
            endArray()
        }
        assertEquals("[{},{\"name\":\"Joe Dow\"}]", result)
    }

    @Test
    fun `write empty object`() {
        var result = setup {
            startObject()
            endObject()
        }
        assertEquals("{}", result)
    }

    @Test
    fun `write one pair object`() {
        var result = setup {
            startObject()
            writeKey("name")
            writeValue("Joe Dow")
            endObject()
        }
        assertEquals("{\"name\":\"Joe Dow\"}", result)
    }

    @Test
    fun `write one pair with nested object`() {
        var result = setup {
            startObject()
            writeKey("personal")
            startObject()
            writeKey("name")
            writeValue("Joe Doe")
            endObject()
            endObject()
        }
        assertEquals("{\"personal\":{\"name\":\"Joe Doe\"}}", result)
    }

    @Test
    fun `writes two pairs object`() {
        var result = setup {
            startObject()
            writeKey("name")
            writeValue("Joe Doe")
            writeKey("age")
            writeValue(20.3)
            endObject()
        }
        assertEquals("{\"name\":\"Joe Doe\",\"age\":20.3}", result)
    }

    @Test
    fun `escapes special chars`() {
        var result = setup {
            writeValue("Joe \n \t \\ \"foo\" Doe")
        }
        assertEquals("\"Joe \\n \\t \\\\ \\\"foo\\\" Doe\"", result)
    }

    @Test
    fun `handles literal json`() {
        var result = setup {
            startObject()
            writeKey("foo")
            writeEncodedValue("{\"bar\": \"baz\"}")
            endObject()
        }
        assertEquals("{\"foo\":{\"bar\": \"baz\"}}", result)
    }

    internal fun setup(block: JsonWriter.() -> Unit): String {
        var writer = StringWriter()
        var jsonWriter = JsonWriter(writer)
        block(jsonWriter)
        return writer.toString()
    }
}
