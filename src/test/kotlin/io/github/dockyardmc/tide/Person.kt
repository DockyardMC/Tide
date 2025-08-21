package io.github.dockyardmc.tide

import io.github.dockyardmc.tide.codec.Codec
import io.github.dockyardmc.tide.codec.StructCodec

data class Person(val name: String, val age: Int) {
    companion object {
        val CODEC = StructCodec.of(
            "name", Codec.STRING, Person::name,
            "age", Codec.INT, Person::age,
            ::Person
        )
    }
}