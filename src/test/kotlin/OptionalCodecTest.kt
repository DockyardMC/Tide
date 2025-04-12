import com.google.gson.JsonObject
import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Codecs
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

class OptionalCodecTest {

    data class NullableTest(val nullable: String?) {

        companion object {
            val codec = Codec.of<NullableTest> {
                field("nullable", Codecs.String.optional(), NullableTest::nullable)
            }
        }
    }

    private val nullableEmpty = NullableTest(null)
    private val nullablePresent = NullableTest("hi")


    @Test
    fun testNullableNetworkReadWrite() {
        testNullableNetwork(nullableEmpty)
        testNullableNetwork(nullablePresent)
    }

    @Test
    fun testNullableJsonReadWrite() {
        testNullableJson(nullableEmpty)
        testNullableJson(nullablePresent)
    }


    private fun testNullableNetwork(type: NullableTest) {
        val buffer = Unpooled.buffer()
        NullableTest.codec.writeNetwork(buffer, type)

        val newNullable = NullableTest.codec.readNetwork(buffer)
        assertEquals(type, newNullable)
        buffer.release()
    }

    private fun testNullableJson(type: NullableTest) {
        val json = JsonObject()
        NullableTest.codec.writeJson(json, type)

        val newNullable = NullableTest.codec.readJson(json)
        assertEquals(type, newNullable)
    }
}