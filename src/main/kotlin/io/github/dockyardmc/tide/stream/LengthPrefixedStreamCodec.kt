package io.github.dockyardmc.tide.stream

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class LengthPrefixedStreamCodec<T>(val inner: StreamCodec<T>) : StreamCodec<T> {

    override fun write(buffer: ByteBuf, value: T) {
        val innerBuffer = Unpooled.buffer()
        inner.write(innerBuffer, value)
        StreamCodec.BYTE_ARRAY.write(buffer, innerBuffer)
    }

    override fun read(buffer: ByteBuf): T {
        val innerBuffer = StreamCodec.BYTE_ARRAY.read(buffer)
        return inner.read(innerBuffer)
    }

}