import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dockyardmc.tide.Codec
import io.github.dockyardmc.tide.Codecs
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class ListCodecTest {

    data class Person(val name: String, val age: Int) {
        companion object {
            val codec = Codec.of(
                "name", Codecs.String, Person::name,
                "age", Codecs.Int, Person::age,
                ::Person
            )
        }
    }

    private val list = listOf<Person>(
        Person("Maya", 23),
        Person("Aso", 18),
        Person("Kev", 25)
    )

    @Test
    fun testReadWriteNetwork() {
        val buffer = Unpooled.buffer()
        Person.codec.list().writeNetwork(buffer, list)

        val newList = Person.codec.list().readNetwork(buffer)
        assertEquals(list, newList)
        buffer.release()
    }

    @Test
    fun testReadWriteJsonArrayRoot() {
        val json = JsonArray()
        Person.codec.list().writeJson(json, list)

        val newList = Person.codec.list().readJson(json)
        assertEquals(list, newList)
        assertEquals("[Person(name=Maya, age=23), Person(name=Aso, age=18), Person(name=Kev, age=25)]", newList.toString())
    }

    @Test
    fun testReadWriteJsonObjectRoot() {
        val json = JsonObject()
        assertThrows<IllegalStateException> {
            Person.codec.list().writeJson(json, list)
        }
    }

    @Test
    fun testReadWriteJsonObject() {
        val json = JsonObject()
        Person.codec.list().writeJson(json, list, "awesome_people")

        val newList = Person.codec.list().readJson(json, "awesome_people")
        assertEquals(list, newList)
        assertEquals("[Person(name=Maya, age=23), Person(name=Aso, age=18), Person(name=Kev, age=25)]", newList.toString())
    }
}