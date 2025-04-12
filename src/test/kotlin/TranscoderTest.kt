import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Codecs
import java.util.*

class TranscoderTest {

    data class AnotherCoolClass(
        val someString: String,
        val someOptionalInt: Int?,
    ) {
        companion object {
            val codec = Codec.of<AnotherCoolClass> {
                field("some_string", Codecs.String, AnotherCoolClass::someString)
                field("some_optional_int", Codecs.VarInt, AnotherCoolClass::someOptionalInt)
            }
        }
    }

    data class Meower(
        val accessories: List<Accessory>,
        val age: Int
    ) {
        companion object {
            val codec = Codec.of<Meower> {
                field("accessories", Accessory.codec.list(), Meower::accessories)
                field("age", Codecs.Int, Meower::age)
            }
        }
    }

    data class Accessory(val name: String, val id: UUID) {
        companion object {
            val codec = Codec.of<Accessory> {
                field("name", Codecs.String, Accessory::name)
                field("id", Codecs.UUID, Accessory::id)
            }
        }
    }

    enum class CoolClassType {
        SLIGHTLY_COOL,
        MODERATELY_COOL,
        COOL,
        VERY_COOL,
        HYPE_ASF
    }

    data class CoolClass(
        val anotherObject: AnotherCoolClass,
        val meowerToNameMap: Map<Meower, String>,
        val enum: CoolClassType?
    ) {
        companion object {
            val codec = Codec.of<CoolClass> {
                field("another_object", AnotherCoolClass.codec, CoolClass::anotherObject)
                field("meower_to_name_map", Meower.codec.mapAsKeyTo(Codecs.String), CoolClass::meowerToNameMap)
                field("enum", Codec.enum(CoolClassType::class), CoolClass::enum)
            }
        }
    }

}