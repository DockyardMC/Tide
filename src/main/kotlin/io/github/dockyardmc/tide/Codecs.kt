package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.inv

object Codecs {

    object Byte : PrimitiveCodec<kotlin.Byte>(kotlin.Byte::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Byte) {
            buffer.writeByte(value.toInt())
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Byte {
            return buffer.readByte()
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Byte {
            return json.getPrimitive<kotlin.Int>(field).toByte()
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Byte {
            return transcoder.readByte(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Byte, field: kotlin.String) {
            transcoder.writeByte(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Byte, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }
    }

    object Double : PrimitiveCodec<kotlin.Double>(kotlin.Double::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Double) {
            buffer.writeDouble(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Double {
            return buffer.readDouble()
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Double {
            return json.getPrimitive<kotlin.Double>(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Double {
            return transcoder.readDouble(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Double, field: kotlin.String) {
            transcoder.writeDouble(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Double, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }
    }

    object Float : PrimitiveCodec<kotlin.Float>(kotlin.Float::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Float) {
            buffer.writeFloat(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Float {
            return buffer.readFloat()
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Float {
            return json.getPrimitive<kotlin.Float>(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Float {
            return transcoder.readFloat(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Float, field: kotlin.String) {
            transcoder.writeFloat(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Float, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }
    }

    object Long : PrimitiveCodec<kotlin.Long>(kotlin.Long::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Long) {
            buffer.writeLong(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Long {
            return buffer.readLong()
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Long {
            return json.getPrimitive<kotlin.Long>(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Long {
            return transcoder.readLong(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Long, field: kotlin.String) {
            transcoder.writeLong(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Long, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }
    }

    object UUID : PrimitiveCodec<java.util.UUID>(java.util.UUID::class) {

        override fun writeNetwork(buffer: ByteBuf, value: java.util.UUID) {
            Long.writeNetwork(buffer, value.mostSignificantBits)
            Long.writeNetwork(buffer, value.leastSignificantBits)
        }

        override fun readNetwork(buffer: ByteBuf): java.util.UUID {
            val most = Long.readNetwork(buffer)
            val least = Long.readNetwork(buffer)
            return UUID(most, least)
        }

        override fun readJson(json: JsonElement, field: kotlin.String): java.util.UUID {
            return java.util.UUID.fromString(json.getPrimitive<kotlin.String>(field))
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): java.util.UUID {
            return transcoder.readUUID(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: java.util.UUID, field: kotlin.String) {
            transcoder.writeUUID(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: java.util.UUID, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value.toString())
        }

    }

    object ByteArray : PrimitiveCodec<kotlin.ByteArray>(kotlin.ByteArray::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.ByteArray) {
            VarInt.writeNetwork(buffer, value.size)
            buffer.writeBytes(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.ByteArray {
            val size = VarInt.readNetwork(buffer)
            val readBytes = buffer.readBytes(size)
            val byteArray = ByteArray(readBytes.readableBytes())
            readBytes.readBytes(byteArray)
            return byteArray
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.ByteArray {
            return Base64.getDecoder().decode(json.getPrimitive<kotlin.String>(field))
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.ByteArray {
            return transcoder.readByteArray(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.ByteArray, field: kotlin.String) {
            transcoder.writeByteArray(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.ByteArray, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, Base64.getEncoder().encodeToString(value))
        }

    }

    object VarInt : PrimitiveCodec<kotlin.Int>(kotlin.Int::class) {
        private const val SEGMENT_BITS: kotlin.Byte = 0x7F
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
            var currentByte: kotlin.Byte
            while (buffer.isReadable) {
                currentByte = buffer.readByte()
                value = value or (currentByte.toInt() and SEGMENT_BITS.toInt() shl position)
                if (currentByte.toInt() and CONTINUE_BIT == 0) break
                position += 7
                if (position >= 32) throw RuntimeException("VarInt is too big")
            }
            return value
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Int {
            return json.getPrimitive<kotlin.Int>(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Int {
            return transcoder.readVarInt(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Int, field: kotlin.String) {
            transcoder.writeVarInt(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Int, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }

    }

    object Boolean : PrimitiveCodec<kotlin.Boolean>(kotlin.Boolean::class) {

        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Boolean) {
            buffer.writeBoolean(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Boolean {
            return buffer.readBoolean()
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Boolean {
            return json.getPrimitive(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Boolean {
            return transcoder.readBoolean(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Boolean, field: kotlin.String) {
            transcoder.writeBoolean(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Boolean, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
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

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.String {
            return json.getPrimitive<kotlin.String>(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.String {
            return transcoder.readString(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.String, field: kotlin.String) {
            return transcoder.writeString(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.String, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }
    }

    object Int : PrimitiveCodec<kotlin.Int>(kotlin.Int::class) {
        override fun writeNetwork(buffer: ByteBuf, value: kotlin.Int) {
            buffer.writeInt(value)
        }

        override fun readNetwork(buffer: ByteBuf): kotlin.Int {
            return buffer.readInt()
        }

        override fun readJson(json: JsonElement, field: kotlin.String): kotlin.Int {
            return json.getPrimitive<kotlin.Int>(field)
        }

        override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: kotlin.String): kotlin.Int {
            return transcoder.readInt(format, field)
        }

        override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: kotlin.Int, field: kotlin.String) {
            transcoder.writeInt(format, field, value)
        }

        override fun writeJson(json: JsonElement, value: kotlin.Int, field: kotlin.String) {
            json.asObjectOrThrow().addProperty(field, value)
        }
    }

}