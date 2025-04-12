package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class OptionalCodec<T>(val elementCodec: Codec<T>) : Codec<T?> {

    override val type: KClass<*> = elementCodec.type

    override fun readNetwork(buffer: ByteBuf): T? {
        val isPresent = buffer.readBoolean()
        if (!isPresent) return null
        return elementCodec.readNetwork(buffer)
    }

    override fun writeNetwork(buffer: ByteBuf, value: T?) {
        buffer.writeBoolean(value != null)
        if (value != null) {
            elementCodec.writeNetwork(buffer, value)
        }
    }

    override fun readJson(json: JsonElement, field: String): T? {
        val jsonElementToRead: JsonElement?

        if (field.isEmpty()) {
            jsonElementToRead = json
        } else {
            val jsonObject = json.asObjectOrThrow()
            if (!jsonObject.has(field)) {
                return null
            }
            jsonElementToRead = jsonObject.get(field)
        }

        if (jsonElementToRead == null || jsonElementToRead.isJsonNull) {
            return null // treat explicit json null as kotlin null
        }
        return elementCodec.readJson(jsonElementToRead, field)
    }

    override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): T? {
        return transcoder.readOptional<T>(format, field)
    }

    override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: T?, field: String) {
        transcoder.writeOptional<T>(format, field, value)
    }

    override fun writeJson(json: JsonElement, value: T?, field: String) {

        if (value != null) {
            elementCodec.writeJson(json, value, field)
        } else {
            json.asObjectOrThrow().add(field, JsonNull.INSTANCE)
        }
    }
}
