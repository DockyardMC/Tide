package io.github.dockyardmc.tide

import com.google.gson.JsonObject
import cz.lukynka.prettylog.log
import io.netty.buffer.Unpooled

fun main() {
    val bus = Bus("fuck do I know", listOf(Person("Maya", 23, PersonType.GAY), Person("Kev", 25, null)), true, mapOf(1 to true, 2 to false, 3 to true, 4 to false))

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

data class Bus(val model: String, val driver: Person, val passengers: List<Person>) {
    companion object {
        val codec = Codec.of<Bus> {
            field("name", Primitives.String, Bus::model)
            field("driver", Person.codec, Bus::driver)
            field("passengers", Person.codec.list(), Bus::passengers)
        }
    }
}


data class Book(val pages: List<String>, val synopsis: String?, val type: Book.Type) {

    enum class Type {
        HORROR,
        SCI_FI,
        MEDIEVAL,
        FANTASY,
        FICTION,
        ADVENTURE,
        THRILLER,
        MANGA,
        HENTAI
    }

    companion object {
        val codec = Codec.of<Book> {
            field("pages", Primitives.String.list(), Book::pages)
            field("synopsis", Primitives.String.optional(), Book::synopsis)
            field("type", Codec.enum(Book.Type::class), Book::type)
        }
    }
}

data class Person(val name: String, val age: Int) {
    companion object {
        val codec = Codec.of<Person> {
            field("name", Primitives.String, Person::name)
            field("age", Primitives.VarInt, Person::age)
        }
    }
}