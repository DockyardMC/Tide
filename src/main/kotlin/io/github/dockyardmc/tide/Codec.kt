package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import io.netty.buffer.ByteBuf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass


/**
 * Codec represents combined encoder and decoder functions for a given value
 *
 * <p>Heavily inspired by <a href="https://github.com/Mojang/DataFixerUpper">Mojang/DataFixerUpper</a>,
 * licensed under the MIT license.</p>
 *
 * @param T The type representing this codec
 */
interface Codec<T> {
    val type: KClass<*>

    /**
     * Writes a given value as network type into a buffer
     *
     * @param buffer the buffer the value will be written to
     * @param value the value
     */
    fun writeNetwork(buffer: ByteBuf, value: T)

    /**
     * Reads value from a buffer
     *
     * @param buffer
     * @return [T]
     */
    fun readNetwork(buffer: ByteBuf): T

    /**
     * Writes a given value as JSON into a [JsonElement]
     *
     * @param json existing json object
     * @param value value
     * @param field field name the value will be written under. If left empty, it's written as root node
     */
    fun writeJson(json: JsonElement, value: T, field: String)

    /**
     * Reads a value from json
     *
     * @param json existing [JsonElement]
     * @param field field name the value will be read under. If left empty, it's read as root node
     * @return [T]
     */
    fun readJson(json: JsonElement, field: String): T

    /**
     * Writes json as a root node into a JsonElement
     *
     * @param json
     * @param value
     */
    fun writeJson(json: JsonElement, value: T) {
        writeJson(json, value, "")  // Empty field here indicates root
    }

    /**
     * Writes codec using provided custom [Transcoder]
     *
     * @param A
     * @param transcoder
     * @param format
     * @param value
     * @param field
     */
    fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: T, field: String)

    /**
     * Reads codec using provided custom [Transcoder]
     *
     * @param A
     * @param transcoder
     * @param format
     * @param field
     * @return [T]
     */
    fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): T

    /**
     * Reads a value from json as root node
     *
     * @param json
     * @return
     */
    fun readJson(json: JsonElement): T {
        return readJson(json, "") // Empty field here indicates root
    }

    /**
     * Makes the field optional. Optional fields are internally prefixed with boolean which indicates if they are present or not (null)
     *
     * @return [OptionalCodec]
     */
    fun optional(): Codec<T?> {
        return Companion.optional(this)
    }

    /**
     * Turns the field into a [ListCodec]. Lists are internally prefixed with var int which indicates their length
     *
     * @return [ListCodec]
     */
    fun list(): Codec<List<T>> {
        return Companion.list(this)
    }

    /**
     * Turns the field into a [MapCodec] of key (current field codec) and value (provided codec)
     *
     * @param V
     * @param valueCodec
     * @return [MapCodec]
     */
    fun <V> mapAsKeyTo(valueCodec: Codec<V>): Codec<Map<T, V>> {
        return Companion.map(this, valueCodec)
    }

    /**
     * Turns the field into a [MapCodec] of key (provided codec) and value (current field codec)
     *
     * @param K
     * @param keyCodec
     * @return [MapCodec]
     */
    fun <K> mapAsValueTo(keyCodec: Codec<K>): Codec<Map<K, T>> {
        return Companion.map(keyCodec, this)
    }


    companion object {

        val propertyCounter = AtomicInteger()

        /**
         * Returns new [ReflectiveCodec] with provided fields
         *
         * @param T
         * @param fields
         * @return [ReflectiveCodec]
         */
        inline fun <reified T : Any> of(fields: List<Field<T>>): Codec<T> {
            return ReflectiveCodec(T::class, fields)
        }

        /**
         * Returns new [ReflectiveCodec] with provided fields
         *
         * @param T
         * @param fields
         * @return [ReflectiveCodec]
         */
        inline fun <reified T : Any> of(vararg fields: Field<T>): Codec<T> {
            return ReflectiveCodec(T::class, fields.toList())
        }

        /**
         * Turns pairs of codecs and getters into Fields with default names ("property0", "property1" etc.) then returns new [ReflectiveCodec] with these fields
         *
         * @param T
         * @param fields
         * @return [ReflectiveCodec]
         */
        inline fun <reified T : Any> of(vararg fields: Pair<Codec<*>, (T) -> Any?>): Codec<T> {
            return ReflectiveCodec(T::class, fields.map { pair -> Field("property${propertyCounter.getAndIncrement()}", pair.first, pair.second) })
        }

        /**
         * Provides dls to create [ReflectiveCodec]
         *
         * @param T
         * @param builder
         * @return [ReflectiveCodec]
         */
        inline fun <reified T : Any> of(builder: CodecBuilder<T>.() -> Unit): ReflectiveCodec<T> {
            val instance = CodecBuilder<T>()
            builder.invoke(instance)
            return ReflectiveCodec(T::class, instance.getFinalFields())
        }

        /**
         * Creates [ListCodec] from the provided codec
         *
         * @param codec
         * @return [ListCodec]
         */
        fun <T> list(codec: Codec<T>): Codec<List<T>> {
            return ListCodec<T>(codec)
        }

        /**
         * Creates [MapCodec] from the provided key and value codecs
         *
         * @param keyCodec
         * @param valueCodec
         * @return [MapCodec]
         */
        fun <K, V> map(keyCodec: Codec<K>, valueCodec: Codec<V>): Codec<Map<K, V>> {
            return MapCodec<K, V>(keyCodec, valueCodec)
        }

        /**
         * Creates [OptionalCodec] from the provided codec
         *
         * @param codec
         * @return [OptionalCodec]
         */
        fun <T> optional(codec: Codec<T>): Codec<T?> {
            return OptionalCodec<T>(codec)
        }

        /**
         * Creates [EnumCodec] from the provided class
         *
         * @param kClass
         * @return [EnumCodec]
         */

        fun <T : Enum<T>> enum(kClass: KClass<out Enum<T>>): EnumCodec<T> {
            return EnumCodec<T>(kClass)
        }
    }
}