package com.fieldbook.tracker.brapi

import java.util.function.IntConsumer

/**
 * Posts child ObservationUnits first (when any), then observations.
 * Injectable steps keep the orchestration JVM-testable without a live BrAPI server.
 */
fun interface TreeBrapiAsyncStep {
    fun run(onSuccess: Runnable, onFail: IntConsumer)
}

fun interface TreeBrapiAsyncTerminal {
    fun run(onFail: IntConsumer)
}

object TreeBrapiUploadSequence {

    @JvmStatic
    fun execute(
        hasChildUnits: Boolean,
        postChildUnits: TreeBrapiAsyncStep,
        postObservations: TreeBrapiAsyncTerminal,
        onFail: IntConsumer,
    ) {
        if (!hasChildUnits) {
            postObservations.run(onFail)
            return
        }
        postChildUnits.run(
            { postObservations.run(onFail) },
            onFail,
        )
    }
}
