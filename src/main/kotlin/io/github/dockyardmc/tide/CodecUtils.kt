package io.github.dockyardmc.tide

import java.util.*

object CodecUtils {
    fun uuidToIntArray(uuid: UUID): IntArray {
        val uuidMost = uuid.mostSignificantBits
        val uuidLeast = uuid.leastSignificantBits
        return intArrayOf(
            (uuidMost shr 32).toInt(),
            uuidMost.toInt(),
            (uuidLeast shr 32).toInt(),
            uuidLeast.toInt()
        )
    }

    fun intArrayToUuid(array: IntArray): UUID {
        val uuidMost = array[0].toLong() shl 32 or (array[1].toLong() and 0xFFFFFFFFL)
        val uuidLeast = array[2].toLong() shl 32 or (array[3].toLong() and 0xFFFFFFFFL)

        return UUID(uuidMost, uuidLeast)
    }
}

