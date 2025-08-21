package io.github.dockyardmc.tide.stream

import io.netty.buffer.ByteBuf

class OptionalStreamCodec<T>(val inner: StreamCodec<T>) : StreamCodec<T?> {

    override fun write(buffer: ByteBuf, value: T?) {
        StreamCodec.BOOLEAN.write(buffer, value != null)
        if (value != null) inner.write(buffer, value)
    }

    override fun read(buffer: ByteBuf): T? {
        val isNull = StreamCodec.BOOLEAN.read(buffer)
        return if (!isNull) inner.read(buffer) else null
    }

}