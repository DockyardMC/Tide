package io.github.dockyardmc.tide

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class MapCodec<K, V>(private val keyCodec: Codec<K>, private val valueCodec: Codec<V>) : Codec<Map<K, V>> {
    override val type: KClass<*> = Map::class

    override fun writeNetwork(buffer: ByteBuf, value: Map<K, V>) {
        Primitives.VarInt.writeNetwork(buffer, value.size)
        value.forEach { (key, value) ->
            keyCodec.writeNetwork(buffer, key)
            valueCodec.writeNetwork(buffer, value)
        }
    }

    override fun readNetwork(buffer: ByteBuf): Map<K, V> {
        val map = mutableMapOf<K, V>()
        val size = Primitives.VarInt.readNetwork(buffer)
        for (i in 0 until size) {
            val key = keyCodec.readNetwork(buffer)
            val value = valueCodec.readNetwork(buffer)
            map[key] = value
        }
        return map
    }

    //
    // me when https://img.lukynka.cloud/wtf.png
    //
    override fun readJson(json: JsonElement, field: String): Map<K, V> {
        val jsonArrayToReadFrom: JsonArray

        if (field.isEmpty()) { // root node
            if (json !is JsonArray) throw IllegalStateException("JsonElement is not JsonArray, cannot write json as root node")
            jsonArrayToReadFrom = json
        } else {
            val jsonObject = json.asObjectOrThrow()
            if (!jsonObject.has(field)) {
                throw IllegalArgumentException("Field '$field' does not exist")
            }
            val jsonElement = jsonObject.get(field)
            if (jsonElement !is JsonArray) {
                throw IllegalArgumentException("Field '$field' is not json array")
            }

            jsonArrayToReadFrom = jsonElement
        }

        val map = mutableMapOf<K, V>()

        jsonArrayToReadFrom.forEach { entryElement ->
            if (!entryElement.isJsonObject) {
                throw IllegalArgumentException("map entry is not json array")
            }
            val entryObj = entryElement.asJsonObject
            if (!entryObj.has("key") || !entryObj.has("value")) {
                throw IllegalArgumentException("map entry is missing key or value field")
            }

            val key = keyCodec.readJson(entryObj, "key")
            val value = valueCodec.readJson(entryObj, "value")
            map[key] = value
        }
        return map
    }

    override fun writeJson(json: JsonElement, value: Map<K, V>, field: String) {
        var jsonArray: JsonArray = JsonArray()

        if (field.isEmpty()) { // root node
            if (json !is JsonArray) throw IllegalStateException("JsonElement is not JsonArray, cannot write json as root node")
            jsonArray = json
        }

        value.forEach { (key, entryValue) ->
            val entryObj = JsonObject()
            keyCodec.writeJson(entryObj, key, "key")
            valueCodec.writeJson(entryObj, entryValue, "value")
            jsonArray.add(entryObj)
        }

        if (json is JsonObject) json.add(field, jsonArray)
    }
}