package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.parameters.DEFAULT_DURATION_SECONDS
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories
import org.json.JSONObject

class PollinatorTraitLayout : BaseTraitLayout {

    companion object {
        private const val TAG = "PollinatorTraitLayout"
        internal const val COUNTS_KEY = "counts"
        internal const val DURATION_KEY = "duration_sec"

        //resolved once, the format definition is rebuilt on every getDatabaseName call
        private val TYPE = Formats.POLLINATOR.getDatabaseName()

        //categories are always defined by the trait, there are no built-in defaults
        internal fun categoriesFor(trait: TraitObject?): List<BrAPIScaleValidValuesCategories> {
            val json = trait?.categories
            if (json.isNullOrEmpty()) return emptyList()
            return try {
                CategoryJsonUtil.decodeCategories(json).filter { !it.label.isNullOrEmpty() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode categories: $json", e)
                emptyList()
            }
        }

        internal fun keyOf(category: BrAPIScaleValidValuesCategories): String =
            category.value?.takeIf { it.isNotEmpty() } ?: category.label
    }

    private var composeView: ComposeView? = null

    private val counts = mutableStateMapOf<String, Int>()
    private val elapsedSeconds = mutableIntStateOf(0)
    private val isRunning = mutableStateOf(false)
    private val isFinished = mutableStateOf(false)

    //mirrors the base isLocked field so the compose ui recomposes when the lock state changes
    private val isDataLocked = mutableStateOf(false)

    //mirrors the trait settings so the compose ui recomposes when a different trait is selected
    private val traitCategories = mutableStateOf<List<BrAPIScaleValidValuesCategories>>(emptyList())
    private val traitDuration = mutableIntStateOf(DEFAULT_DURATION_SECONDS)

    //cancels the timer coroutine when the layout is reset
    private var saveJob: Job? = null

    //tracks if the observation has been saved so we don't save twice
    @Volatile var hasSavedCurrent = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    //marking an entry missing replaces the counts, clear them so the ui matches the stored value
    override fun setNaTraitsText() {
        resetObservationState()
    }

    override fun type(): String = TYPE

    override fun layoutId(): Int = R.layout.trait_pollinator

    override fun init(act: Activity) {
        composeView = act.findViewById(R.id.compose_view)
    }

    override fun loadLayout() {
        super.loadLayout()
        isRunning.value = false
        refreshLayout(false)
        setupUi()
    }

    private fun resetObservationState() {
        cancelTimer()
        hasSavedCurrent = false
        isRunning.value = false
        isFinished.value = false
        elapsedSeconds.intValue = 0
        counts.clear()
    }

    //reload the saved counts when navigating between repeated measures
    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        traitCategories.value = categoriesFor(currentTrait)
        traitDuration.intValue =
            currentTrait?.duration?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_DURATION_SECONDS
        resetObservationState()
        if (onNew == false) restore(currentObservation?.value)
        //base updates isLocked per rep when frozen, pick it up after the value is restored
        isDataLocked.value = isLocked
    }

    override fun refreshLock() {
        super.refreshLock()
        isDataLocked.value = isLocked
        //never keep counting into an observation that has just been locked
        if (isLocked) isRunning.value = false
    }

    //warn before a toolbar action wipes counts that have been recorded
    override fun getDataLossWarning(): String? =
        if (hasData()) context.getString(R.string.trait_pollinator_data_loss_warning) else null

    //NA and other values that hold no counts are not worth warning about
    private fun hasData(): Boolean =
        elapsedSeconds.intValue > 0 ||
                counts.values.any { it > 0 } ||
                currentObservation?.value?.let {
                    it.isNotEmpty() && !isNotCollectedValue(it)
                } == true

    override fun deleteTraitListener() {
        if (isLocked) return
        collectActivity.removeTrait()
        super.deleteTraitListener()
        //super selects the closest remaining rep, reloading the layout would jump to the last one
        if (collectInputView.isRepeatEnabled()) refreshLayout(false) else loadLayout()
    }

    private fun cancelTimer() {
        saveJob?.cancel()
        saveJob = null
    }

    override fun onExit() {
        isRunning.value = false
    }

    //show the visit total instead of the raw json in the collect input and repeated values toolbar
    override fun decodeValue(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        if (isNotCollectedValue(value)) return value
        return try {
            val json = JSONObject(value)
            val seconds = json.optInt(DURATION_KEY)
            val countsJson = json.optJSONObject(COUNTS_KEY) ?: JSONObject()
            var total = 0
            countsJson.keys().forEach { total += countsJson.optInt(it) }
            context.getString(R.string.trait_pollinator_summary, total, seconds / 60, seconds % 60)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode value: $value", e)
            value
        }
    }

    private fun durationSeconds(): Int = traitDuration.intValue

    private fun categories(): List<BrAPIScaleValidValuesCategories> = traitCategories.value

