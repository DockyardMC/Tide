package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets

object Primitives {

    object StringCodec : PrimitiveCodec<String>(String::class) {
        override fun writeNetwork(buffer: ByteBuf, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            buffer.writeInt(bytes.size).writeBytes(bytes)
        }

        override fun readNetwork(buffer: ByteBuf) =
            String(ByteArray(buffer.readInt()).apply { buffer.readBytes(this) })

        override fun readJson(json: JsonObject, field: String): String {
            return json.getPrimitive<String>(field)
        }

        override fun writeJson(json: JsonObject, value: String, field: String) {
            json.addProperty(field, value)
        }
    }

    object IntCodec : PrimitiveCodec<Int>(Int::class) {
        override fun writeNetwork(buffer: ByteBuf, value: Int) {
            buffer.writeInt(value)
        }

        override fun readNetwork(buffer: ByteBuf): Int {
            return buffer.readInt()
        }

        override fun readJson(json: JsonObject, field: String): Int {
            return json.getPrimitive<Int>(field)
        }

        override fun writeJson(json: JsonObject, value: Int, field: String) {
            json.addProperty(field, value)
        }
    }

}