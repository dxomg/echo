package dev.brahmkshatriya.echo.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlin.experimental.ExperimentalTypeInference

object CoroutineUtils {
    fun setDebug() {
        System.setProperty(
            kotlinx.coroutines.DEBUG_PROPERTY_NAME,
            kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
        )
    }

    suspend fun <T, R> Flow<T>.collectWith(flow: Flow<R>, block: suspend (T, R) -> Unit) {
        this.combine(flow) { t, r -> t to r }.collectLatest { (t, r) -> block(t, r) }
    }

    fun <T> Flow<T>.throttleLatest(delayMillis: Long): Flow<T> = conflate().transform {
        emit(it)
        delay(delayMillis)
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTypeInference::class)
    inline fun <reified T, R> combineTransformLatest(
        vararg flows: Flow<T>,
        @BuilderInference noinline transform: suspend FlowCollector<R>.(Array<T>) -> Unit
    ): Flow<R> {
        return combine(*flows) { it }
            .transformLatest(transform)
    }

    @OptIn(ExperimentalTypeInference::class)
    fun <T1, T2, R> Flow<T1>.combineTransformLatest(
        flow2: Flow<T2>,
        @BuilderInference transform: suspend FlowCollector<R>.(T1, T2) -> Unit
    ): Flow<R> {
        return combineTransformLatest(this, flow2) { args ->
            @Suppress("UNCHECKED_CAST")
            transform(
                args[0] as T1,
                args[1] as T2
            )
        }
    }

}