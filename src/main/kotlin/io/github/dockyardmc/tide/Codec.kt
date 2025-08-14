package io.github.dockyardmc.tide

import java.util.*

interface Codec<T> {
    fun <D> encode(transcoder: Transcoder<D>, value: T): D
    fun <D> decode(transcoder: Transcoder<D>, value: D): T

    fun <S> transform(from: (T) -> S, to: (S) -> T): TransformativeCodec<T, S> {
        return TransformativeCodec<T, S>(this, from, to)
    }

    companion object {
        val BOOLEAN: Codec<Boolean> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeBoolean(value) },
            { transcoder, value -> transcoder.decodeBoolean(value) }
        )

        val BYTE: Codec<Byte> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeByte(value) },
            { transcoder, value -> transcoder.decodeByte(value) }
        )

        val SHORT: Codec<Short> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeShort(value) },
            { transcoder, value -> transcoder.decodeShort(value) }
        )

        val INT: Codec<Int> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeInt(value) },
            { transcoder, value -> transcoder.decodeInt(value) }
        )

        val LONG: Codec<Long> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeLong(value) },
            { transcoder, value -> transcoder.decodeLong(value) }
        )

        val FLOAT: Codec<Float> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeFloat(value) },
            { transcoder, value -> transcoder.decodeFloat(value) }
        )

        val DOUBLE: Codec<Double> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeDouble(value) },
            { transcoder, value -> transcoder.decodeDouble(value) }
        )

        val STRING: Codec<String> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeString(value) },
            { transcoder, value -> transcoder.decodeString(value) }
        )

        val BYTE_ARRAY: Codec<ByteArray> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeByteArray(value) },
            { transcoder, value -> transcoder.decodeByteArray(value) }
        )

        val INT_ARRAY: Codec<IntArray> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeIntArray(value) },
            { transcoder, value -> transcoder.decodeIntArray(value) }
        )

        val LONG_ARRAY: Codec<LongArray> = PrimitiveCodec(
            { transcoder, value -> transcoder.encodeLongArray(value) },
            { transcoder, value -> transcoder.decodeLongArray(value) }
        )

        val UUID: Codec<UUID> = INT_ARRAY.transform(CodecUtils::intArrayToUuid, CodecUtils::uuidToIntArray)

        val UUID_STRING: Codec<UUID> = STRING.transform(java.util.UUID::fromString, java.util.UUID::toString)

        inline fun <reified E : Enum<E>> enum(): Codec<E> {
            return STRING.transform(
                { value -> E::class.java.enumConstants.first { const -> const.name == value.uppercase() } },
                { value -> value.name.lowercase() }
            )
        }
    }

    private class PrimitiveCodec<T>(
        private val encoder: (Transcoder<Any?>, T) -> Any?,
        private val decoder: (Transcoder<Any?>, Any?) -> T
    ) : Codec<T> {

        @Suppress("UNCHECKED_CAST")
        override fun <D> encode(transcoder: Transcoder<D>, value: T): D {
            return encoder.invoke(transcoder as Transcoder<Any?>, value) as D
        }


        @Suppress("UNCHECKED_CAST")
        override fun <D> decode(transcoder: Transcoder<D>, value: D): T {
            return decoder.invoke(transcoder as Transcoder<Any?>, value)
        }
    }
}