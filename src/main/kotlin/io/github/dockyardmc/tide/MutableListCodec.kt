package io.github.dockyardmc.tide

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf

class MutableListCodec<T>(val elementCodec: Codec<T>) : Codec<MutableList<T>> {

    override fun readNetwork(buffer: ByteBuf): MutableList<T> {
        val inner = mutableListOf<T>()
        val size = Codecs.VarInt.readNetwork(buffer)
        for (i in 0 until size) {
            inner.add(elementCodec.readNetwork(buffer))
        }
        return inner
    }

    override fun writeNetwork(buffer: ByteBuf, value: MutableList<T>) {
        Codecs.VarInt.writeNetwork(buffer, value.size)
        value.forEach { element ->
            elementCodec.writeNetwork(buffer, element)
        }
    }

    override fun readJson(json: JsonElement, field: String): MutableList<T> {
        val list = mutableListOf<T>()

        if(field.isEmpty()) { // root node
            if (json !is JsonArray) throw IllegalStateException("JsonElement is not JsonArray, cannot read json as root node")
            json.forEach { element ->
                list.add(elementCodec.readJson(element))
            }
            return list
        }

        val jsonObject = json.asObjectOrThrow()
        if (!jsonObject.has(field)) {
            throw IllegalArgumentException("Field '$field' does not exist in current json: $jsonObject")
        }

        val element = jsonObject.get(field) ?: throw IllegalArgumentException("Field `$field` does not exist in current json: $jsonObject!")
        if(!element.isJsonArray) throw IllegalArgumentException("Field `$field` is not json array element! (json: $jsonObject)")
        val jsonArray = element.asJsonArray!!

        jsonArray.forEach { child ->
            list.add(elementCodec.readJson(child))
        }

        return list
    }

    override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): MutableList<T> {
        return transcoder.readList<T>(format, field).toMutableList()
    }

    override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: MutableList<T>, field: String) {
        transcoder.writeList(format, field, value)
    }

    override fun writeJson(json: JsonElement, value: MutableList<T>, field: String) {
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