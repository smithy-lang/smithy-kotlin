package software.amazon.smithy.kotlin.protocolTests.utils

import java.io.Closeable
import java.io.Writer

/**
 * A JSON simple, streaming, JSON writer.
 */
class JsonWriter(private val writer: Writer) : Closeable {
    internal enum class State {
        Start, End,
        ObjectStart, ObjectFirstKey, ObjectAfterFirstKey, ObjectValue,
        ArrayStart, ArrayValue
    }

    private val stack = ArrayDeque<State>()

    init {
        stack.add(State.Start)
    }

    /**
     * Starts a JSON object.
     */
    fun startObject(): JsonWriter {
        pushTransition(State.ObjectStart)
        writer.write("{")
        return this;
    }

    /**
     * Ends a JSON object.
     */
    fun endObject(): JsonWriter {
        val current = stack.removeLast()
        if (current != State.ObjectStart && current != State.ObjectValue) {
            throw Exception("Cannot end object while state is $current")
        }
        writer.write("}")
        return this
    }

    /**
     * Writes a key for a JSON object.
     */
    fun writeKey(key: String): JsonWriter {
        val current = stack.removeLast()
        if (current == State.ObjectStart) {
            stack.addLast(State.ObjectFirstKey)
        } else if (current == State.ObjectValue) {
            writer.write(",")
            stack.addLast(State.ObjectAfterFirstKey)
        } else {
            throw Exception("Cannot write key $key while state is $current")
        }
        writeString(key)
        writer.write(":")
        return this;
    }

    /**
     * Writes a key value pair for a JSON object.
     */
    fun writeKvp(key: String, value: Any?): JsonWriter {
        writeKey(key)
        writeValue(value)
        return this
    }

    /**
     * Starts a JSON array.
     */
    fun startArray(): JsonWriter {
        pushTransition(State.ArrayStart)
        writer.write("[")
        return this;
    }

    /**
     * Ends a JSON array.
     */
    fun endArray(): JsonWriter {
        val current = stack.removeLast()
        if (current != State.ArrayStart && current != State.ArrayValue) {
            throw Exception("Cannot end array while state is $current")
        }
        writer.write("]")
        return this
    }

    /**
     * Writes any value. Possible values allowed are String, Int, Double, Float,
     * Long, and, Boolean.
     */
    fun writeValue(input: Any?): JsonWriter {
        val current = stack.last()
        transitionForValue(current)

        if (input == null) {
            writer.write("null")
        }
        when (input) {
            is String -> writeString(input)
            is Int -> writer.write(input.toString())
            is Double -> writer.write(input.toString())
            is Float -> writer.write(input.toString())
            is Long -> writer.write(input.toString())
            is Boolean -> writer.write(input.toString())
            else -> throw Exception("Unsupported input type: $input")
        }
        return this;
    }

    /**
     * Writes a literal String that is already JSON encoded.
     */
    fun writeEncodedValue(input: String): JsonWriter {
        val current = stack.last()
        transitionForValue(current)
        writer.write(input)
        return this;
    }

    /**
     * Transitions and pushes a new state onto the stack.
     */
    private fun pushTransition(state: State) {
        transitionForValue(state)
        stack.addLast(state)
    }

    /**
     * Transitions to a new proper state to add a new value to the stream.
     */
    private fun transitionForValue(state: State) {
        when (val current = stack.removeLast()) {
            State.Start -> {
                stack.addLast(State.End)
            }

            State.ObjectFirstKey, State.ObjectAfterFirstKey -> {
                stack.addLast(State.ObjectValue)
            }

            State.ArrayStart -> {
                stack.addLast(State.ArrayValue)
            }

            State.ArrayValue -> {
                writer.write(",")
                stack.addLast(State.ArrayValue)
            }

            else -> {
                throw Exception("The current state $current cannot precede an structured of type $state")
            }
        }

    }

    /**
     * Writes a JSON string encoding any special characters to output correct JSON.
     */
    private fun writeString(input: String) {
        writer.write('"'.code)
        input.forEach { c ->
            when (c) {
                '\\' -> writer.write("\\\\")
                '"' -> writer.write("\\\"")
                '\b' -> writer.write("\\b")
                '\u000C' -> writer.write("\\f")
                '\n' -> writer.write("\\n")
                '\r' -> writer.write("\\r")
                '\t' -> writer.write("\\t")
                in '\u0000'..'\u001F' -> writer.write(String.format("\\u%04X", c.code))
                else -> writer.write(c.code)
            }
        }
        writer.write('"'.code)
    }

    /**
     * Closes the underlying writer.
     */
    override fun close() {
        writer.close()
    }
}
