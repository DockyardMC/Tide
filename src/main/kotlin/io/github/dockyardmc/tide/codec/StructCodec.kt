package io.github.dockyardmc.tide.codec

import io.github.dockyardmc.tide.transcoder.Transcoder

interface StructCodec<R> : Codec<R> {

    fun <T> encodeToMap(transcoder: Transcoder<T>, value: R, map: Transcoder.VirtualMapBuilder<T>): T

    fun <T> decodeFromMap(transcoder: Transcoder<T>, map: Transcoder.VirtualMap<T>): R

    override fun <T> encode(transcoder: Transcoder<T>, value: R): T {
        return encodeToMap<T>(transcoder, value, transcoder.encodeMap())
    }

    override fun <D> decode(transcoder: Transcoder<D>, value: D): R {
        return decodeFromMap(transcoder, transcoder.decodeMap(value))
    }

    companion object {
        const val INLINE = "_inline_"

        fun <R> of(new: () -> R): StructCodec<R> {
            return object : StructCodec<R> {

                override fun <T> encodeToMap(transcoder: Transcoder<T>, value: R, map: Transcoder.VirtualMapBuilder<T>): T {
                    return transcoder.emptyMap()
                }

                override fun <T> decodeFromMap(transcoder: Transcoder<T>, map: Transcoder.VirtualMap<T>): R {
                    return new.invoke()
                }

            }
        }

        fun <P1, R> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            new: (P1) -> R
        ): StructCodec<R> {
            return object : StructCodec<R> {

                override fun <T> encodeToMap(transcoder: Transcoder<T>, value: R, map: Transcoder.VirtualMapBuilder<T>): T {
                    put(transcoder, codec1, map, name1, getter1.invoke(value))
                    return map.build()
                }

                override fun <T> decodeFromMap(transcoder: Transcoder<T>, map: Transcoder.VirtualMap<T>): R {
                    val result1 = get(transcoder, codec1, name1, map)
                    return new.invoke(result1)
                }

            }
        }

        fun <P1, P2, R> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            new: (P1, P2) -> R
        ): StructCodec<R> {
            return object : StructCodec<R> {

                override fun <T> encodeToMap(transcoder: Transcoder<T>, value: R, map: Transcoder.VirtualMapBuilder<T>): T {
                    put(transcoder, codec1, map, name1, getter1.invoke(value))
                    put(transcoder, codec2, map, name2, getter2.invoke(value))
                    return map.build()
                }

                override fun <T> decodeFromMap(transcoder: Transcoder<T>, map: Transcoder.VirtualMap<T>): R {
                    val result1 = get(transcoder, codec1, name1, map)
                    val result2 = get(transcoder, codec2, name2, map)
                    return new.invoke(result1, result2)
                }

            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <D, T> put(transcoder: Transcoder<D>, codec: Codec<T>, map: Transcoder.VirtualMapBuilder<D>, key: String, value: T): D {

            if (key == INLINE) {
                val encodeCodec: Codec<T> = if (codec is OptionalCodec<*>) (codec.inner as Codec<T>) else if (codec is DefaultCodec<T>) codec.inner else codec
                if (encodeCodec !is StructCodec<T>) {
                    throw Codec.EncodingException("provided codec for $key is not StructCodec")
                }

                return encodeCodec.encodeToMap(transcoder, value, map)
            }

            val result = codec.encode(transcoder, value)
            map.put(key, result)
            return result
        }

        @Suppress("UNCHECKED_CAST")
        private fun <D, T> get(transcoder: Transcoder<D>, codec: Codec<T>, key: String, map: Transcoder.VirtualMap<D>): T {

            if (key == INLINE) {
                val decodeCodec = if (codec is OptionalCodec<*>) codec.inner else if (codec is DefaultCodec<*>) codec.inner else codec
                if (decodeCodec !is StructCodec<*>) {
                    throw Codec.DecodingException("provided codec for $key is not StructCodec")
                }

                val result = runCatching {
                    decodeCodec.decodeFromMap(transcoder, map)
                }
                if (result.isFailure && codec is DefaultCodec<*>) {
                    return codec.default as T
                }
                if (result.isFailure && codec is OptionalCodec<*>) {
                    return null as T
                }
                return result.getOrThrow() as T
            }

            if (codec is DefaultCodec<*> && !map.hasValue(key)) {
                return codec.default as T
            }

            if (codec is OptionalCodec<*> && !map.hasValue(key)) {
                return null as T
            }

            return codec.decode(transcoder, map.getValue(key))
        }
    }
}