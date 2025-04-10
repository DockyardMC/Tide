package io.github.dockyardmc.tide

class Field<T>(val name: String, val codec: Codec<*>, val getter: (T) -> Any?)