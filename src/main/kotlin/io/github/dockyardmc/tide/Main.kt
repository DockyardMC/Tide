package io.github.dockyardmc.tide

import cz.lukynka.prettylog.log
import io.netty.buffer.Unpooled
import kotlin.system.measureTimeMillis

fun main() {
    val car = Car("Mercedes GLA", Person("Maya", 69), Person("Pikachu", 420))
    val buffer = Unpooled.buffer()
    val time = measureTimeMillis {
        repeat(20) {
            Car.codec.writeNetwork(buffer, car)

            buffer.readerIndex(0)

            val newCar = Car.codec.readNetwork(buffer)
            log(newCar.toString())
        }
    }
    log("Took $time ms")
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
            Primitives.StringCodec to Person::name,
            Primitives.IntCodec to Person::age
        )
    }
}