import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiListResponse;
import com.fieldbook.tracker.brapi.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.BrapiStudySummary;
import com.fieldbook.tracker.traits.TraitObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BrapiServiceTest {

    String brapiBaseUrl = "https://test-server.brapi.org/brapi/v1";
    BrAPIService brAPIService;
    Boolean checkGetStudiesResult = false;
    Boolean checkGetStudyDetailsResult = false;
    Boolean checkGetPlotDetailsResult = false;
    Boolean checkGetTraitsResult = false;
    Boolean checkGetOntologyResult = false;


    @Before
    public void setUp() throws Exception {
        // Instantiate our brapi service class
        this.brAPIService = new BrAPIService(brapiBaseUrl, null);
    }

    @Test
    public void checkGetStudies() {


        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        // Call our get studies endpoint with the same parsing that our classes use.
        this.brAPIService.getStudies(new Function<List<BrapiStudySummary>, Void>() {
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

        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String studyDbId = "1001";

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getStudyDetails(studyDbId, new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that the study db id we passed is what we are getting back
                BrapiServiceTest.this.checkGetStudyDetailsResult = input.getStudyDbId().equals(studyDbId);

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<String, Void>() {
            @Override
            public Void apply(String input) {
                BrapiServiceTest.this.checkGetStudyDetailsResult = false;
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(BrapiServiceTest.this.checkGetStudyDetailsResult);
        }
        catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetPlotDetails(){

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String studyDbId = "1001";

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getPlotDetails(studyDbId, new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that we are getting some results back
                BrapiServiceTest.this.checkGetPlotDetailsResult = input.getNumberOfPlots() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<String, Void>() {
            @Override
            public Void apply(String input) {
                BrapiServiceTest.this.checkGetPlotDetailsResult = false;
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(BrapiServiceTest.this.checkGetPlotDetailsResult);
        }
        catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetOntology(){

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getOntology(null, null, new Function<BrapiListResponse<TraitObject>, Void>() {
            @Override
            public Void apply(BrapiListResponse<TraitObject> input) {
                // Check that we are getting some results back
                BrapiServiceTest.this.checkGetOntologyResult = input.getData().size() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<String, Void>() {
            @Override
            public Void apply(String input) {
                BrapiServiceTest.this.checkGetOntologyResult = false;
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(BrapiServiceTest.this.checkGetOntologyResult);
        }
        catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetTraits(){

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String studyDbId = "1001";

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getTraits(studyDbId, new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that we are getting some results back
                BrapiServiceTest.this.checkGetTraitsResult = input.getTraits().size() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<String, Void>() {
            @Override
            public Void apply(String input) {
                BrapiServiceTest.this.checkGetTraitsResult = false;
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(BrapiServiceTest.this.checkGetTraitsResult);
        }
        catch (InterruptedException e) {
            fail(e.toString());
        }

    }
}
