package io.github.dockyardmc.tide.stream

import io.netty.buffer.ByteBuf

class ListStreamCodec<T>(val inner: StreamCodec<T>) : StreamCodec<List<T>> {

    override fun write(buffer: ByteBuf, value: List<T>) {
        StreamCodec.VAR_INT.write(buffer, value.size)
        value.forEach { item ->
            inner.write(buffer, item)
        }
    }

    override fun read(buffer: ByteBuf): List<T> {
        val size = StreamCodec.VAR_INT.read(buffer)
        val list = mutableListOf<T>()
        for (i in 0 until size) {
            list.add(inner.read(buffer))
        }
        return list
    }
}