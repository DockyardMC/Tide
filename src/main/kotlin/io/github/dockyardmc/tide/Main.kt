package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import cz.lukynka.prettylog.log
import io.netty.buffer.Unpooled

fun main() {
    val bus = Bus("fuck do I know", listOf(Person("Maya", 23, PersonType.GAY), Person("Kev", 25, null)), true)

    val buffer = Unpooled.buffer()
    val json = JsonObject()

    Bus.codec.writeNetwork(buffer, bus)
    Bus.codec.writeJson(json, bus)

    buffer.resetReaderIndex()

    val newBus = Bus.codec.readNetwork(buffer)
    val newBusFromJson = Bus.codec.readJson(json, "")

    log("Reconstructed network type: $newBus")
    log("Reconstructed from json: $newBusFromJson")

    log("Json: $json")
}

enum class PersonType {
    GAY,
    NORMAL
}

data class Class(val className: String, val studentsPresent: Map<Person, Boolean>, val isGay: Boolean?) {
    companion object {
        val codec = Codec.of(
            Field("class_name", Primitives.String, Class::className),
            Field("students_present", Codec.map<Person, Boolean>(Person.codec, Primitives.Boolean), Class::studentsPresent),
            Field("is_gay", Codec.optional(Primitives.Boolean), Class::isGay),
        )
    }
}

data class Bus(val name: String, val passengers: List<Person>, val isGonnaExplode: Boolean) {
    companion object {
        val codec = Codec.of(
            Field("name", Primitives.String, Bus::name),
            Field("passengers", Codec.list(Person.codec), Bus::passengers),
            Field("is_gonna_explode", Primitives.Boolean, Bus::isGonnaExplode)
        )
    }
}

data class Person(val name: String, val age: Int, val type: PersonType?) {
    companion object {
        val codec = Codec.of<Person>(
            Field("name", Primitives.String, Person::name),
            Field("age", Primitives.VarInt, Person::age),
            Field("type", Codec.optional(Codec.enum(PersonType::class)), Person::type)
        )
    }
}