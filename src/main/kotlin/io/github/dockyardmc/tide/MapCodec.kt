package io.github.dockyardmc.tide

import com.google.gson.JsonArray
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
    override fun readJson(json: JsonObject, field: String): Map<K, V> {
        if (!json.has(field)) {
            throw IllegalArgumentException("Field '$field' does not exist")
        }
        val jsonElement = json.get(field)
        if (!jsonElement.isJsonArray) {
            throw IllegalArgumentException("Field '$field' is not json array")
        }
        val jsonArray = jsonElement.asJsonArray
        val map = mutableMapOf<K, V>()
        jsonArray.forEach { entryElement ->
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

    override fun writeJson(json: JsonObject, value: Map<K, V>, field: String) {
        val jsonArray = JsonArray()
        value.forEach { (key, entryValue) ->
            val entryObj = JsonObject()
            keyCodec.writeJson(entryObj, key, "key")
            valueCodec.writeJson(entryObj, entryValue, "value")
            jsonArray.add(entryObj)
        }
        json.add(field, jsonArray)
    }
}