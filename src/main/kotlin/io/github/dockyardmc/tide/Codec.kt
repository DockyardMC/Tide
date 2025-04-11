package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

interface Codec<T> {
    val type: KClass<*>
    fun writeNetwork(buffer: ByteBuf, value: T)
    fun readNetwork(buffer: ByteBuf): T
    fun writeJson(json: JsonElement, value: T, field: String)
    fun readJson(json: JsonElement, field: String): T

    fun writeJson(json: JsonElement, value: T) {
        writeJson(json, value, "")  // Empty field here indicates root
    }

    companion object {

        val propertyCounter = AtomicInteger()

        inline fun <reified T : Any> of(vararg fields: Field<T>): Codec<T> {
            return ReflectiveCodec(T::class, fields.toList())
        }

        inline fun <reified T : Any> of(vararg fields: Pair<Codec<*>, (T) -> Any?>): Codec<T> {
            return ReflectiveCodec(T::class, fields.map { pair -> Field("property${propertyCounter.getAndIncrement()}", pair.first, pair.second) })
        }

        fun <T> list(codec: Codec<T>): Codec<List<T>> {
            return ListCodec<T>(codec)
        }

        fun <K, V> map(keyCodec: Codec<K>, valueCodec: Codec<V>): Codec<Map<K, V>> {
            return MapCodec<K, V>(keyCodec, valueCodec)
        }

        fun <T> optional(codec: Codec<T>): Codec<T?> {
            return OptionalCodec<T>(codec)
        }

        fun <T : Enum<T>> enum(kClass: KClass<out Enum<T>>): EnumCodec<T> {
            return EnumCodec<T>(kClass)
        }
    }
}