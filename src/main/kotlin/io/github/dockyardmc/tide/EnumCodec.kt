package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import java.lang.IllegalStateException
import kotlin.reflect.KClass

class EnumCodec<T : Enum<T>>(private val kClass: KClass<Enum<T>>) : Codec<Enum<T>> {
    override val type: KClass<*> = Enum::class

    override fun readNetwork(buffer: ByteBuf): Enum<T> {
        val ordinal = Primitives.VarInt.readNetwork(buffer)
        return kClass.java.enumConstants[ordinal]
    }

    override fun writeNetwork(buffer: ByteBuf, value: Enum<T>) {
        Primitives.VarInt.writeNetwork(buffer, value.ordinal)
    }

    override fun readJson(json: JsonElement, field: String): Enum<T> {
        val name = json.getPrimitive<String>(field)
        return kClass.java.enumConstants.first { entry -> entry.name == name }
    }

    override fun writeJson(json: JsonElement, value: Enum<T>, field: String) {
        json.asObjectOrThrow().addProperty(field, value.name)
    }
}