package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import cz.lukynka.prettylog.log
import io.netty.buffer.Unpooled

fun main() {
    val json = JsonObject()
//    val bus = Bus(
//        name = "Mercedes-Benz Citaro",
//        passengers = listOf(Person("Maya", 69), Person("Aso", 420), Person("Pikachu", 727)),
//        isGonnaExplode = true
//    )
    val classs = Class(
        "A1", mapOf(
            Person("Maya", 69) to false,
            Person("Aso", 420) to true,
            Person("Pikachu", 727) to false,
            Person("Kev", Int.MAX_VALUE) to true,
            Person("Not Maya", 69) to true
        )
    )

    val buffer = Unpooled.buffer()

    Class.codec.writeNetwork(buffer, classs)
    Class.codec.writeJson(json, classs)
    buffer.resetReaderIndex()

    val newClass = Class.codec.readNetwork(buffer)

    log("Reconstructed Network: $newClass")
    log("Json: $json")
}

data class Class(val className: String, val studentsPresent: Map<Person, Boolean>) {
    companion object {
        val codec = Codec.of(
            Field("class_name", Primitives.String, Class::className),
            Field("students_present", Codec.map<Person, Boolean>(Person.codec, Primitives.Boolean), Class::studentsPresent),
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