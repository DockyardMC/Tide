import cz.lukynka.prettylog.LogType
import cz.lukynka.prettylog.log
import io.github.dockyardmc.tide.Codecs
import io.github.dockyardmc.tide.StructCodec
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test

class StructTest {

    data class Person(val name: String) {
        companion object {
            val CODEC = StructCodec.struct(
                "name", Codecs.String, Person::name,
                ::Person
            )
        }
    }

    @Test
    fun testStruct() {
        val buffer = Unpooled.buffer()
        val person = Person("Maya")
        Person.CODEC.writeNetwork(buffer, person)

        val newPerson = Person.CODEC.readNetwork(buffer)
        log("$newPerson", LogType.DEBUG)
    }

}