    private fun key(category: BrAPIScaleValidValuesCategories): String = keyOf(category)

    //NA and any other value that was not collected here leaves the counts empty
    private fun isNotCollectedValue(value: String) = value == "NA" || !JsonUtil.isJsonValid(value)

    private fun restore(value: String?) {
        if (value.isNullOrEmpty() || isNotCollectedValue(value)) return
        try {
            val json = JSONObject(value)
            elapsedSeconds.intValue = json.optInt(DURATION_KEY)
            val countsJson = json.optJSONObject(COUNTS_KEY) ?: JSONObject()
            countsJson.keys().forEach { k -> counts[k] = countsJson.optInt(k) }
            isFinished.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore value: $value", e)
        }
    }

    private fun save() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.IO).launch {
            doSave()
        }
    }

    private fun doSave() {
        if (hasSavedCurrent) return
        hasSavedCurrent = true
        val categories = categories()
        if (categories.isEmpty()) return
        val countsJson = JSONObject()
        categories.forEach { countsJson.put(key(it), counts[key(it)] ?: 0) }
        val json = JSONObject()
        json.put(COUNTS_KEY, countsJson)
        json.put(DURATION_KEY, elapsedSeconds.intValue)
        val value = json.toString()
        val savedLocked = isLocked
        collectActivity.updateObservation(currentTrait, value, null)
        CoroutineScope(Dispatchers.Main).launch {
            collectInputView.text = decodeValue(value)
            afterLoadExists(collectActivity, value)
            isDataLocked.value = savedLocked
        }
    }

    private fun setupUi() {
        composeView?.setContent {
            AppTheme {
                PollinatorView()
            }
        }
    }

    @Composable
    private fun PollinatorView() {

        val total = durationSeconds()
        val categories = categories()

        //tick from the system clock so the countdown stays accurate while running
        LaunchedEffect(isRunning.value) {
            if (isRunning.value) {
                val start = SystemClock.elapsedRealtime() - elapsedSeconds.intValue * 1000L
                while (isRunning.value && elapsedSeconds.intValue < total) {
                    elapsedSeconds.intValue = ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
                    delay(200)
                }
                if (elapsedSeconds.intValue >= total) {
                    elapsedSeconds.intValue = total
                    isRunning.value = false
                    isFinished.value = true
                    save()
                }
            }
        }

        val remaining = (total - elapsedSeconds.intValue).coerceAtLeast(0)

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //the trait defines its own categories, prompt when none exist s.a. an imported trait
            if (categories.isEmpty()) {
                Text(
                    text = context.getString(R.string.trait_pollinator_no_categories),
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            categories.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { category ->
                        CountButton(Modifier.weight(1f), category)
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            //the countdown sits between the controls to leave the space above for categories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(
                    Icons.Default.Stop,
                    context.getString(R.string.stop),
                    enabled = canCollect() && elapsedSeconds.intValue > 0
                ) {
                    isRunning.value = false
                    if (hasSavedCurrent) {
                        isFinished.value = true
                        return@ControlButton
                    }
                    save()
                    isFinished.value = true
                }

                Text(
                    text = "%d:%02d".format(remaining / 60, remaining % 60),
                    style = AppTheme.typography.titleStyle.copy(
                        fontSize = AppTheme.typography.titleSize * 2
                    ),
                    color = AppTheme.colors.text.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    //weight keeps the controls on screen at the largest text sizes
                    modifier = Modifier.weight(1f)
                )

                ControlButton(
                    if (isRunning.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                    context.getString(if (isRunning.value) R.string.pause else R.string.play),
                    enabled = canCollect() && categories.isNotEmpty()
                ) { isRunning.value = !isRunning.value }
            }
        }
    }

    //counting is only possible on a running observation that is not saved, locked or frozen
    private fun canCollect(): Boolean = !isFinished.value && !isDataLocked.value

    @Composable
    private fun CountButton(modifier: Modifier, category: BrAPIScaleValidValuesCategories) {
        val k = key(category)
        Button(
            onClick = { if (isRunning.value && canCollect()) counts[k] = (counts[k] ?: 0) + 1 },
            enabled = isRunning.value && canCollect(),
            //min height so the button grows with the text size preference instead of clipping
            modifier = modifier.defaultMinSize(minHeight = 72.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.primary,
                contentColor = AppTheme.colors.text.button
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = (counts[k] ?: 0).toString(), style = AppTheme.typography.titleStyle)
                Text(
                    text = category.label,
                    style = AppTheme.typography.bodyStyle,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun ControlButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
        val accent = AppTheme.colors.primary
        val iconColor = AppTheme.colors.text.button
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Box(
                modifier = Modifier.size(56.dp).background(if (enabled) accent else accent.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = description,
                    tint = if (enabled) iconColor else iconColor.copy(alpha = 0.38f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
