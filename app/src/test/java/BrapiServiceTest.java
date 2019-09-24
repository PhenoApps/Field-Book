import android.content.Context;
import android.view.View;

import androidx.arch.core.util.Function;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiActivity;
import com.fieldbook.tracker.brapi.BrapiStudySummary;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.utilities.Constants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class BrapiServiceTest {

    String brapiBaseUrl = "https://test-server.brapi.org/brapi/v1";
    Boolean checkGetStudiesResult = false;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void checkGetStudies() {

        // Instantiate our brapi service class
        BrAPIService brapiService = new BrAPIService(brapiBaseUrl, null);

        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        // Call our get studies endpoint with the same parsing that our classes use.
        brapiService.getStudies(new Function<List<BrapiStudySummary>, Void>() {
            @Override
            public Void apply(List<BrapiStudySummary> input) {
                // Check that there is atleast one study returned.
                BrapiServiceTest.this.checkGetStudiesResult = input.size() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<String, Void>() {
            @Override
            public Void apply(String input) {
                BrapiServiceTest.this.checkGetStudiesResult = false;
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(BrapiServiceTest.this.checkGetStudiesResult);
        }
        catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetStudyDetails(){

        // Instantiate our brapi service class

        // Call our get study details endpoint with the same parsing that our classes use.

        // What are we checking for?

    }

    @Test
    public void checkGetPlotDetails(){

        // Instantiate our brapi service class

        // Call our get plot details endpoint with the same parsing that our classes use.

        // What are we checking for?

    }

    @Test
    public void checkGetOntology(){

        // Instantiate our brapi service class

        // Call our get ontology endpoint with the same parsing that our classes use.

        // What are we checking for?
    }

    @Test
    public void checkGetTraits(){

        // Instantiate our brapi service class

        // Call our get traits endpoint with the same parsing that our classes use.

        // What are we checking for?
    }
}
