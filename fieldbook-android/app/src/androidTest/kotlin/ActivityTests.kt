import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.runner.AndroidJUnit4
import com.fieldbook.tracker.activities.CollectActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class ActivityTests {

    @get:Rule(order = 1)
    open var activityRule = activityScenarioRule<CollectActivity>()

    @Test
    fun testLifecycles() {
        testLifecycle(Lifecycle.State.STARTED)
        testLifecycle(Lifecycle.State.RESUMED)
        testLifecycle(Lifecycle.State.CREATED)
        testLifecycle(Lifecycle.State.DESTROYED)
        assert(true)
    }

    private fun testLifecycle(lifecycle: Lifecycle.State) {
        val scenario = activityRule.scenario
        scenario.moveToState(lifecycle)
    }
}