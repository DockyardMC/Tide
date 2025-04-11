package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import java.nio.charset.StandardCharsets
import kotlin.experimental.inv

object Primitives {

    object VarInt : PrimitiveCodec<kotlin.Int>(kotlin.Int::class) {
        private const val SEGMENT_BITS: Byte = 0x7F
        private const val CONTINUE_BIT = 0x80

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Int) {
            var varIntValue = value
            while (true) {
                if (varIntValue and SEGMENT_BITS.inv().toInt() == 0) {
                    buffer.writeByte(varIntValue)
                    return
                }
                buffer.writeByte(value and SEGMENT_BITS.toInt() or CONTINUE_BIT)
                varIntValue = varIntValue ushr 7
            }
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Int {
            var value = 0
            var position = 0
            var currentByte: Byte
            while (buffer.isReadable) {
                currentByte = buffer.readByte()
                value = value or (currentByte.toInt() and SEGMENT_BITS.toInt() shl position)
                if (currentByte.toInt() and CONTINUE_BIT == 0) break
                position += 7
                if (position >= 32) throw RuntimeException("VarInt is too big")
            }
            return value
        }

        override fun readJson(json: JsonObject, field: kotlin.String): kotlin.Int {
            return json.getPrimitive<kotlin.Int>(field)
        }

        override fun writeJson(json: JsonObject, value: kotlin.Int, field: kotlin.String) {
            json.addProperty(field, value)
        }

    }

    object Boolean : PrimitiveCodec<kotlin.Boolean>(kotlin.Boolean::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Boolean) {
            buffer.writeBoolean(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Boolean {
            return buffer.readBoolean()
        }

        override fun readJson(json: JsonObject, field: kotlin.String): kotlin.Boolean {
            return json.getPrimitive(field)
        }

        override fun writeJson(json: JsonObject, value: kotlin.Boolean, field: kotlin.String) {
            json.addProperty(field, value)
        }

    }

    object String : PrimitiveCodec<kotlin.String>(kotlin.String::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.String) {

            val utf8Bytes = value.toByteArray(StandardCharsets.UTF_8)
            VarInt.writeNetwork(buffer, utf8Bytes.size)
            buffer.writeBytes(utf8Bytes)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.String {
            val maxSize = Short.MAX_VALUE * 3
            val size = VarInt.readNetwork(buffer)
            if (size > maxSize) throw DecoderException("The received string was longer than the allowed $maxSize ($size > $maxSize)")
            if (size < 0) throw DecoderException("The received string's length was smaller than 0")
            val string = buffer.toString(buffer.readerIndex(), size, StandardCharsets.UTF_8)
            buffer.readerIndex(buffer.readerIndex() + size)
            if (string.length > Short.MAX_VALUE) throw DecoderException("The received string was longer than the allowed (${string.length} > ${Short.MAX_VALUE})")
            return string
        }

        override fun readJson(json: JsonObject, field: kotlin.String): kotlin.String {
            return json.getPrimitive<kotlin.String>(field)
        }

        override fun writeJson(json: JsonObject, value: kotlin.String, field: kotlin.String) {
            json.addProperty(field, value)
        }
    }

    object Int : PrimitiveCodec<kotlin.Int>(kotlin.Int::class) {
        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Int) {
            buffer.writeInt(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Int {
            return buffer.readInt()
        }

        override fun readJson(json: JsonObject, field: kotlin.String): kotlin.Int {
            return json.getPrimitive<kotlin.Int>(field)
        }

        override fun writeJson(json: JsonObject, value: kotlin.Int, field: kotlin.String) {
            json.addProperty(field, value)
        }
    }

}