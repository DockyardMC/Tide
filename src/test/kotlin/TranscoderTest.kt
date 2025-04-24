import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Codecs
import java.util.*

class TranscoderTest {

    data class AnotherCoolClass(
        val someString: String,
        val someOptionalInt: Int?,
    ) {
        companion object {
            val codec = Codec.of(
                "some_string", Codecs.String, AnotherCoolClass::someString,
                "some_optional_int", Codecs.VarInt.optional(), AnotherCoolClass::someOptionalInt,
                ::AnotherCoolClass
            )
        }
    }

    data class Meower(
        val accessories: List<Accessory>,
        val age: Int
    ) {
        companion object {
            val codec = Codec.of(
                "accessories", Accessory.codec.list(), Meower::accessories,
                "age", Codecs.Int, Meower::age,
                ::Meower
            )
        }
    }

    data class Accessory(val name: String, val id: UUID) {
        companion object {
            val codec = Codec.of(
                "name", Codecs.String, Accessory::name,
                "id", Codecs.UUID, Accessory::id,
                ::Accessory
            )
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
            val codec = Codec.of(
                "another_object", AnotherCoolClass.codec, CoolClass::anotherObject,
                "meower_to_name_map", Meower.codec.mapAsKeyTo(Codecs.String), CoolClass::meowerToNameMap,
                "enum", Codec.enum<CoolClassType>().optional(), CoolClass::enum,
                ::CoolClass
            )
        }
    }

}