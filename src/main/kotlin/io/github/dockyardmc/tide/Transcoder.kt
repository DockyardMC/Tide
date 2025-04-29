package io.github.dockyardmc.tide

import java.util.UUID
import kotlin.reflect.KClass

//my coder is WHAT? 🏳️‍⚧️🏳️‍⚧️🏳️‍⚧️
interface Transcoder<T> {

    fun writeInt(format: T, field: String, value: Int)

    fun writeString(format: T, field: String, value: String)

    fun writeBoolean(format: T, field: String, value: Boolean)

    fun writeVarInt(format: T, field: String, value: Int)

    fun writeByteArray(format: T, field: String, value: ByteArray)

    fun writeUUID(format: T, field: String, value: UUID)

    fun writeLong(format: T, field: String, value: Long)

    fun writeFloat(format: T, field: String, value: Float)

    fun writeDouble(format: T, field: String, value: Double)

    fun writeByte(format: T, field: String, value: Byte)

    //-----------------------------------------------------------//

    fun <D> writeOptional(format: T, field: String, value: D?, codec: Codec<D>)

    fun <D> readOptional(format: T, field: String, codec: Codec<D>): D?


    fun <D> writeList(format: T, field: String, value: List<D>, codec: Codec<D>)

    fun <D> readList(format: T, field: String, codec: Codec<D>): List<D>


    fun <K, V> writeMap(format: T, field: String, value: Map<K, V>, keyCodec: Codec<K>, valueCodec: Codec<V>)

    fun <K, V> readMap(format: T, field: String, keyCodec: Codec<K>, valueCodec: Codec<V>): Map<K, V>


    fun <E> writeEnum(kClass: KClass<*>, format: T, field: String, value: E)

    fun <E> readEnum(kClass: KClass<*>, format: T, field: String): E

    //-----------------------------------------------------------//

    fun readInt(format: T, field: String): Int

    fun readString(format: T, field: String): String

    fun readBoolean(format: T, field: String): Boolean

    fun readVarInt(format: T, field: String): Int

    fun readByteArray(format: T, field: String): ByteArray

    fun readUUID(format: T, field: String): UUID

    fun readLong(format: T, field: String): Long

    fun readFloat(format: T, field: String): Float

    fun readDouble(format: T, field: String): Double

    fun readByte(format: T, field: String): Byte

}