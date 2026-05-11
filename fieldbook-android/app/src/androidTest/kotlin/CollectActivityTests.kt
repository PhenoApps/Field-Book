import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.runner.AndroidJUnit4
import com.fieldbook.tracker.activities.CollectActivity
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectActivityTests : ActivityTests() {

    @get:Rule
    override var activityRule = activityScenarioRule<CollectActivity>()
}