package io.github.dockyardmc.tide

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class OptionalCodec<T>(private val elementCodec: Codec<T>) : Codec<T?> {

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

    override fun readJson(json: JsonObject, field: String): T? {
        if (!json.has(field)) {
            return null
        }
        val jsonElement = json.get(field)
        if (jsonElement.isJsonNull) {
            return null // treat explicit json null as kotlin null
        }
        return elementCodec.readJson(json, field)
    }

    override fun writeJson(json: JsonObject, value: T?, field: String) {
        if (value != null) {
            elementCodec.writeJson(json, value, field)
        } else {
            json.add(field, JsonNull.INSTANCE)
        }
    }
}
