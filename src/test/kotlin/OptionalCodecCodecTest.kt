import com.google.gson.JsonObject
import io.github.dockyardmc.tide.codec.Codec
import io.github.dockyardmc.tide.Codecs
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

class OptionalCodecCodecTest {

    data class NullableTest(val nullable: String?, val time: Long?) {
        companion object {
            val CODEC = Codec.of(
                "nullable", Codecs.String.optional(), NullableTest::nullable,
                "time", Codecs.Long.optional(), NullableTest::time,
                ::NullableTest
            )
        }
    }

    data class NullableHolder(val someString: String, val list: List<NullableTest>) {
        companion object {
            val CODEC = Codec.of(
                "some_string", Codecs.String, NullableHolder::someString,
                "list", NullableTest.CODEC.list(), NullableHolder::list,
                ::NullableHolder
            )
        }
    }

    private val nullableEmpty = NullableTest(null, 7897895L)
    private val nullablePresent = NullableTest("hi", null)
    private val holder = NullableHolder("tzest", mutableListOf(nullablePresent, nullableEmpty))

    @Test
    fun testNullableNetworkReadWrite() {
        testNullableNetwork(nullableEmpty)
        testNullableNetwork(nullablePresent)
        testNullableHolderNetwork(holder)
    }

    @Test
    fun testNullableJsonReadWrite() {
        testNullableJson(nullableEmpty)
        testNullableJson(nullablePresent)
        testNullableHolderJson(holder)
    }

    private fun testNullableHolderNetwork(type: NullableHolder) {
        val buffer = Unpooled.buffer()
        NullableHolder.CODEC.writeNetwork(buffer, type)

        val newNullable = NullableHolder.CODEC.readNetwork(buffer)
        assertEquals(type, newNullable)
        buffer.release()
    }

    private fun testNullableHolderJson(type: NullableHolder) {
        val json = JsonObject()
        NullableHolder.CODEC.writeJson(json, type)

        val newNullable = NullableHolder.CODEC.readJson(json)
        assertEquals(type, newNullable)
    }

    private fun testNullableNetwork(type: NullableTest) {
        val buffer = Unpooled.buffer()
        NullableTest.CODEC.writeNetwork(buffer, type)

        val newNullable = NullableTest.CODEC.readNetwork(buffer)
        assertEquals(type, newNullable)
        buffer.release()
    }

    private fun testNullableJson(type: NullableTest) {
        val json = JsonObject()
        NullableTest.CODEC.writeJson(json, type)

        val newNullable = NullableTest.CODEC.readJson(json)
        assertEquals(type, newNullable)
    }
}