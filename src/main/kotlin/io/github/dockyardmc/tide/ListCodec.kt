package io.github.dockyardmc.tide

import com.google.gson.JsonArray
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

    override fun readJson(json: JsonObject, field: String): List<T> {
        if (!json.has(field)) {
            throw IllegalArgumentException("Field '$field' does not exist")
        }
        val jsonElement = json.get(field)
        if (!jsonElement.isJsonArray) {
            throw IllegalArgumentException("Field '$field' is not json array")
        }
        val jsonArray = jsonElement.asJsonArray
        val list = mutableListOf<T>()
        jsonArray.forEach { element ->
            val tempObj = JsonObject()
            tempObj.add("value", element)
            list.add(elementCodec.readJson(tempObj, "value"))
        }
        return list
    }

    override fun writeJson(json: JsonObject, value: List<T>, field: String) {
        val jsonArray = JsonArray()
        value.forEach { element ->
            val tempObj = JsonObject()
            elementCodec.writeJson(tempObj, element, "value")
            val elementValue = tempObj.get("value")
            jsonArray.add(elementValue)
        }
        json.add(field, jsonArray)
    }
}