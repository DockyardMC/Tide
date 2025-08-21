package io.github.dockyardmc.tide.codec

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import java.nio.charset.StandardCharsets
import java.util.*

object CodecUtils {

    const val SEGMENT_BITS: Int = 0x7F
    const val CONTINUE_BIT: Int = 0x80
    const val MAXIMUM_VAR_INT_SIZE = 5

    fun uuidToIntArray(uuid: UUID): IntArray {
        val uuidMost = uuid.mostSignificantBits
        val uuidLeast = uuid.leastSignificantBits
        return intArrayOf(
            (uuidMost shr 32).toInt(),
            uuidMost.toInt(),
            (uuidLeast shr 32).toInt(),
            uuidLeast.toInt()
        )
    }

    fun intArrayToUuid(array: IntArray): UUID {
        val uuidMost = array[0].toLong() shl 32 or (array[1].toLong() and 0xFFFFFFFFL)
        val uuidLeast = array[2].toLong() shl 32 or (array[3].toLong() and 0xFFFFFFFFL)

        return UUID(uuidMost, uuidLeast)
    }

    fun readVarInt(buffer: ByteBuf): Int {
        val readable = buffer.readableBytes()
        if (readable == 0) throw Codec.DecodingException("Invalid VarInt")

        // decode only one byte first as this is the most common size of varints
        var current = buffer.readByte().toInt()
        if ((current and CONTINUE_BIT) != 128) {
            return current
        }

        // no point in while loop that has higher overhead instead of for loop with max size of the varint
        val maxRead = MAXIMUM_VAR_INT_SIZE.coerceAtMost(readable)
        var varInt = current and SEGMENT_BITS
        for (i in 1..<maxRead) {
            current = buffer.readByte().toInt()
            varInt = varInt or ((current and SEGMENT_BITS) shl i * 7)
            if (current and CONTINUE_BIT != 128) {
                return varInt
            }
        }
        throw Codec.DecodingException("Invalid VarInt")
    }

    // https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
    // little bit modified to write bytes directly because kotlin fucks up the byte order
    fun writeVarInt(buffer: ByteBuf, int: Int) {
        when {
            // 1-byte
            int and (-1 shl 7) == 0 -> {
                buffer.writeByte(int)
            }

            // 2-byte
            int and (-1 shl 14) == 0 -> {
                val w = (int and SEGMENT_BITS or CONTINUE_BIT) shl 8 or (int ushr 7)
                buffer.writeShort(w)
            }

            // 3-byte
            int and (-1 shl 21) == 0 -> {
                buffer.writeByte(int and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte((int ushr 7) and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte(int ushr 14)
            }

            // 4-byte
            int and (-1 shl 28) == 0 -> {
                buffer.writeByte(int and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte((int ushr 7) and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte((int ushr 14) and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte(int ushr 21)
            }

            // 5-byte
            else -> {
                buffer.writeByte(int and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte((int ushr 7) and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte((int ushr 14) and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte((int ushr 21) and SEGMENT_BITS or CONTINUE_BIT)
                buffer.writeByte(int ushr 28)
            }
        }
    }

    fun readString(buffer: ByteBuf, i: Int): String {
        val maxSize = i * 3
        val size = readVarInt(buffer)
        if (size > maxSize) throw DecoderException("The received string was longer than the allowed $maxSize ($size > $maxSize)")
        if (size < 0) throw DecoderException("The received string's length was smaller than 0")
        val string = buffer.toString(buffer.readerIndex(), size, StandardCharsets.UTF_8)
        buffer.readerIndex(buffer.readerIndex() + size)
        if (string.length > i) throw DecoderException("The received string was longer than the allowed (${string.length} > $i)")
        return string
    }

    fun readString(buffer: ByteBuf) = this.readString(buffer, Short.MAX_VALUE.toInt())

    fun writeString(buffer: ByteBuf, text: String): ByteBuf {
        val utf8Bytes = text.toByteArray(StandardCharsets.UTF_8)
        val length = utf8Bytes.size
        writeVarInt(buffer, length)
        buffer.writeBytes(utf8Bytes)
        return buffer
    }
}

fun ByteBuf.toByteArraySafe(): ByteArray {
    if (this.hasArray()) {
        return this.array()
    }

    val bytes = ByteArray(this.readableBytes())
    this.getBytes(this.readerIndex(), bytes)

    return bytes
}

fun ByteArray.toByteBuf(): ByteBuf = Unpooled.copiedBuffer(this)
