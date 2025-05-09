package io.github.dockyardmc.tide.transcoders

import com.moandjiezana.toml.TomlWriter
import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Transcoder
import java.lang.IllegalStateException
import java.util.*
import kotlin.jvm.java
import kotlin.reflect.KClass

object TomlTranscoder : Transcoder<Toml> {

    override fun writeInt(format: Toml, field: String, value: Int) {
        format.put(field, value)
    }

    override fun writeString(format: Toml, field: String, value: String) {
        format.put(field, value)
    }

    override fun writeBoolean(format: Toml, field: String, value: Boolean) {
        format.put(field, value)
    }

    override fun writeVarInt(format: Toml, field: String, value: Int) {
        format.put(field, value)
    }

    override fun writeByteArray(format: Toml, field: String, value: ByteArray) {
        format.put(field, value)
    }

    override fun writeUUID(format: Toml, field: String, value: UUID) {
        format.put(field, value)
    }

    override fun writeLong(format: Toml, field: String, value: Long) {
        format.put(field, value)
    }

    override fun writeFloat(format: Toml, field: String, value: Float) {
        format.put(field, value)
    }

    override fun writeDouble(format: Toml, field: String, value: Double) {
        format.put(field, value)
    }

    override fun writeByte(format: Toml, field: String, value: Byte) {
        format.put(field, value)
    }

    override fun <D> writeOptional(format: Toml, field: String, value: D?, codec: Codec<D>) {
        if(value == null) return
        format.put(field, value)
    }

    override fun <D> readOptional(format: Toml, field: String, codec: Codec<D>): D? {
        return format.getOrNull(field)
    }

    override fun <D> writeList(format: Toml, field: String, value: List<D>, codec: Codec<D>) {
        format.put(field, value)
    }

    override fun <D> readList(format: Toml, field: String, codec: Codec<D>): List<D> {
        return format.get<List<D>>(field)
    }

    override fun <K, V> writeMap(format: Toml, field: String, value: Map<K, V>, keyCodec: Codec<K>, valueCodec: Codec<V>) {
        return
    }

    override fun <K, V> readMap(format: Toml, field: String, keyCodec: Codec<K>, valueCodec: Codec<V>): Map<K, V> {
        return mapOf()
    }

    override fun <E> writeEnum(kClass: KClass<*>, format: Toml, field: String, value: E) {
        format.put(field, (value as Enum<*>).name)
    }

    override fun <E> readEnum(kClass: KClass<*>, format: Toml, field: String): E {
        val name = format.get<String>(field)
        return kClass.java.enumConstants.first { constant -> (constant as Enum<*>).name == name } as E
    }

    override fun readInt(format: Toml, field: String): Int {
        return format.get<Int>(field)
    }

    override fun readString(format: Toml, field: String): String {
        return format.get<String>(field)
    }

    override fun readBoolean(format: Toml, field: String): Boolean {
        return format.get(field)
    }

    override fun readVarInt(format: Toml, field: String): Int {
        return format.get<Int>(field)
    }

    override fun readByteArray(format: Toml, field: String): ByteArray {
        return format.get(field)
    }

    override fun readUUID(format: Toml, field: String): UUID {
        return format.get(field)
    }

    override fun readLong(format: Toml, field: String): Long {
        return format.get(field)
    }

    override fun readFloat(format: Toml, field: String): Float {
        return format.get(field)
    }

    override fun readDouble(format: Toml, field: String): Double {
        return format.get(field)
    }

    override fun readByte(format: Toml, field: String): Byte {
        return format.get(field)
    }

}

class Toml(private val map: MutableMap<String, Any> = mutableMapOf()) {

    fun put(key: String, any: Any) {
        map[key] = any
    }

    fun <T> getOrNull(key: String): T? {
        return map[key] as T?
    }

    fun <T> get(key: String): T {
        return getOrNull<T>(key) ?: throw IllegalStateException("Value with key `$key` is null (contents of map: $map)")
    }

    fun getAsString(): String {
        return TomlWriter().write(map)
    }

    companion object {
        fun fromString(string: String): Toml {
            return Toml(com.moandjiezana.toml.Toml().read(string).toMap().toMutableMap())
        }
    }
}