package com.fieldbook.tracker.utilities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BackgroundUiTask {

    companion object {

        /**
         * Executes a background tas kasynchronously and then updates the UI on the main thread.
         *
         * This function uses Kotlin coroutines to manage asynchronous operations. It launches a new coroutine
         * on the IO dispatcher to execute the background task, and then switches to the main dispatcher
         * to update the UI with the results.
         ** If an exception occurs during the background task or UI update, the `onCanceled` block is executed.
         * The coroutine scope is canceled in the `finally` block to ensure proper resource cleanup.
         *
         * @param backgroundBlock The background task to be executed. This block is executed on the IO dispatcher.
         * @param uiBlock The UI update code to be executed. This block is executed on the main dispatcher.
         * @param onCanceled The code to be executed if the operation is canceled due to an exception.
         *
         * Example kotlin reflection of location methods:
         * BackgroundUiTask.execute(
         *                 backgroundBlock = ::queryFunction,
         *                 uiBlock = ::uiFunction,
         *                 onCanceled = ::uiFunction2
         *             )
         *
         * Example kotlin usage with lambdas
         *             BackgroundUiTask.execute(
         *                 backgroundBlock = {
         *                     queryFunction()
         *                     },
         *                 uiBlock = {
         *                     uiFunction()
         *                 },
         *                 onCanceled = { uiFunction2() })
         *
         * Java example with lambdas:
         * BackgroundUiTask.Companion.execute(continuation -> {
         *             queryFunction();
         *             return null;
         *         }, (continuation) -> {
         *             uiFunction();
         *             return null;
         *         }, continuation -> {
         *             uiFunction2();
         *             return null;
         *         });
         */
        fun execute(
            backgroundBlock: suspend () -> Unit,
            uiBlock: suspend () -> Unit,
            onCanceled: suspend () -> Unit
        ) {
            val scope =
                CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        backgroundBlock()
                        withContext(Dispatchers.Main) {
                            uiBlock()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        onCanceled()
                    }
                } finally {
                    scope.cancel()
                }
            }
        }
    }
}