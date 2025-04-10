package io.github.dockyardmc.tide

import io.netty.buffer.ByteBuf
import jdk.jshell.spi.ExecutionControl.NotImplementedException
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

class ReflectiveCodec<T : Any>(
    private val kclass: KClass<T>,
    private val fields: List<Field<T>>,
) : Codec<T> {
    override val type: KClass<*> = kclass
    private val constructor: KFunction<T> by lazy {
        kclass.primaryConstructor ?: throw IllegalArgumentException("No primary constructor")
    }

    private val parameters by lazy { constructor.parameters.sortedBy { parameter -> parameter.index } }

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

    override fun readJson(json: JsonObject, field: String): T {
        TODO("Not yet implemented")
    }

    override fun writeJson(json: JsonObject, value: T, field: String) {
        TODO("Not yet implemented")
    }
}