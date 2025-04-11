package io.github.dockyardmc.tide

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class ListCodec<T>(private val elementCodec: Codec<T>) : Codec<List<T>> {
    override val type: KClass<*> = List::class

    override fun readNetwork(buffer: ByteBuf): List<T> {
        val inner = mutableListOf<T>()
        val size = Primitives.VarInt.readNetwork(buffer)
        for (i in 0 until size) {
            inner.add(elementCodec.readNetwork(buffer))
        }
        return inner.toList()
    }

    override fun writeNetwork(buffer: ByteBuf, value: List<T>) {
        Primitives.VarInt.writeNetwork(buffer, value.size)
        value.forEach { element ->
            elementCodec.writeNetwork(buffer, element)
        }
    }

    override fun readJson(json: JsonElement, field: String): List<T> {
        val list = mutableListOf<T>()

        if (field.isEmpty()) { // root node
            if (json !is JsonArray) throw IllegalStateException("JsonElement is not JsonArray, cannot read json as root node")
            val tempObj = JsonObject()
            json.forEach { element ->
                tempObj.add("value", element)
                list.add(elementCodec.readJson(tempObj, "value"))
            }
            return list
        }

        val jsonObject = json.asObjectOrThrow()
        if (!jsonObject.has(field)) {
            throw IllegalArgumentException("Field '$field' does not exist")
        }
        val jsonElement = jsonObject.get(field)
        if (!jsonElement.isJsonArray) {
            throw IllegalArgumentException("Field '$field' is not json array")
        }

        val jsonArray = jsonElement.asJsonArray
        jsonArray.forEach { element ->
            val tempObj = JsonObject()
            tempObj.add("value", element)
            list.add(elementCodec.readJson(tempObj, "value"))
        }
        return list
    }

    override fun writeJson(json: JsonElement, value: List<T>, field: String) {
        var arrayToWriteTo: JsonArray = JsonArray()

        if (field.isEmpty()) { // root node
            if (json !is JsonArray) throw IllegalStateException("JsonElement is not JsonArray, cannot write json as root node")
            arrayToWriteTo = json
        }

        value.forEach { element ->
            val tempObj = JsonObject()
            elementCodec.writeJson(tempObj, element, "value")
            val elementValue = tempObj.get("value")
            arrayToWriteTo.add(elementValue)
        }

        if (json is JsonObject) json.add(field, arrayToWriteTo)
    }
}