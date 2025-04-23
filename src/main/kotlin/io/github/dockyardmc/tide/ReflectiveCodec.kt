package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

/**
 * Reflective codec, uses reflection to get constructors or field values of a class and either write them or read them and create a class
 *
 * Reflection is done when the object is created so when the codec is being used (read or write) it isn't slow
 *
 * @param T
 * @property kclass Class of the object this codec is for
 * @property fields List of [Field] to provide information to the codec
 */
class ReflectiveCodec<T : Any>(
    val kclass: KClass<T>,
    val fields: List<Field<T>>,
) : Codec<T> {
    override val type: KClass<*> = kclass

    /**
     * Constructor of the provided class, stored ahead of time to not slow down reading
     */
    val constructor: KFunction<T> = kclass.primaryConstructor ?: throw IllegalArgumentException("No primary constructor")

    /**
     * Parameters of the constructor, stored ahead of time to not slow down reading
     */
    val parameters = constructor.parameters.sortedBy { parameter -> parameter.index }

    // type checking
    init {
        fields.forEachIndexed { i, field ->
            val paramType = parameters[i].type.classifier as? KClass<*>
                ?: throw IllegalArgumentException("Unsupported generic type")

            val codecType = field.codec.type

            val typeMatches = when {
                codecType == Enum::class -> paramType.java.isEnum
                else -> paramType == codecType
            }

            require(typeMatches) {
                "Type mismatch for parameter ${parameters[i].name}: " +
                        "Expected ${if (codecType == Enum::class) "Enum" else codecType.simpleName}, " +
                        "found ${paramType.simpleName}"
            }
        }
    }

    override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): T {
        val args = fields.map { arg ->
            @Suppress("UNCHECKED_CAST")
            (arg.codec as Codec<Any?>).readTranscoded(transcoder, format, arg.name)
        }
        return constructor.callBy(parameters.associateWith { parameter -> args[parameter.index] })
    }

    override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: T, field: String) {
        fields.forEach { arg ->
            val fieldValue = arg.getter(value)
            @Suppress("UNCHECKED_CAST")
            (arg.codec as Codec<Any?>).writeTranscoded(transcoder, format, fieldValue, arg.name)
        }
    }

    override fun writeNetwork(buffer: ByteBuf, value: T) {
        fields.forEach { field ->
            @Suppress("UNCHECKED_CAST")
            (field.codec as Codec<Any?>).writeNetwork(buffer, field.getter(value))
        }
    }

    override fun readNetwork(buffer: ByteBuf): T {
        val args = fields.map { field ->
            @Suppress("UNCHECKED_CAST")
            (field.codec as Codec<Any?>).readNetwork(buffer)
        }
        return constructor.callBy(parameters.associateWith { parameter -> args[parameter.index] })
    }

    override fun writeJson(json: JsonElement, value: T, field: String) {
        val nestedJson = JsonObject()
        fields.forEach { arg ->
            val fieldValue = arg.getter(value)
            @Suppress("UNCHECKED_CAST")
            (arg.codec as Codec<Any?>).writeJson(nestedJson, fieldValue, arg.name)
        }

        if (field.isEmpty()) {
            nestedJson.entrySet().forEach { (key, value) ->
                json.asObjectOrThrow().add(key, value)
            }
        } else {
            json.asObjectOrThrow().add(field, nestedJson)
        }
    }

    override fun readJson(json: JsonElement, field: String): T {
        val jsonToReadFrom: JsonElement

        if (field.isEmpty()) { // root node
            if (json !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject, cannot write json as root node")
            jsonToReadFrom = json
        } else {
            jsonToReadFrom = json.asObjectOrThrow().get(field) ?: throw IllegalStateException("[field: $field] get is somehow null ($json) (is object: ${json.isJsonObject}, is primitive: ${json.isJsonPrimitive}, is null: ${json.isJsonNull})")
        }
        val args = fields.map { arg ->
            @Suppress("UNCHECKED_CAST")
            (arg.codec as Codec<Any?>).readJson(jsonToReadFrom, arg.name)
        }
        return constructor.callBy(
            parameters.associateWith { parameter -> args[parameter.index] }
        )
    }


}