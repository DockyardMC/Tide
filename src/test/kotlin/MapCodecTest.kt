import com.google.gson.JsonArray
import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Codecs
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

class MapCodecTest {

    data class Person(val name: String, val age: Int) {
        companion object {
            val codec = Codec.of<Person> {
                field("name", Codecs.String, Person::name)
                field("age", Codecs.Int, Person::age)
            }
        }
    }

    private val shortPeople = mapOf<Person, Boolean>(
        Person("Maya", 23) to false,
        Person("Aso", 18) to true,
        Person("Kev", 25) to false
    )

    @Test
    fun testReadWriteNetwork() {
        val buffer = Unpooled.buffer()
        Person.codec.mapAsKeyTo(Codecs.Boolean).writeNetwork(buffer, shortPeople)

        val newMap = Codecs.Boolean.mapAsValueTo(Person.codec).readNetwork(buffer)
        assertEquals(shortPeople, newMap)
        buffer.release()
    }

    @Test
    fun testReadWriteJsonArray() {
        val json = JsonArray()
        Person.codec.mapAsKeyTo(Codecs.Boolean).writeJson(json, shortPeople)

        val newMap = Codecs.Boolean.mapAsValueTo(Person.codec).readJson(json)
        assertEquals(shortPeople, newMap)
    }
}