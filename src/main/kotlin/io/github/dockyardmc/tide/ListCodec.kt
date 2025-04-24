package io.github.dockyardmc.tide

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import cz.lukynka.prettylog.log
import io.netty.buffer.ByteBuf
import kotlin.reflect.KClass

class ListCodec<T>(val elementCodec: Codec<T>) : Codec<List<T>> {

    override fun readNetwork(buffer: ByteBuf): List<T> {
        val inner = mutableListOf<T>()
        val size = Codecs.VarInt.readNetwork(buffer)
        for (i in 0 until size) {
            inner.add(elementCodec.readNetwork(buffer))
        }
        return inner.toList()
    }

    override fun writeNetwork(buffer: ByteBuf, value: List<T>) {
        Codecs.VarInt.writeNetwork(buffer, value.size)
        value.forEach { element ->
            elementCodec.writeNetwork(buffer, element)
        }
    }

    override fun readJson(json: JsonElement, field: String): List<T> {
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

    override fun <A> readTranscoded(transcoder: Transcoder<A>, format: A, field: String): List<T> {
        return transcoder.readList(format, field)
    }

    override fun <A> writeTranscoded(transcoder: Transcoder<A>, format: A, value: List<T>, field: String) {
        transcoder.writeList(format, field, value)
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