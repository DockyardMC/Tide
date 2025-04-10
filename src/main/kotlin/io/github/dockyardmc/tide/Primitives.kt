package io.github.dockyardmc.tide

import io.netty.buffer.ByteBuf
import kotlinx.serialization.json.JsonObject
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
            TODO("Not yet implemented")
        }

        override fun writeJson(json: JsonObject, value: String, field: String) {
            TODO("Not yet implemented")
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
            TODO("Not yet implemented")
        }

        override fun writeJson(json: JsonObject, value: Int, field: String) {
            TODO("Not yet implemented")
        }
    }

}