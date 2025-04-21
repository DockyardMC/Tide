package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class EnumCodec<T : Enum<T>>(val kClass: KClass<out Enum<T>>) : Codec<Enum<T>> {
    override val type: KClass<*> = Enum::class

    override fun readNetwork(buffer: ByteBuf): Enum<T> {
        val ordinal = Codecs.VarInt.readNetwork(buffer)
        return kClass.java.enumConstants[ordinal]
    }

    override fun writeNetwork(buffer: ByteBuf, value: Enum<T>) {
        Codecs.VarInt.writeNetwork(buffer, value.ordinal)
    }

    override fun readJson(json: JsonElement, field: String): Enum<T> {
        val name = json.getPrimitive<String>(field)
        return kClass.java.enumConstants.first { entry -> entry.name == name }
    }

    override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): Enum<T> {
        return transcoder.readEnum<T>(kClass, format, field)
    }

    override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: Enum<T>, field: String) {
        transcoder.writeEnum(kClass, format, field, value)
    }

    override fun writeJson(json: JsonElement, value: Enum<T>, field: String) {
        json.asObjectOrThrow().addProperty(field, value.name)
    }
}