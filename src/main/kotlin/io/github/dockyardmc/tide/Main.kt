package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import cz.lukynka.prettylog.log
import io.netty.buffer.Unpooled

fun main() {
    val json = JsonObject()
    val bus = Bus(
        name = "Mercedes-Benz Citaro",
        passengers = listOf(Person("Maya", 69), Person("Aso", 420), Person("Pikachu", 727)),
        isGonnaExplode = true
    )

    val buffer = Unpooled.buffer()

    Bus.codec.writeNetwork(buffer, bus)
    Bus.codec.writeJson(json, bus)
    buffer.resetReaderIndex()

    val newBus = Bus.codec.readNetwork(buffer)

    log("Reconstructed Network: $newBus")
    log("Json: $json")
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

data class Car(val name: String, val driver: Person, val passenger: Person) {

    companion object {
        val codec = Codec.of<Car>(
            Field("name", Primitives.String, Car::name),
            Field("driver", Person.codec, Car::driver),
            Field("passenger", Person.codec, Car::passenger)
        )
    }
}

data class Person(val name: String, val age: Int) {
    companion object {
        val codec = Codec.of<Person>(
            Field("name", Primitives.String, Person::name),
            Field("age", Primitives.VarInt, Person::age),
        )
    }
}