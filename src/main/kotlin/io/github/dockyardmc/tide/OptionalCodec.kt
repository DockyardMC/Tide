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
        val jsonElementToRead = if (field.isEmpty()) { // root node
            json
        } else {
            json.asObjectOrThrow().get(field) ?: throw IllegalArgumentException("Field `$field` does not exist in current json: $json")
        }

        if (jsonElementToRead.isJsonNull) return null
        return elementCodec.readJson(jsonElementToRead)
    }

//    override fun readJson(json: JsonElement, field: String): T? {
//        log("Reading $field in $json", LogType.FATAL)
//        val jsonElementToRead = if (field.isEmpty()) {
//            // Handle root element case
//            if (json.isJsonNull) null else json
//        } else {
//            // Handle nested field case
//            if (!json.isJsonObject) return null
//            val jsonObject = json.asJsonObject
//
//            jsonObject.get(field)?.takeIf { jsonElement -> !jsonElement.isJsonNull }
//        }
//
//        return jsonElementToRead?.let { jsonElement ->
//            elementCodec.readJson(jsonElement)
//        }
//    }

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
