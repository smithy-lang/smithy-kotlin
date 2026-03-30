package aws.smithy.kotlin.runtime.http.codecs

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder
import aws.smithy.kotlin.runtime.serde.codecs.KeyValueEncoder

public class HttpRequestEncoder(
    private val requestBuilder: HttpRequestBuilder,
    private val bodyEncoder: Encoder,
) : Encoder {
    override fun encodeBigDecimal(value: BigDecimal) {
        TODO("Not yet implemented")
    }

    override fun encodeBigInteger(value: BigInteger) {
        TODO("Not yet implemented")
    }

    override fun encodeBoolean(value: Boolean) {
        TODO("Not yet implemented")
    }

    override fun encodeByte(value: Byte) {
        TODO("Not yet implemented")
    }

    override fun encodeByteStream(value: ByteStream) {
        TODO("Not yet implemented")
    }

    override fun encodeDouble(value: Double) {
        TODO("Not yet implemented")
    }

    override fun encodeFloat(value: Float) {
        TODO("Not yet implemented")
    }

    override fun encodeInt(value: Int) {
        TODO("Not yet implemented")
    }

    override fun encodeList(elementBlock: (Encoder) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun encodeLong(value: Long) {
        TODO("Not yet implemented")
    }

    override fun encodeMap(entryBlock: (KeyValueEncoder) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun encodeNull() {
        TODO("Not yet implemented")
    }

    override fun encodeShort(value: Short) {
        TODO("Not yet implemented")
    }

    override fun encodeString(value: String) {
        TODO("Not yet implemented")
    }

    override fun encodeStructure(memberBlock: (KeyValueEncoder) -> Unit) {
        TODO("Not yet implemented")
    }
}
