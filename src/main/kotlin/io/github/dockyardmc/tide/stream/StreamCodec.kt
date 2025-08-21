package io.github.dockyardmc.tide.stream

import io.netty.buffer.ByteBuf

interface StreamCodec<T> {
    fun write(buffer: ByteBuf, value: T)
    fun read(buffer: ByteBuf): T
}