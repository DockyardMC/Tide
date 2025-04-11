package io.github.dockyardmc.tide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

fun JsonElement.asObjectOrThrow(): JsonObject {
    if (this !is JsonObject) throw IllegalStateException("JsonElement is not JsonObject")
    return this
}

inline fun <reified T> JsonElement.getPrimitive(key: String): T {
    return this.getPrimitiveOrNull(key) ?: throw IllegalStateException("Field with name $key was not found")
}

inline fun <reified T> JsonElement.getPrimitiveOrNull(key: String): T? {
    val element: JsonElement

    if(this is JsonPrimitive) {
        return getJsonAsGeneric<T>(this)
    }

    if (key.isEmpty()) {
        element = this
    } else {
        if (this !is JsonObject) throw IllegalStateException("this is not JsonObject")
        if (!has(key)) return null
        element = get(key)
    }
    return getJsonAsGeneric<T>(element)

}

inline fun <reified T> getJsonAsGeneric(element: JsonElement): T {
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