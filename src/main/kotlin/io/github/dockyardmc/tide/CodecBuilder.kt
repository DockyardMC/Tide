package io.github.dockyardmc.tide

class CodecBuilder<T> {
    private val fields = mutableListOf<Field<T>>()

    fun field(name: String, codec: Codec<*>, getter: (T) -> Any?) {
        fields.add(Field<T>(name, codec, getter))
    }

    fun field(codec: Codec<*>, getter: (T) -> Any?) {
        fields.add(Field<T>("property${Codec.propertyCounter.getAndIncrement()}", codec, getter))
    }

    fun getBuiltFields(): List<Field<T>> {
        return fields.toList()
    }
}