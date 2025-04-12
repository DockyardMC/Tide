package io.github.dockyardmc.tide.transcoders

import com.moandjiezana.toml.TomlWriter
import io.github.dockyardmc.tide.Transcoder
import java.lang.IllegalStateException
import java.util.*
import kotlin.reflect.KClass


object TomlTranscoder : Transcoder<TomlMap>() {

    override fun writeInt(format: TomlMap, field: String, value: Int) {
        format.put(field, value)
    }

    override fun writeString(format: TomlMap, field: String, value: String) {
        format.put(field, value)
    }

    override fun writeBoolean(format: TomlMap, field: String, value: Boolean) {
        format.put(field, value)
    }

    override fun writeVarInt(format: TomlMap, field: String, value: Int) {
        format.put(field, value)
    }

    override fun writeByteArray(format: TomlMap, field: String, value: ByteArray) {
        format.put(field, value)
    }

    override fun writeUUID(format: TomlMap, field: String, value: UUID) {
        format.put(field, value)
    }

    override fun writeLong(format: TomlMap, field: String, value: Long) {
        format.put(field, value)
    }

    override fun writeFloat(format: TomlMap, field: String, value: Float) {
        format.put(field, value)
    }

    override fun writeDouble(format: TomlMap, field: String, value: Double) {
        format.put(field, value)
    }

    override fun writeByte(format: TomlMap, field: String, value: Byte) {
        format.put(field, value)
    }

    override fun <D> writeOptional(format: TomlMap, field: String, value: D?) {
        if(value == null) return
        format.put(field, value)
    }

    override fun <D> readOptional(format: TomlMap, field: String): D? {
        return format.getOrNull(field)
    }

    override fun <D> writeList(format: TomlMap, field: String, value: List<D>) {
        format.put(field, value)
    }

    override fun <D> readList(format: TomlMap, field: String): List<D> {
        return format.get<List<D>>(field)
    }

    override fun <K, V> writeMap(format: TomlMap, field: String, value: Map<K, V>) {
        return
    }

    override fun <K, V> readMap(format: TomlMap, field: String): Map<K, V> {
        return mapOf()
    }

    override fun <E : Enum<E>> writeEnum(kClass: KClass<out Enum<E>>, format: TomlMap, field: String, value: Enum<E>) {
        format.put(field, value.name)
    }

    override fun <E : Enum<E>> readEnum(kClass: KClass<out Enum<E>>, format: TomlMap, field: String): E {
        val name = format.get<String>(field)
        return kClass.java.enumConstants.first { constant -> constant.name == name } as E
    }

    override fun readInt(format: TomlMap, field: String): Int {
        TODO("Not yet implemented")
    }

    override fun readString(format: TomlMap, field: String): String {
        TODO("Not yet implemented")
    }

    override fun readBoolean(format: TomlMap, field: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun readVarInt(format: TomlMap, field: String): Int {
        TODO("Not yet implemented")
    }

    override fun readByteArray(format: TomlMap, field: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readUUID(format: TomlMap, field: String): UUID {
        TODO("Not yet implemented")
    }

    override fun readLong(format: TomlMap, field: String): Long {
        TODO("Not yet implemented")
    }

    override fun readFloat(format: TomlMap, field: String): Float {
        TODO("Not yet implemented")
    }

    override fun readDouble(format: TomlMap, field: String): Double {
        TODO("Not yet implemented")
    }

    override fun readByte(format: TomlMap, field: String): Byte {
        TODO("Not yet implemented")
    }

}

class TomlMap() {
    private val map: MutableMap<String, Any> = mutableMapOf()

    fun put(key: String, any: Any) {
        map[key] = any
    }

    fun <T> getOrNull(key: String): T? {
        return map[key] as T?
    }

    fun <T> get(key: String): T {
        return getOrNull<T>(key) ?: throw IllegalStateException("Value is null")
    }

    fun getAsString(): String {
        return TomlWriter().write(map)
    }
}