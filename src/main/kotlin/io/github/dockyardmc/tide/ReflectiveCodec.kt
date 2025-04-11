package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

class ReflectiveCodec<T : Any>(
    private val kclass: KClass<T>,
    private val fields: List<Field<T>>,
) : Codec<T> {
    override val type: KClass<*> = kclass
    private val constructor: KFunction<T> = kclass.primaryConstructor ?: throw IllegalArgumentException("No primary constructor")


    private val parameters = constructor.parameters.sortedBy { parameter -> parameter.index }

    init {
        // type checking
        fields.forEachIndexed { i, field ->
            val paramType = parameters[i].type.classifier as? KClass<*>
                ?: throw IllegalArgumentException("Unsupported generic type")

            require(paramType == field.codec.type) {
                "Type mismatch for parameter ${parameters[i].name}: " +
                        "Expected ${field.codec.type.simpleName}, found ${paramType.simpleName}"
            }
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

    fun readJson(json: JsonObject): T {
        return readJson(json, "_")
    }

    override fun writeJson(json: JsonObject, value: T, field: String) {
        val nestedJson = JsonObject()
        fields.forEach { arg ->
            val fieldValue = arg.getter(value)
            @Suppress("UNCHECKED_CAST")
            (arg.codec as Codec<Any?>).writeJson(nestedJson, fieldValue, arg.name)
        }

        if (field.isEmpty()) {
            nestedJson.entrySet().forEach { (key, value) ->
                json.add(key, value)
            }
        } else {
            json.add(field, nestedJson)
        }
    }

    override fun readJson(json: JsonObject, field: String): T {
        val nestedJson = json.getAsJsonObject(field)
        val args = fields.map { arg ->
            @Suppress("UNCHECKED_CAST")
            (arg.codec as Codec<Any?>).readJson(nestedJson, arg.name)
        }
        return constructor.callBy(
            parameters.associateWith { parameter -> args[parameter.index] }
        )
    }
}