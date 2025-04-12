import cz.lukynka.prettylog.LogType
import cz.lukynka.prettylog.log
import io.netty.buffer.Unpooled
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class PerformanceBenchmarks {

    @Test
    fun runBenchmarks() {
        val testObject: CodecTest.Test

        val create = measureTimeMillis {
            testObject = CodecTest().testObject
        }

        val buffer = Unpooled.buffer()

        val first = measureTimeMillis {
            CodecTest.Test.codec.writeNetwork(buffer, testObject)
        }

        buffer.resetReaderIndex()

        val subsequent = measureTimeMillis {
            CodecTest.Test.codec.writeNetwork(buffer, testObject)
        }

        val hundred = measureTimeMillis {
            repeat(100) { _ ->
                CodecTest.Test.codec.writeNetwork(buffer, testObject)
            }
        }

        log("Class create took: ${create}ms", LogType.PERFORMANCE)
        log("First took: ${first}ms", LogType.PERFORMANCE)
        log("Subsequent took: ${subsequent}ms", LogType.PERFORMANCE)
        log("Hundred took: ${hundred}ms", LogType.PERFORMANCE)
    }
}