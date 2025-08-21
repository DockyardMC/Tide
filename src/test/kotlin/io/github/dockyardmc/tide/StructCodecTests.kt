package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dockyardmc.tide.codec.Codec
import io.github.dockyardmc.tide.codec.StructCodec
import io.github.dockyardmc.tide.transcoder.JsonTranscoder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class StructCodecTests {

    data class Person(val name: String, val data: PersonData) {
        companion object {
            val CODEC = StructCodec.of(
                "name", Codec.STRING, Person::name,
                "data", PersonData.CODEC, Person::data,
                ::Person
            )
        }

        data class PersonData(val age: Int, val license: String?) {
            companion object {
                val CODEC = StructCodec.of(
                    "age", Codec.INT, PersonData::age,
                    "license", Codec.STRING.optional(), PersonData::license,
                    ::PersonData
                )
            }
        }
    }

    class Empty {
        override fun equals(other: Any?): Boolean {
            return other is Empty
        }

        override fun hashCode(): Int {
            return 0
        }
    }

    data class SingleField(val name: String) {
        companion object {
            val CODEC = StructCodec.of("name", Codec.STRING, SingleField::name, ::SingleField)
        }
    }

    data class SingleFieldOptional(val name: String?) {
        companion object {
            val CODEC = StructCodec.of("name", Codec.STRING.optional(), SingleFieldOptional::name, ::SingleFieldOptional)
        }
    }

    data class SingleFieldDefault(val name: String) {
        companion object {
            val CODEC = StructCodec.of("name", Codec.STRING.default("maya"), SingleFieldDefault::name, ::SingleFieldDefault)
        }
    }

    data class InlineField(val name: String, val innerObject: InnerObject) {
        companion object {
            val CODEC = StructCodec.of(
                "name", Codec.STRING, InlineField::name,
                StructCodec.INLINE, InnerObject.CODEC, InlineField::innerObject,
                ::InlineField
            )
        }

        data class InnerObject(val value: String) {
            companion object {
                val CODEC = StructCodec.of("value", Codec.STRING, InnerObject::value, ::InnerObject)
            }
        }
    }

    @Test
    fun emptyCodecTest() {
        val codec = StructCodec.of(::Empty)
        val result = codec.decode(JsonTranscoder, JsonObject())
        assertEquals(result, Empty())
    }

    @Test
    fun testSingleField() {
        val result = SingleField.CODEC.decode(JsonTranscoder, json("{\"name\": \"maya\"}"))
        assertEquals(SingleField("maya"), result)
    }

    @Test
    fun testSingleFieldMissing() {
        assertThrows<Exception> {
            SingleField.CODEC.decode(JsonTranscoder, json("{}"))
        }
    }

    @Test
    fun testSingleFieldOptionalMissing() {
        assertDoesNotThrow {
            val result = SingleFieldOptional.CODEC.decode(JsonTranscoder, json("{}"))
            assertEquals(SingleFieldOptional(null), result)
        }
        assertDoesNotThrow {
            val result = SingleFieldOptional.CODEC.decode(JsonTranscoder, json("{\"name\": \"maya\"}"))
            assertEquals(SingleFieldOptional("maya"), result)
        }
    }

    @Test
    fun testSingleFieldOptionalDefaultMissing() {
        assertDoesNotThrow {
            val result = SingleFieldDefault.CODEC.decode(JsonTranscoder, json("{}"))
            assertEquals(SingleFieldDefault("maya"), result)
        }
    }

    @Test
    fun testInlineField() {
        val result = InlineField.CODEC.decode(JsonTranscoder, json("{ \"name\": \"maya\", \"value\": \"inner_value\"}"))
        assertEquals(InlineField("maya", InlineField.InnerObject("inner_value")), result)

        val encodeResult = InlineField.CODEC.encode(JsonTranscoder, InlineField("maya", InlineField.InnerObject("inner_value")))
        assertEquals(json("{ \"name\": \"maya\", \"value\": \"inner_value\"}"), encodeResult)
    }

    @Test
    fun testEncodeDecode() {
        val person = Person("Maya", Person.PersonData(69, null))

        val encoded = Person.CODEC.encode(JsonTranscoder, person)
        val decoded = Person.CODEC.decode(JsonTranscoder, encoded)

        assertEquals(person, decoded)
    }

    private fun json(key: String): JsonElement {
        return JsonParser().parse(key)
    }
}