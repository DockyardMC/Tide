package io.github.dockyardmc.tide

import io.netty.buffer.ByteBuf
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

interface Codec<T> {
    val type: KClass<*>
    fun writeNetwork(buffer: ByteBuf, value: T)
    fun readNetwork(buffer: ByteBuf): T
    fun writeJson(json: JsonObject, value: T, field: String)
    fun readJson(json: JsonObject, field: String): T

    companion object {
        inline fun <reified T : Any> of(vararg fields: Field<T>): Codec<T> {
            return ReflectiveCodec(T::class, fields.toList())
        }

        inline fun <reified T : Any> of(vararg fields: Pair<Codec<*>, (T) -> Any?>): Codec<T> {
            return ReflectiveCodec(T::class, fields.map { pair -> Field("_", pair.first, pair.second) })
        }
    }
}