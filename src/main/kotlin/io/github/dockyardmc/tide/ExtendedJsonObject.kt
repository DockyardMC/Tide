package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.IllegalStateException


inline fun <reified T> JsonObject.getPrimitive(key: String): T {
    return this.getPrimitiveOrNull(key) ?: throw IllegalStateException("Field with name $key was not found")
}

inline fun <reified T> JsonObject.getPrimitiveOrNull(key: String): T? {
    if (!has(key)) return null

    val element = get(key)
    return when (T::class) {
        String::class -> {
            when {
                element.isJsonPrimitive -> element.asString
                else -> element.toString()
            } as T
        }
        Int::class -> element.asInt as T
        Long::class -> element.asLong as T
        Double::class -> element.asDouble as T
        Float::class -> element.asFloat as T
        Boolean::class -> element.asBoolean as T
        JsonObject::class -> element.asJsonObject as T
        JsonElement::class -> element as T
        else -> throw UnsupportedOperationException("Type ${T::class} is not supported")
    }
}