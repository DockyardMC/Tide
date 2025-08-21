package io.github.dockyardmc.tide

import cz.lukynka.prettylog.LogType
import cz.lukynka.prettylog.log
import io.github.dockyardmc.tide.transcoder.JsonTranscoder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BasicCodecTest {

    @Test
    fun testEncodeDecode() {
        val person = Person("Maya", 69)
        val encoded = Person.CODEC.encode(JsonTranscoder, person)
        log("Encoded -> $encoded", LogType.DEBUG)

        val decoded = Person.CODEC.decode(JsonTranscoder, encoded)
        log("Decoded -> $decoded", LogType.DEBUG)

        assertEquals(person, decoded)
    }
}