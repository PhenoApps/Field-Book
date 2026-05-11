import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.runner.AndroidJUnit4
import com.fieldbook.tracker.R
import org.hamcrest.CoreMatchers.anything
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigActivityTests : ActivityTests() {

    @Test
    fun testFieldEditor() {
        onData(anything()).inAdapterView(withId(R.id.myList)).atPosition(0)
            .perform(click())
    }
}