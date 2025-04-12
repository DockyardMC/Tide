import com.google.gson.JsonObject
import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Codecs
import io.netty.buffer.Unpooled
import java.util.*
import kotlin.test.assertEquals

class CodecTest {

    private val uuid = UUID.fromString("0c9151e4-7083-418d-a29c-bbc58f7c741b")
    private val player = Player("LukynkaCZE", null, mapOf(DisplayedSkinPart.HAT to true, DisplayedSkinPart.LEFT_PANTS to false), Location(Vector3d(0.0, 3.0, 6.9), World("main")))
    val testObject = Test(player, 69, 4.20, 6L, 0x4, 3.3f, uuid, WrappedByteArray(Unpooled.buffer().writeBoolean(true).array()), TestEnum.OPTION3, false)

    enum class DisplayedSkinPart(val bit: Byte) {
        CAPE(0x01),
        JACKET(0x02),
        LEFT_SLEEVE(0x04),
        RIGHT_SLEEVE(0x08),
        LEFT_PANTS(0x10),
        RIGHT_PANTS(0x20),
        HAT(0x40),
        UNUSED(0x80.toByte())
    }

    data class World(val name: String) {
        companion object {
            val codec = Codec.of<World> {
                field("name", Codecs.String, World::name)
            }
        }
    }

    data class Vector3d(val x: Double, val y: Double, val z: Double) {
        companion object {
            val codec = Codec.of<Vector3d> {
                field("x", Codecs.Double, Vector3d::x)
                field("y", Codecs.Double, Vector3d::y)
                field("z", Codecs.Double, Vector3d::z)
            }
        }
    }

    data class Location(val position: Vector3d, val world: World) {
        companion object {
            val codec = Codec.of<Location> {
                field("position", Vector3d.codec, Location::position)
                field("world", World.codec, Location::world)
            }
        }
    }

    data class Player(
        val username: String,
        val uuid: UUID?,
        val displayedSkinPart: Map<DisplayedSkinPart, Boolean>?,
        val location: Location,
    ) {
        companion object {
            val codec = Codec.of<Player> {
                field("username", Codecs.String, Player::username)
                field("uuid", Codecs.UUID.optional(), Player::uuid)
                field("display_skin_parts", Codec.enum(DisplayedSkinPart::class).mapAsKeyTo(Codecs.Boolean).optional(), Player::displayedSkinPart)
                field("location", Location.codec, Player::location)
            }
        }
    }

    data class WrappedByteArray(val value: ByteArray) {

        override fun equals(other: Any?): Boolean {
            if (other == null || other !is WrappedByteArray) return false
            if (other.value.size != this.value.size) return false
            return true
        }

        override fun hashCode(): Int {
            return value.size
        }

        companion object {
            val codec = Codec.of<WrappedByteArray> {
                field("value", Codecs.ByteArray, WrappedByteArray::value)
            }
        }
    }

    enum class TestEnum {
        OPTION1,
        OPTION2,
        OPTION3,
        OPTION4
    }

    data class Test(
        val player: Player,
        val int: Int,
        val double: Double,
        val long: Long,
        val byte: Byte,
        val float: Float,
        val uuid: UUID,
        val byteArray: WrappedByteArray,
        val enum: TestEnum,
        val boolean: Boolean,
    ) {
        companion object {
            val codec = Codec.of<Test> {
                field("player", Player.codec, Test::player)
                field("int", Codecs.Int, Test::int)
                field("double", Codecs.Double, Test::double)
                field("long", Codecs.Long, Test::long)
                field("byte", Codecs.Byte, Test::byte)
                field("float", Codecs.Float, Test::float)
                field("uuid", Codecs.UUID, Test::uuid)
                field("byteArray", WrappedByteArray.codec, Test::byteArray)
                field("enum", Codec.enum(TestEnum::class), Test::enum)
                field("boolean", Codecs.Boolean, Test::boolean)
            }
        }
    }

    @kotlin.test.Test
    fun testReadWriteNetwork() {
        val buffer = Unpooled.buffer()

        Test.codec.writeNetwork(buffer, testObject)

        val newObject = Test.codec.readNetwork(buffer)

        assertEquals(testObject, newObject)

        buffer.release()
    }

    @kotlin.test.Test
    fun testReadWriteJson() {
        val json = JsonObject()

        Test.codec.writeJson(json, testObject)

        val newObject = Test.codec.readJson(json)

        assertEquals(testObject, newObject)
    }
}