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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import kotlinx.coroutines.delay
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories
import org.json.JSONObject

class PollinatorTraitLayout : BaseTraitLayout {

    companion object {
        private const val TAG = "PollinatorTraitLayout"
        private const val DEFAULT_DURATION = 300
        internal const val COUNTS_KEY = "counts"
        internal const val DURATION_KEY = "duration_sec"

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

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun setNaTraitsText() {
    }

    override fun type(): String = "pollinator"

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

    //reload the saved counts when navigating between repeated measures
    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        isRunning.value = false
        isFinished.value = false
        elapsedSeconds.intValue = 0
        counts.clear()
        if (onNew == false) restore(currentObservation?.value)
    }

    //warn before the toolbar delete button wipes counts that have been recorded
    override fun getDeleteConfirmationMessage(): String? =
        if (hasData()) context.getString(R.string.trait_pollinator_confirm_delete) else null

    private fun hasData(): Boolean =
        elapsedSeconds.intValue > 0 ||
                counts.values.any { it > 0 } ||
                currentObservation?.value?.isNotEmpty() == true

    override fun deleteTraitListener() {
        if (isLocked) return
        collectActivity.removeTrait()
        super.deleteTraitListener()
        loadLayout()
    }

    override fun onExit() {
        isRunning.value = false
    }

    //show the visit total instead of the raw json in the repeated values toolbar
    override fun decodeValue(value: String?): String {
        if (value.isNullOrEmpty()) return ""
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

    private fun durationSeconds(): Int = currentTrait?.minimum?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_DURATION

    private fun categories(): List<BrAPIScaleValidValuesCategories> = categoriesFor(currentTrait)

    private fun key(category: BrAPIScaleValidValuesCategories): String = keyOf(category)

    private fun restore(value: String?) {
        if (value.isNullOrEmpty()) return
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
        val categories = categories()
        if (categories.isEmpty()) return
        val countsJson = JSONObject()
        categories.forEach { countsJson.put(key(it), counts[key(it)] ?: 0) }
        val json = JSONObject()
        json.put(COUNTS_KEY, countsJson)
        json.put(DURATION_KEY, elapsedSeconds.intValue)
        val value = json.toString()
        collectInputView.text = value
        collectActivity.updateObservation(currentTrait, value, null)
    }

    private fun setupUi() {
        composeView?.setContent {
            PollinatorView(getThemedColor(R.attr.fb_color_primary))
        }
    }

    @Composable
    private fun PollinatorView(accent: Color) {

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
            Text(
                text = "%d:%02d".format(remaining / 60, remaining % 60),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            //the trait defines its own categories, prompt when none exist s.a. an imported trait
            if (categories.isEmpty()) {
                Text(
                    text = context.getString(R.string.trait_pollinator_no_categories),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            categories.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { category ->
                        CountButton(Modifier.weight(1f), category, accent)
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlButton(Icons.Default.Stop, context.getString(R.string.stop), accent, enabled = !isFinished.value && elapsedSeconds.intValue > 0) {
                    isRunning.value = false
                    isFinished.value = true
                    save()
                }

                ControlButton(
                    if (isRunning.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                    context.getString(if (isRunning.value) R.string.pause else R.string.play),
                    accent,
                    enabled = !isFinished.value && categories.isNotEmpty()
                ) { isRunning.value = !isRunning.value }
            }
        }
    }

    @Composable
    private fun CountButton(modifier: Modifier, category: BrAPIScaleValidValuesCategories, accent: Color) {
        val k = key(category)
        Button(
            onClick = { if (isRunning.value && !isFinished.value) counts[k] = (counts[k] ?: 0) + 1 },
            enabled = isRunning.value && !isFinished.value,
            modifier = modifier.height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = (counts[k] ?: 0).toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = category.label, fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun ControlButton(icon: ImageVector, description: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Box(
                modifier = Modifier.size(56.dp).background(if (enabled) accent else accent.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(28.dp))
            }
        }
    }

    private fun getThemedColor(resId: Int): Color {
        val typedArray = context.theme.obtainStyledAttributes(intArrayOf(resId))
        val colorInt = typedArray.getColor(0, 0xFF6200EE.toInt())
        typedArray.recycle()
        return Color(colorInt)
    }
}
