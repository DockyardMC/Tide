package io.github.dockyardmc.tide

import io.github.dockyardmc.tide.codec.Codec
import io.github.dockyardmc.tide.codec.StructCodec
import io.github.dockyardmc.tide.stream.StreamCodec
import io.github.dockyardmc.tide.transcoder.JsonTranscoder
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UnionCodecTest {

    sealed interface ConsumeEffect {
        companion object {
            val STREAM_CODEC = StreamCodec.enum<Type>().union(
                { type ->
                    when (type) {
                        Type.APPLY_EFFECTS -> ApplyEffects.STREAM_CODEC
                        Type.REMOVE_EFFECTS -> RemoveEffects.STREAM_CODEC
                        Type.PLAY_SOUND -> PlaySound.STREAM_CODEC
                        Type.TELEPORT -> Teleport.STREAM_CODEC
                    }
                },
                { consumeEffect ->
                    when (consumeEffect) {
                        is ApplyEffects -> Type.APPLY_EFFECTS
                        is RemoveEffects -> Type.REMOVE_EFFECTS
                        is PlaySound -> Type.PLAY_SOUND
                        is Teleport -> Type.TELEPORT
                    }
                })

            val CODEC = Codec.enum<Type>().union<ConsumeEffect>(
                { type ->
                    when (type) {
                        Type.APPLY_EFFECTS -> ApplyEffects.CODEC
                        Type.REMOVE_EFFECTS -> RemoveEffects.CODEC
                        Type.PLAY_SOUND -> PlaySound.CODEC
                        Type.TELEPORT -> Teleport.CODEC
                    }
                },
                { consumeEffect ->
                    when (consumeEffect) {
                        is ApplyEffects -> Type.APPLY_EFFECTS
                        is RemoveEffects -> Type.REMOVE_EFFECTS
                        is PlaySound -> Type.PLAY_SOUND
                        is Teleport -> Type.TELEPORT
                    }
                })

        }

        fun consumeEffectToType(consumeEffect: ConsumeEffect): Type {
            return when (consumeEffect) {
                is ApplyEffects -> Type.APPLY_EFFECTS
                is RemoveEffects -> Type.REMOVE_EFFECTS
                is PlaySound -> Type.PLAY_SOUND
                is Teleport -> Type.TELEPORT
            }
        }

        enum class Type {
            APPLY_EFFECTS,
            REMOVE_EFFECTS,
            PLAY_SOUND,
            TELEPORT
        }

        data class ApplyEffects(val effects: List<String>) : ConsumeEffect {
            companion object {
                val CODEC = StructCodec.of(
                    "effects", Codec.STRING.list(), ApplyEffects::effects,
                    ::ApplyEffects
                )

                val STREAM_CODEC = StreamCodec.of(
                    StreamCodec.STRING.list(), ApplyEffects::effects,
                    ::ApplyEffects
                )
            }
        }

        data class RemoveEffects(val effects: List<String>) : ConsumeEffect {
            companion object {
                val CODEC = StructCodec.of(
                    "effects", Codec.STRING.list(), RemoveEffects::effects,
                    ::RemoveEffects
                )

                val STREAM_CODEC = StreamCodec.of(
                    StreamCodec.STRING.list(), RemoveEffects::effects,
                    ::RemoveEffects
                )
            }
        }

        data class PlaySound(val sound: String, val range: Float? = null) : ConsumeEffect {
            companion object {
                val CODEC = StructCodec.of(
                    "sound", Codec.STRING, PlaySound::sound,
                    "range", Codec.FLOAT.optional(), PlaySound::range,
                    ::PlaySound
                )

                val STREAM_CODEC = StreamCodec.of(
                    StreamCodec.STRING, PlaySound::sound,
                    StreamCodec.FLOAT.optional(), PlaySound::range,
                    ::PlaySound
                )
            }
        }

        data class Teleport(val radius: Float? = 1.6f) : ConsumeEffect {
            companion object {
                val CODEC = StructCodec.of(
                    "radius", Codec.FLOAT.optional(), Teleport::radius,
                    ::Teleport
                )

                val STREAM_CODEC = StreamCodec.of(
                    StreamCodec.FLOAT.optional(), Teleport::radius,
                    ::Teleport
                )
            }
        }

    }

    @Test
    fun testUnionCodec() {
        val tp = ConsumeEffect.ApplyEffects(listOf("minecraft:speed", "minecraft:gayness"))
        val encoded = ConsumeEffect.CODEC.encode(JsonTranscoder, tp)
        val decoded = ConsumeEffect.CODEC.decode(JsonTranscoder, encoded)
        assertEquals(tp, decoded)
    }

    @Test
    fun testUnionStreamCodec() {
        val tp = ConsumeEffect.ApplyEffects(listOf("minecraft:speed", "minecraft:gayness"))
        val buffer = Unpooled.buffer()
        ConsumeEffect.STREAM_CODEC.write(buffer, tp)
        val decoded = ConsumeEffect.STREAM_CODEC.read(buffer)
        assertEquals(tp, decoded)
    }

}