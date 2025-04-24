package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import cz.lukynka.prettylog.LogType
import cz.lukynka.prettylog.log
import io.netty.buffer.ByteBuf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.system.measureTimeMillis


/**
 * Codec represents combined encoder and decoder functions for a given value
 *
 * <p>Heavily inspired by <a href="https://github.com/Mojang/DataFixerUpper">Mojang/DataFixerUpper</a>,
 * licensed under the MIT license.</p>
 *
 * @param T The type representing this codec
 */
interface Codec<T> {

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

        init {
            val time = measureTimeMillis {
                Codecs.Int
                Codecs.Double
                Codecs.Float
                Codecs.UUID
                Codecs.ByteArray
                Codecs.Byte
                Codecs.Boolean
                Codecs.VarInt
                Codecs.String
                EnumCodec::class.companionObject
                ListCodec::class.companionObject
                MapCodec::class.companionObject
                OptionalCodec::class.companionObject
            }
            log("\"Cache default codecs\" took ${time}ms", LogType.DEBUG)
        }

        val propertyCounter = AtomicInteger()

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

        inline fun <reified T> enum(): EnumCodec<T> {
            return EnumCodec<T>(T::class as KClass<out Enum<*>>)
        }


        fun <R, P1> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            constructor: (P1) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                return constructor.invoke(p1)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                return constructor.invoke(p1)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                return constructor.invoke(p1)
            }

        }


        fun <R, P1, P2> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            constructor: (P1, P2) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                return constructor.invoke(p1, p2)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                return constructor.invoke(p1, p2)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                return constructor.invoke(p1, p2)
            }

        }


        fun <R, P1, P2, P3> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            constructor: (P1, P2, P3) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                return constructor.invoke(p1, p2, p3)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                return constructor.invoke(p1, p2, p3)
            }

        }


        fun <R, P1, P2, P3, P4> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            constructor: (P1, P2, P3, P4) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                return constructor.invoke(p1, p2, p3, p4)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                return constructor.invoke(p1, p2, p3, p4)
            }

        }


        fun <R, P1, P2, P3, P4, P5> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            constructor: (P1, P2, P3, P4, P5) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                return constructor.invoke(p1, p2, p3, p4, p5)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                return constructor.invoke(p1, p2, p3, p4, p5)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            constructor: (P1, P2, P3, P4, P5, P6) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                return constructor.invoke(p1, p2, p3, p4, p5, p6)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                return constructor.invoke(p1, p2, p3, p4, p5, p6)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            constructor: (P1, P2, P3, P4, P5, P6, P7) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13)
            }

        }

        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            name15: String, codec15: Codec<P15>, getter15: (R) -> P15,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                val p15 = codec15.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
                codec15.writeNetwork(buffer, getter15.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                val p15 = codec15.readTranscoded(transcoder, format, name15)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
                codec15.writeTranscoded(transcoder, format, getter15.invoke(value), name15)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                codec15.writeJson(nestedJson, getter15.invoke(value), name15)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                val p15 = codec15.readJson(jsonToReadFrom, name15)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            name15: String, codec15: Codec<P15>, getter15: (R) -> P15,
            name16: String, codec16: Codec<P16>, getter16: (R) -> P16,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                val p15 = codec15.readNetwork(buffer)
                val p16 = codec16.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
                codec15.writeNetwork(buffer, getter15.invoke(value))
                codec16.writeNetwork(buffer, getter16.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                val p15 = codec15.readTranscoded(transcoder, format, name15)
                val p16 = codec16.readTranscoded(transcoder, format, name16)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
                codec15.writeTranscoded(transcoder, format, getter15.invoke(value), name15)
                codec16.writeTranscoded(transcoder, format, getter16.invoke(value), name16)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                codec15.writeJson(nestedJson, getter15.invoke(value), name15)
                codec16.writeJson(nestedJson, getter16.invoke(value), name16)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                val p15 = codec15.readJson(jsonToReadFrom, name15)
                val p16 = codec16.readJson(jsonToReadFrom, name16)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            name15: String, codec15: Codec<P15>, getter15: (R) -> P15,
            name16: String, codec16: Codec<P16>, getter16: (R) -> P16,
            name17: String, codec17: Codec<P17>, getter17: (R) -> P17,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                val p15 = codec15.readNetwork(buffer)
                val p16 = codec16.readNetwork(buffer)
                val p17 = codec17.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
                codec15.writeNetwork(buffer, getter15.invoke(value))
                codec16.writeNetwork(buffer, getter16.invoke(value))
                codec17.writeNetwork(buffer, getter17.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                val p15 = codec15.readTranscoded(transcoder, format, name15)
                val p16 = codec16.readTranscoded(transcoder, format, name16)
                val p17 = codec17.readTranscoded(transcoder, format, name17)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
                codec15.writeTranscoded(transcoder, format, getter15.invoke(value), name15)
                codec16.writeTranscoded(transcoder, format, getter16.invoke(value), name16)
                codec17.writeTranscoded(transcoder, format, getter17.invoke(value), name17)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                codec15.writeJson(nestedJson, getter15.invoke(value), name15)
                codec16.writeJson(nestedJson, getter16.invoke(value), name16)
                codec17.writeJson(nestedJson, getter17.invoke(value), name17)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                val p15 = codec15.readJson(jsonToReadFrom, name15)
                val p16 = codec16.readJson(jsonToReadFrom, name16)
                val p17 = codec17.readJson(jsonToReadFrom, name17)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            name15: String, codec15: Codec<P15>, getter15: (R) -> P15,
            name16: String, codec16: Codec<P16>, getter16: (R) -> P16,
            name17: String, codec17: Codec<P17>, getter17: (R) -> P17,
            name18: String, codec18: Codec<P18>, getter18: (R) -> P18,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                val p15 = codec15.readNetwork(buffer)
                val p16 = codec16.readNetwork(buffer)
                val p17 = codec17.readNetwork(buffer)
                val p18 = codec18.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
                codec15.writeNetwork(buffer, getter15.invoke(value))
                codec16.writeNetwork(buffer, getter16.invoke(value))
                codec17.writeNetwork(buffer, getter17.invoke(value))
                codec18.writeNetwork(buffer, getter18.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                val p15 = codec15.readTranscoded(transcoder, format, name15)
                val p16 = codec16.readTranscoded(transcoder, format, name16)
                val p17 = codec17.readTranscoded(transcoder, format, name17)
                val p18 = codec18.readTranscoded(transcoder, format, name18)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
                codec15.writeTranscoded(transcoder, format, getter15.invoke(value), name15)
                codec16.writeTranscoded(transcoder, format, getter16.invoke(value), name16)
                codec17.writeTranscoded(transcoder, format, getter17.invoke(value), name17)
                codec18.writeTranscoded(transcoder, format, getter18.invoke(value), name18)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                codec15.writeJson(nestedJson, getter15.invoke(value), name15)
                codec16.writeJson(nestedJson, getter16.invoke(value), name16)
                codec17.writeJson(nestedJson, getter17.invoke(value), name17)
                codec18.writeJson(nestedJson, getter18.invoke(value), name18)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                val p15 = codec15.readJson(jsonToReadFrom, name15)
                val p16 = codec16.readJson(jsonToReadFrom, name16)
                val p17 = codec17.readJson(jsonToReadFrom, name17)
                val p18 = codec18.readJson(jsonToReadFrom, name18)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18)
            }

        }


        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            name15: String, codec15: Codec<P15>, getter15: (R) -> P15,
            name16: String, codec16: Codec<P16>, getter16: (R) -> P16,
            name17: String, codec17: Codec<P17>, getter17: (R) -> P17,
            name18: String, codec18: Codec<P18>, getter18: (R) -> P18,
            name19: String, codec19: Codec<P19>, getter19: (R) -> P19,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                val p15 = codec15.readNetwork(buffer)
                val p16 = codec16.readNetwork(buffer)
                val p17 = codec17.readNetwork(buffer)
                val p18 = codec18.readNetwork(buffer)
                val p19 = codec19.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
                codec15.writeNetwork(buffer, getter15.invoke(value))
                codec16.writeNetwork(buffer, getter16.invoke(value))
                codec17.writeNetwork(buffer, getter17.invoke(value))
                codec18.writeNetwork(buffer, getter18.invoke(value))
                codec19.writeNetwork(buffer, getter19.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                val p15 = codec15.readTranscoded(transcoder, format, name15)
                val p16 = codec16.readTranscoded(transcoder, format, name16)
                val p17 = codec17.readTranscoded(transcoder, format, name17)
                val p18 = codec18.readTranscoded(transcoder, format, name18)
                val p19 = codec19.readTranscoded(transcoder, format, name19)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
                codec15.writeTranscoded(transcoder, format, getter15.invoke(value), name15)
                codec16.writeTranscoded(transcoder, format, getter16.invoke(value), name16)
                codec17.writeTranscoded(transcoder, format, getter17.invoke(value), name17)
                codec18.writeTranscoded(transcoder, format, getter18.invoke(value), name18)
                codec19.writeTranscoded(transcoder, format, getter19.invoke(value), name19)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                codec15.writeJson(nestedJson, getter15.invoke(value), name15)
                codec16.writeJson(nestedJson, getter16.invoke(value), name16)
                codec17.writeJson(nestedJson, getter17.invoke(value), name17)
                codec18.writeJson(nestedJson, getter18.invoke(value), name18)
                codec19.writeJson(nestedJson, getter19.invoke(value), name19)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                val p15 = codec15.readJson(jsonToReadFrom, name15)
                val p16 = codec16.readJson(jsonToReadFrom, name16)
                val p17 = codec17.readJson(jsonToReadFrom, name17)
                val p18 = codec18.readJson(jsonToReadFrom, name18)
                val p19 = codec19.readJson(jsonToReadFrom, name19)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19)
            }

        }

        fun <R, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20> of(
            name1: String, codec1: Codec<P1>, getter1: (R) -> P1,
            name2: String, codec2: Codec<P2>, getter2: (R) -> P2,
            name3: String, codec3: Codec<P3>, getter3: (R) -> P3,
            name4: String, codec4: Codec<P4>, getter4: (R) -> P4,
            name5: String, codec5: Codec<P5>, getter5: (R) -> P5,
            name6: String, codec6: Codec<P6>, getter6: (R) -> P6,
            name7: String, codec7: Codec<P7>, getter7: (R) -> P7,
            name8: String, codec8: Codec<P8>, getter8: (R) -> P8,
            name9: String, codec9: Codec<P9>, getter9: (R) -> P9,
            name10: String, codec10: Codec<P10>, getter10: (R) -> P10,
            name11: String, codec11: Codec<P11>, getter11: (R) -> P11,
            name12: String, codec12: Codec<P12>, getter12: (R) -> P12,
            name13: String, codec13: Codec<P13>, getter13: (R) -> P13,
            name14: String, codec14: Codec<P14>, getter14: (R) -> P14,
            name15: String, codec15: Codec<P15>, getter15: (R) -> P15,
            name16: String, codec16: Codec<P16>, getter16: (R) -> P16,
            name17: String, codec17: Codec<P17>, getter17: (R) -> P17,
            name18: String, codec18: Codec<P18>, getter18: (R) -> P18,
            name19: String, codec19: Codec<P19>, getter19: (R) -> P19,
            name20: String, codec20: Codec<P20>, getter20: (R) -> P20,
            constructor: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) -> R,
        ): Codec<R> = object : Codec<R> {
            override fun readNetwork(buffer: ByteBuf): R {
                val p1 = codec1.readNetwork(buffer)
                val p2 = codec2.readNetwork(buffer)
                val p3 = codec3.readNetwork(buffer)
                val p4 = codec4.readNetwork(buffer)
                val p5 = codec5.readNetwork(buffer)
                val p6 = codec6.readNetwork(buffer)
                val p7 = codec7.readNetwork(buffer)
                val p8 = codec8.readNetwork(buffer)
                val p9 = codec9.readNetwork(buffer)
                val p10 = codec10.readNetwork(buffer)
                val p11 = codec11.readNetwork(buffer)
                val p12 = codec12.readNetwork(buffer)
                val p13 = codec13.readNetwork(buffer)
                val p14 = codec14.readNetwork(buffer)
                val p15 = codec15.readNetwork(buffer)
                val p16 = codec16.readNetwork(buffer)
                val p17 = codec17.readNetwork(buffer)
                val p18 = codec18.readNetwork(buffer)
                val p19 = codec19.readNetwork(buffer)
                val p20 = codec20.readNetwork(buffer)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20)
            }

            override fun writeNetwork(buffer: ByteBuf, value: R) {
                codec1.writeNetwork(buffer, getter1.invoke(value))
                codec2.writeNetwork(buffer, getter2.invoke(value))
                codec3.writeNetwork(buffer, getter3.invoke(value))
                codec4.writeNetwork(buffer, getter4.invoke(value))
                codec5.writeNetwork(buffer, getter5.invoke(value))
                codec6.writeNetwork(buffer, getter6.invoke(value))
                codec7.writeNetwork(buffer, getter7.invoke(value))
                codec8.writeNetwork(buffer, getter8.invoke(value))
                codec9.writeNetwork(buffer, getter9.invoke(value))
                codec10.writeNetwork(buffer, getter10.invoke(value))
                codec11.writeNetwork(buffer, getter11.invoke(value))
                codec12.writeNetwork(buffer, getter12.invoke(value))
                codec13.writeNetwork(buffer, getter13.invoke(value))
                codec14.writeNetwork(buffer, getter14.invoke(value))
                codec15.writeNetwork(buffer, getter15.invoke(value))
                codec16.writeNetwork(buffer, getter16.invoke(value))
                codec17.writeNetwork(buffer, getter17.invoke(value))
                codec18.writeNetwork(buffer, getter18.invoke(value))
                codec19.writeNetwork(buffer, getter19.invoke(value))
                codec20.writeNetwork(buffer, getter20.invoke(value))
            }

            override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): R {
                val p1 = codec1.readTranscoded(transcoder, format, name1)
                val p2 = codec2.readTranscoded(transcoder, format, name2)
                val p3 = codec3.readTranscoded(transcoder, format, name3)
                val p4 = codec4.readTranscoded(transcoder, format, name4)
                val p5 = codec5.readTranscoded(transcoder, format, name5)
                val p6 = codec6.readTranscoded(transcoder, format, name6)
                val p7 = codec7.readTranscoded(transcoder, format, name7)
                val p8 = codec8.readTranscoded(transcoder, format, name8)
                val p9 = codec9.readTranscoded(transcoder, format, name9)
                val p10 = codec10.readTranscoded(transcoder, format, name10)
                val p11 = codec11.readTranscoded(transcoder, format, name11)
                val p12 = codec12.readTranscoded(transcoder, format, name12)
                val p13 = codec13.readTranscoded(transcoder, format, name13)
                val p14 = codec14.readTranscoded(transcoder, format, name14)
                val p15 = codec15.readTranscoded(transcoder, format, name15)
                val p16 = codec16.readTranscoded(transcoder, format, name16)
                val p17 = codec17.readTranscoded(transcoder, format, name17)
                val p18 = codec18.readTranscoded(transcoder, format, name18)
                val p19 = codec19.readTranscoded(transcoder, format, name19)
                val p20 = codec20.readTranscoded(transcoder, format, name20)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20)
            }

            override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: R, field: String) {
                codec1.writeTranscoded(transcoder, format, getter1.invoke(value), name1)
                codec2.writeTranscoded(transcoder, format, getter2.invoke(value), name2)
                codec3.writeTranscoded(transcoder, format, getter3.invoke(value), name3)
                codec4.writeTranscoded(transcoder, format, getter4.invoke(value), name4)
                codec5.writeTranscoded(transcoder, format, getter5.invoke(value), name5)
                codec6.writeTranscoded(transcoder, format, getter6.invoke(value), name6)
                codec7.writeTranscoded(transcoder, format, getter7.invoke(value), name7)
                codec8.writeTranscoded(transcoder, format, getter8.invoke(value), name8)
                codec9.writeTranscoded(transcoder, format, getter9.invoke(value), name9)
                codec10.writeTranscoded(transcoder, format, getter10.invoke(value), name10)
                codec11.writeTranscoded(transcoder, format, getter11.invoke(value), name11)
                codec12.writeTranscoded(transcoder, format, getter12.invoke(value), name12)
                codec13.writeTranscoded(transcoder, format, getter13.invoke(value), name13)
                codec14.writeTranscoded(transcoder, format, getter14.invoke(value), name14)
                codec15.writeTranscoded(transcoder, format, getter15.invoke(value), name15)
                codec16.writeTranscoded(transcoder, format, getter16.invoke(value), name16)
                codec17.writeTranscoded(transcoder, format, getter17.invoke(value), name17)
                codec18.writeTranscoded(transcoder, format, getter18.invoke(value), name18)
                codec19.writeTranscoded(transcoder, format, getter19.invoke(value), name19)
                codec20.writeTranscoded(transcoder, format, getter20.invoke(value), name20)
            }

            override fun writeJson(json: JsonElement, value: R, field: String) {
                val nestedJson = JsonObject()
                codec1.writeJson(nestedJson, getter1.invoke(value), name1)
                codec2.writeJson(nestedJson, getter2.invoke(value), name2)
                codec3.writeJson(nestedJson, getter3.invoke(value), name3)
                codec4.writeJson(nestedJson, getter4.invoke(value), name4)
                codec5.writeJson(nestedJson, getter5.invoke(value), name5)
                codec6.writeJson(nestedJson, getter6.invoke(value), name6)
                codec7.writeJson(nestedJson, getter7.invoke(value), name7)
                codec8.writeJson(nestedJson, getter8.invoke(value), name8)
                codec9.writeJson(nestedJson, getter9.invoke(value), name9)
                codec10.writeJson(nestedJson, getter10.invoke(value), name10)
                codec11.writeJson(nestedJson, getter11.invoke(value), name11)
                codec12.writeJson(nestedJson, getter12.invoke(value), name12)
                codec13.writeJson(nestedJson, getter13.invoke(value), name13)
                codec14.writeJson(nestedJson, getter14.invoke(value), name14)
                codec15.writeJson(nestedJson, getter15.invoke(value), name15)
                codec16.writeJson(nestedJson, getter16.invoke(value), name16)
                codec17.writeJson(nestedJson, getter17.invoke(value), name17)
                codec18.writeJson(nestedJson, getter18.invoke(value), name18)
                codec19.writeJson(nestedJson, getter19.invoke(value), name19)
                codec20.writeJson(nestedJson, getter20.invoke(value), name20)
                if (field.isEmpty()) {
                    nestedJson.entrySet().forEach { (key, value) ->
                        json.asObjectOrThrow().add(key, value)
                    }
                } else {
                    json.asObjectOrThrow().add(field, nestedJson)
                }
            }

            override fun readJson(json: JsonElement, field: String): R {
                val jsonToReadFrom: JsonElement
                if (field.isEmpty()) { // root node
                    if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
                    jsonToReadFrom = json
                } else {
                    jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
                }

                val p1 = codec1.readJson(jsonToReadFrom, name1)
                val p2 = codec2.readJson(jsonToReadFrom, name2)
                val p3 = codec3.readJson(jsonToReadFrom, name3)
                val p4 = codec4.readJson(jsonToReadFrom, name4)
                val p5 = codec5.readJson(jsonToReadFrom, name5)
                val p6 = codec6.readJson(jsonToReadFrom, name6)
                val p7 = codec7.readJson(jsonToReadFrom, name7)
                val p8 = codec8.readJson(jsonToReadFrom, name8)
                val p9 = codec9.readJson(jsonToReadFrom, name9)
                val p10 = codec10.readJson(jsonToReadFrom, name10)
                val p11 = codec11.readJson(jsonToReadFrom, name11)
                val p12 = codec12.readJson(jsonToReadFrom, name12)
                val p13 = codec13.readJson(jsonToReadFrom, name13)
                val p14 = codec14.readJson(jsonToReadFrom, name14)
                val p15 = codec15.readJson(jsonToReadFrom, name15)
                val p16 = codec16.readJson(jsonToReadFrom, name16)
                val p17 = codec17.readJson(jsonToReadFrom, name17)
                val p18 = codec18.readJson(jsonToReadFrom, name18)
                val p19 = codec19.readJson(jsonToReadFrom, name19)
                val p20 = codec20.readJson(jsonToReadFrom, name20)
                return constructor.invoke(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20)
            }

        }
    }
}