package io.github.dockyardmc.tide

class CodecBuilder<T> {
    private val fields = mutableListOf<Field<T>>()

    /**
     * Adds a field to the final [ReflectiveCodec]
     *
     * @param name
     * @param codec
     * @param getter
     * @receiver
     */
    fun field(name: String, codec: Codec<*>, getter: (T) -> Any?) {
        fields.add(Field<T>(name, codec, getter))
    }

    fun constructor() {

    }

    /**
     * Creates a field from [codec] and [getter] and generated name ("property0", "property1" etc.) and adds it to the final [ReflectiveCodec]
     *
     * @param codec
     * @param getter
     * @receiver
     */
    fun field(codec: Codec<*>, getter: (T) -> Any?) {
        fields.add(Field<T>("property${Codec.propertyCounter.getAndIncrement()}", codec, getter))
    }


    /**
     * Returns the final list of fields
     *
     * @return [List]
     */
    fun getFinalFields(): List<Field<T>> {
        return fields.toList()
    }
}