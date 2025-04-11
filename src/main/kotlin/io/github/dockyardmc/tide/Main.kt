package io.github.dockyardmc.tide

fun main() {
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