package io.github.dockyardmc.tide

import com.google.gson.JsonObject

fun main() {
    val car = Car("Mercedes GLA", Person("Maya", 69), Person("Pikachu", 420))
    val json = JsonObject()

    Car.codec.writeJson(json, car)
    println(json.toString())
}

data class Car(val name: String, val driver: Person, val passenger: Person) {

    companion object {
        val codec = Codec.of<Car>(
            Field("name", Primitives.StringCodec, Car::name),
            Field("driver", Person.codec, Car::driver),
            Field("passenger", Person.codec, Car::passenger)
        )
    }
}

data class Person(val name: String, val age: Int) {
    companion object {
        val codec = Codec.of<Person>(
            Field("name", Primitives.StringCodec, Person::name),
            Field("age", Primitives.IntCodec, Person::age),
        )
    }
}