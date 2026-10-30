import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.math.roundToInt

data class SensorReading(val sensorId: String, val value: Double, val timestamp: Long)

suspend fun emitSensorData(sensorId: String, channel: SendChannel<SensorReading>) = coroutineScope {
    launch {
        for (i in 1..10) {
            delay(500)
            val randomVal = (Random.nextDouble(10.0, 50.0) * 100).roundToInt() / 100.0
            val reading = SensorReading(sensorId, randomVal, System.currentTimeMillis())
            channel.send(reading)
        }
    }
}

fun sensorFlow(sensorId: String): Flow<SensorReading> = flow {
    println("-> Поток Flow для датчика $sensorId начал генерацию данных...")
    for (i in 1..10) {
        delay(300)
        val randomVal = (Random.nextDouble(10.0, 50.0) * 100).roundToInt() / 100.0
        val reading = SensorReading(sensorId, randomVal, System.currentTimeMillis())
        emit(reading)
    }
}

fun main() = runBlocking {
    println("Горячий источник")
    val channel = Channel<SensorReading>()

    launch {
        val job1 = launch { emitSensorData("TEMP", channel) }
        val job2 = launch { emitSensorData("HUM", channel) }
        val job3 = launch { emitSensorData("CO2", channel) }

        joinAll(job1, job2, job3)
        channel.close()
    }

    var count = 0
    for (msg in channel) {
        count++
        println("Канал [$count/30]: Датчик ${msg.sensorId} | Значение = ${msg.value}")
    }

    println("\nХолодный источник")

    val processedFlow = sensorFlow("TEMP")
        .filter { it.value > 20.0 }
        .map {
            val calibrated = (it.value * 1.1 * 100).roundToInt() / 100.0
            it.copy(value = calibrated)
        }
        .take(5)
        .onEach { println("Обработано в цепочке Flow: $it") }

    println("Цепочка Flow создана, но генерация еще НЕ началась")
    delay(1000)

    println("\n1) Вызываем оператор toList():")
    val resultList = processedFlow.toList()
    println("Итоговый список из toList() (получено элементов: ${resultList.size}):")
    resultList.forEach { println(it) }

    println("\n2) Вызываем терминальный оператор fold() для подсчета среднего:")
    var elementsCount = 0
    val sum = processedFlow
        .onEach { elementsCount++ }
        .fold(0.0) { acc, reading -> acc + reading.value }

    val average = if (elementsCount > 0) sum / elementsCount else 0.0
    println("Сумма значений: ${"%.2f".format(sum)}")
    println("Среднее значение (из $elementsCount элементов): ${"%.2f".format(average)}")
}