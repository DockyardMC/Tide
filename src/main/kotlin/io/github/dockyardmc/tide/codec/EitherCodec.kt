package io.github.dockyardmc.tide.codec

import io.github.dockyardmc.tide.types.Either

class EitherCodec<L, R>(val leftCodec: Codec<L>, val rightCodec: Codec<R>) : Codec<Either<L, R>> {

    override fun <D> encode(transcoder: Transcoder<D>, value: Either<L, R>): D {
        return when (value) {
            is Either.Left -> leftCodec.encode(transcoder, value.value)
            is Either.Right -> rightCodec.encode(transcoder, value.value)
        }
    }

    override fun <D> decode(transcoder: Transcoder<D>, value: D): Either<L, R> {
        val result = runCatching {
            leftCodec.decode(transcoder, value)
        }
        return if (result.isSuccess) {
            Either.left(result.getOrThrow())
        } else {
            Either.right(rightCodec.decode(transcoder, value))
        }
    }
}