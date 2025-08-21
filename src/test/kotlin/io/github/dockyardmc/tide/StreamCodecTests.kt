package io.github.dockyardmc.tide

import io.github.dockyardmc.tide.stream.StreamCodec
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class StreamCodecTests {

    data class Player(val username: String, val uuid: UUID, val data: PlayerData?) {

        companion object {
            val STREAM_CODEC = StreamCodec.of(
                StreamCodec.STRING, Player::username,
                StreamCodec.UUID, Player::uuid,
                PlayerData.STREAM_CODEC.optional(), Player::data,
                ::Player
            )
        }

        data class PlayerData(
            val isGay: Boolean,
            val banReason: String?,
            val rank: Rank
        ) {
            companion object {
                val STREAM_CODEC = StreamCodec.of(
                    StreamCodec.BOOLEAN, PlayerData::isGay,
                    StreamCodec.STRING.optional(), PlayerData::banReason,
                    StreamCodec.enum<Rank>(), PlayerData::rank,
                    ::PlayerData
                )
            }
        }

        enum class Rank {
            ADMIN,
            PLAYER,
            MODERATOR
        }
    }

    @Test
    fun testWriteRead() {
        val player = Player("LukynkaCZE", UUID.randomUUID(), Player.PlayerData(true, null, Player.Rank.ADMIN))
        val buffer = Unpooled.buffer()
        Player.STREAM_CODEC.write(buffer, player)
        val decoded = Player.STREAM_CODEC.read(buffer)
        assertEquals(player, decoded)
    }

}