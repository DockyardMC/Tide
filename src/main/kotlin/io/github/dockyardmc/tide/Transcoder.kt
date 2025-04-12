package io.github.dockyardmc.tide

import java.util.UUID
import kotlin.reflect.KClass

//my coder is WHAT? 🏳️‍⚧️🏳️‍⚧️🏳️‍⚧️
abstract class Transcoder<T> {

    abstract fun writeInt(format: T, field: String, value: Int)

    abstract fun writeString(format: T, field: String, value: String)

    abstract fun writeBoolean(format: T, field: String, value: Boolean)

    abstract fun writeVarInt(format: T, field: String, value: Int)

    abstract fun writeByteArray(format: T, field: String, value: ByteArray)

    abstract fun writeUUID(format: T, field: String, value: UUID)

    abstract fun writeLong(format: T, field: String, value: Long)

    abstract fun writeFloat(format: T, field: String, value: Float)

    abstract fun writeDouble(format: T, field: String, value: Double)

    abstract fun writeByte(format: T, field: String, value: Byte)

    //-----------------------------------------------------------//

    abstract fun <D> writeOptional(format: T, field: String, value: D?)

    abstract fun <D> readOptional(format: T, field: String): D?


    abstract fun <D> writeList(format: T, field: String, value: List<D>)

    abstract fun <D> readList(format: T, field: String): List<D>


    abstract fun <K, V> writeMap(format: T, field: String, value: Map<K, V>)

    abstract fun <K, V> readMap(format: T, field: String): Map<K, V>


    abstract fun <E : Enum<E>> writeEnum(kClass: KClass<out Enum<E>>, format: T, field: String, value: Enum<E>)

    abstract fun <E : Enum<E>> readEnum(kClass: KClass<out Enum<E>>, format: T, field: String): E

    //-----------------------------------------------------------//

    abstract fun readInt(format: T, field: String): Int

    abstract fun readString(format: T, field: String): String

    abstract fun readBoolean(format: T, field: String): Boolean

    abstract fun readVarInt(format: T, field: String): Int

    abstract fun readByteArray(format: T, field: String): ByteArray

    abstract fun readUUID(format: T, field: String): UUID

    abstract fun readLong(format: T, field: String): Long

    abstract fun readFloat(format: T, field: String): Float

    abstract fun readDouble(format: T, field: String): Double

    abstract fun readByte(format: T, field: String): Byte

}