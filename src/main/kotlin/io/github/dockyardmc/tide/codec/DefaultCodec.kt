package io.github.dockyardmc.tide.codec

class DefaultCodec<T>(val inner: Codec<T>, val default: T) : Codec<T> {

    override fun <D> encode(transcoder: Transcoder<D>, value: T): D {
        return inner.encode(transcoder, value ?: default)
    }

    override fun <D> decode(transcoder: Transcoder<D>, value: D): T {
        val result = runCatching {
            inner.decode(transcoder, value)
        }
        if (result.isSuccess) return result.getOrThrow()
        return default
    }
}