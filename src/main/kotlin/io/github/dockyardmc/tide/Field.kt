package io.github.dockyardmc.tide

/**
 * A [Codec] field
 *
 * @param T The type of object this field is for
 * @property name Name of the field. Used for serializing JSON and NBT
 * @property codec [Codec] of the field which is used to [Codec.readNetwork] or [Codec.writeNetwork]
 * @property getter Reference to the class field in the class constructor (Example: Person::name)
 */
class Field<T>(val name: String, val codec: Codec<*>, val getter: (T) -> Any?)