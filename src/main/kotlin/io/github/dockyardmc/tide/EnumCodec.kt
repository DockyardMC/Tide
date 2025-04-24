package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class EnumCodec<T>(val kClass: KClass<out Enum<*>>) : Codec<T> {

    override fun readNetwork(buffer: ByteBuf): T {
        val ordinal = Codecs.VarInt.readNetwork(buffer)
        return kClass.java.enumConstants[ordinal] as T
    }

    override fun writeNetwork(buffer: ByteBuf, value: T) {
        Codecs.VarInt.writeNetwork(buffer, (value as Enum<*>).ordinal)
    }

    override fun readJson(json: JsonElement, field: String): T {
        val name = json.getPrimitive<String>(field)
        return kClass.java.enumConstants.first { entry -> entry.name == name } as T
    }

    override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): T {
        return transcoder.readEnum<T>(kClass, format, field)
    }

    override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: T, field: String) {
        transcoder.writeEnum(kClass, format, field, value)
    }

    override fun writeJson(json: JsonElement, value: T, field: String) {
        json.asObjectOrThrow().addProperty(field, (value as Enum<*>).name)
    }
}