package io.github.dockyardmc.tide

import com.google.gson.JsonElement
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

    fun optional(): Codec<T?> {
        return Companion.optional(this)
    }

    fun list(): Codec<List<T>> {
        return Companion.list(this)
    }

    fun <V> mapAsKeyTo(valueCodec: Codec<V>): Codec<Map<T, V>> {
        return Companion.map(this, valueCodec)
    }

    fun <K> mapAsValueTo(keyCodec: Codec<K>): Codec<Map<K, T>> {
        return Companion.map(keyCodec, this)
    }


    companion object {

        val propertyCounter = AtomicInteger()

        inline fun <reified T : Any> of(fields: List<Field<T>>): Codec<T> {
            return ReflectiveCodec(T::class, fields)
        }

        inline fun <reified T : Any> of(vararg fields: Field<T>): Codec<T> {
            return ReflectiveCodec(T::class, fields.toList())
        }

        inline fun <reified T : Any> of(vararg fields: Pair<Codec<*>, (T) -> Any?>): Codec<T> {
            return ReflectiveCodec(T::class, fields.map { pair -> Field("property${propertyCounter.getAndIncrement()}", pair.first, pair.second) })
        }

        inline fun <reified T: Any> of(builder: CodecBuilder<T>.() -> Unit): ReflectiveCodec<T> {
            val instance = CodecBuilder<T>()
            builder.invoke(instance)
            return ReflectiveCodec(T::class, instance.getBuiltFields())
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