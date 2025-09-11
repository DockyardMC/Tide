package io.github.dockyardmc.tide

import io.github.dockyardmc.tide.codec.Codec
import io.github.dockyardmc.tide.codec.StructCodec
import io.github.dockyardmc.tide.transcoder.JsonTranscoder
import org.junit.jupiter.api.Test

class DefaultCodecTest {

    data class Player(val username: String, val isGay: Boolean) {
        companion object {
            val CODEC = StructCodec.of(
                "username", Codec.STRING, Player::username,
                "is_gay", Codec.BOOLEAN.default(false), Player::isGay,
                ::Player
            )
        }
    }

    @Test
    fun testDefault() {
        val player = Player("LukynkaCZE", false)
        val encoded = Player.CODEC.encode(JsonTranscoder, player)

        assert(!encoded.toString().contains("isGay"))
    }

}