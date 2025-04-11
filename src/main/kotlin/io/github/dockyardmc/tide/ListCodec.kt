package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class ListCodec<T>(private val elementCodec: Codec<T>) : Codec<List<T>> {
    override val type: KClass<*> = List::class

    override fun readNetwork(buffer: ByteBuf): List<T> {
        TODO("Not yet implemented")
    }

    override fun readJson(json: JsonObject, field: String): List<T> {
        TODO("Not yet implemented")
    }

    override fun writeJson(json: JsonObject, value: List<T>, field: String) {
        TODO("Not yet implemented")
    }

    override fun writeNetwork(buffer: ByteBuf, value: List<T>) {
//        buffer.writeV
    }
}