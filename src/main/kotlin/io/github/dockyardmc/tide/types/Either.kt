package io.github.dockyardmc.tide.types

sealed interface Either<L, R> {

    data class Left<L, R>(val value: L) : Either<L, R>

    data class Right<L, R>(val value: R) : Either<L, R>

    companion object {
        fun <L, R> left(value: L): Either<L, R> {
            return Left(value)
        }

        fun <L, R> right(value: R): Either<L, R> {
            return Right(value)
        }
    }
}