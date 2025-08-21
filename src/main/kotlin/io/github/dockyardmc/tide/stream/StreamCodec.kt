package io.github.dockyardmc.tide.stream

import io.github.dockyardmc.tide.codec.CodecUtils
import io.netty.buffer.ByteBuf
import java.util.*

interface StreamCodec<T> {
    fun write(buffer: ByteBuf, value: T)
    fun read(buffer: ByteBuf): T

    fun optional(): OptionalStreamCodec<T> {
        return OptionalStreamCodec<T>(this)
    }

    fun default(default: T): DefaultStreamCodec<T> {
        return DefaultStreamCodec<T>(this, default)
    }

    fun <S> transform(from: (S) -> T, to: (T) -> S): TransformativeStreamCodec<T, S> {
        return TransformativeStreamCodec<T, S>(this, to, from)
    }

    fun <V> mapTo(valueCodec: StreamCodec<V>): MapStreamCodec<T, V> {
        return MapStreamCodec<T, V>(this, valueCodec)
    }

    fun list(): ListStreamCodec<T> {
        return ListStreamCodec<T>(this)
    }

    companion object {
        val UNIT = object : StreamCodec<Unit> {

            override fun write(buffer: ByteBuf, value: Unit) {}

            override fun read(buffer: ByteBuf) {}
        }

        val BOOLEAN = object : StreamCodec<Boolean> {

            override fun write(buffer: ByteBuf, value: Boolean) {
                buffer.writeBoolean(value)
            }

            override fun read(buffer: ByteBuf): Boolean {
                return buffer.readBoolean()
            }
        }

        val BYTE = object : StreamCodec<Byte> {

            override fun write(buffer: ByteBuf, value: Byte) {
                buffer.writeByte(value.toInt())
            }

            override fun read(buffer: ByteBuf): Byte {
                return buffer.readByte()
            }
        }

        val SHORT = object : StreamCodec<Short> {

            override fun write(buffer: ByteBuf, value: Short) {
                buffer.writeShort(value.toInt())
            }

            override fun read(buffer: ByteBuf): Short {
                return buffer.readShort()
            }
        }

        val INT = object : StreamCodec<Int> {

            override fun write(buffer: ByteBuf, value: Int) {
                buffer.writeInt(value)
            }

            override fun read(buffer: ByteBuf): Int {
                return buffer.readInt()
            }
        }

        val VAR_INT = object : StreamCodec<Int> {

            override fun write(buffer: ByteBuf, value: Int) {
                CodecUtils.writeVarInt(buffer, value)
            }

            override fun read(buffer: ByteBuf): Int {
                return CodecUtils.readVarInt(buffer)
            }
        }

        val LONG = object : StreamCodec<Long> {

            override fun write(buffer: ByteBuf, value: Long) {
                buffer.writeLong(value)
            }

            override fun read(buffer: ByteBuf): Long {
                return buffer.readLong()
            }
        }

        val FLOAT = object : StreamCodec<Float> {

            override fun write(buffer: ByteBuf, value: Float) {
                buffer.writeFloat(value)
            }

            override fun read(buffer: ByteBuf): Float {
                return buffer.readFloat()
            }
        }

        val DOUBLE = object : StreamCodec<Double> {

            override fun write(buffer: ByteBuf, value: Double) {
                buffer.writeDouble(value)
            }

            override fun read(buffer: ByteBuf): Double {
                return buffer.readDouble()
            }

        }

        val STRING = object : StreamCodec<String> {

            override fun write(buffer: ByteBuf, value: String) {
                CodecUtils.writeString(buffer, value)
            }

            override fun read(buffer: ByteBuf): String {
                return CodecUtils.readString(buffer)
            }

        }

        val RAW_BYTES = object : StreamCodec<ByteBuf> {

            override fun write(buffer: ByteBuf, value: ByteBuf) {
                buffer.writeBytes(value)
            }

            override fun read(buffer: ByteBuf): ByteBuf {
                return buffer.readBytes(buffer.readableBytes())
            }
        }

        val BYTE_ARRAY = object : StreamCodec<ByteBuf> {

            override fun write(buffer: ByteBuf, value: ByteBuf) {
                VAR_INT.write(buffer, value.readableBytes())
                RAW_BYTES.write(buffer, value)
            }

            override fun read(buffer: ByteBuf): ByteBuf {
                val size = VAR_INT.read(buffer)
                return buffer.readBytes(size)
            }
        }

        val UUID = object : StreamCodec<UUID> {

            override fun write(buffer: ByteBuf, value: UUID) {
                LONG.write(buffer, value.mostSignificantBits)
                LONG.write(buffer, value.leastSignificantBits)
            }

            override fun read(buffer: ByteBuf): UUID {
                val mostSignificant = LONG.read(buffer)
                val leastSignificant = LONG.read(buffer)
                return UUID(mostSignificant, leastSignificant)
            }
        }

        val UUID_STRING = STRING.transform<UUID>({ uuid -> uuid.toString() }, { string -> java.util.UUID.fromString(string) })

        inline fun <reified E : Enum<E>> enum(): EnumStreamCodec<E> {
            return EnumStreamCodec<E>(E::class)
        }

        inline fun <reified E : Enum<E>> enumString(): StreamCodec<E> {
            return STRING.transform<E>({ enum -> enum.name.lowercase() }, { string -> enumValueOf<E>(string) })
        }

        fun fixedBitSet(length: Int): FixedBitSetStreamCodec {
            return FixedBitSetStreamCodec(length)
        }

        fun <T> lengthPrefixed(codec: StreamCodec<T>): LengthPrefixedStreamCodec<T> {
            return LengthPrefixedStreamCodec<T>(codec)
        }

        fun <L, R> either(leftCodec: StreamCodec<L>, rightCodec: StreamCodec<R>): EitherStreamCodec<L, R> {
            return EitherStreamCodec<L, R>(leftCodec, rightCodec)
        }
    }
}