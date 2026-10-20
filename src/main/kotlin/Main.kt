import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

data class Order(
    val id: String,
    val dishName: String,
    var cookingProgress: Int = 0,
    var status: String = "Ожидает"
)

data class DishResult(
    val orderId: String,
    val dishName: String,
    val qualityScore: Double
)

suspend fun prepareIngredient(ingredientName: String): String = withContext(Dispatchers.Default) {
    delay(1500L)
    when (ingredientName) {
        "Нога страуса" -> "Две ноги страуса"
        "Сгущенка" -> "Спущенка"
        else -> "Подготовленный $ingredientName"
    }
}

suspend fun cookOrder(order: Order) = coroutineScope {
    order.status = "Готовится"

    launch {
        while (isActive && order.cookingProgress < 100) {
            delay(500L)
            order.cookingProgress += 10
            println("${order.id}: ${order.dishName} — ${order.cookingProgress}%")
        }
    }

    launch {
        try {
            delay(2000L)
            if (order.dishName == "Подгоревшее") {
                throw RuntimeException("Блюдо подгорело")
            }
            println("${order.id}: температура в норме")
        } catch (e: Exception) {
            println("Ошибка в ${order.id}: ${e.message}")
        }
    }

    launch {
        while (isActive && order.cookingProgress < 80) {
            delay(100L)
        }
        if (isActive) {
            println("Блюдо почти готово, уже добавляем секретный соус")
        }
    }
}

suspend fun checkDishQuality(orderId: String): Deferred<DishResult> = coroutineScope {
    async {
        delay(3000L)
        DishResult(orderId, "Блюдо $orderId", 3.3)
    }
}

suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000L)
    job.cancel()
    println("Заказ ${order.id} отменён, блюдо ${order.dishName} снято с приготовления")
    job.join()
}

fun main() = runBlocking {
    println(prepareIngredient("Нога страуса"))

    val order = Order("1", "Макарони и сосиса")
    launch { cookOrder(order) }.join()

    val quality = checkDishQuality("1").await()
    println("Оценка качества: ${quality.qualityScore}")

    val cancelTarget = Order("2", "Ле борщ")
    val cancelJob = launch { cookOrder(cancelTarget) }
    cancelOrder(cancelTarget, cancelJob)

    val channel = Channel<DishResult>()

    val cook = launch {
        repeat(5) { i ->
            channel.send(DishResult("$i", "Блюдо $i", 8.0 + i * 0.3))
        }
        channel.close()
    }

    val consumer = launch {
        for (r in channel) {
            println("Выдано: ${r.orderId}, ${r.dishName}, качество ${r.qualityScore}")
        }
    }

    cook.join()
    consumer.join()

    val bad = Order("3", "Подгоревшее")
    val good = Order("4", "Сендвич со спущенкой")
    launch { cookOrder(bad) }
    launch { cookOrder(good) }.join()
}