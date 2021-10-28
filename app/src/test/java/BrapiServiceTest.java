import android.graphics.Bitmap;
import android.os.Build;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.OffsetDateTime;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class BrapiServiceTest {

    String brapiBaseUrl = "https://test-server.brapi.org/brapi/v1";
    BrAPIService brAPIService;
    Boolean checkGetStudiesResult = false;
    Boolean checkGetStudyDetailsResult = false;
    Boolean checkGetPlotDetailsResult = false;
    Boolean checkGetTraitsResult = false;
    Boolean checkGetOntologyResult = false;
    List<Observation> putObservationsResponse;
    FieldBookImage postImageMetaDataResponse;
    Bitmap missingImage;
    private String programDbId = "1";
    private String trialDbId = "101";


    @Before
    public void setUp() throws Exception {
        // Instantiate our brapi service class
        this.brAPIService = new BrAPIServiceV1(null);
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap missingImage = Bitmap.createBitmap(100, 100, conf);
    }

    @Test
    public void checkGetStudies() {

        final String brapiToken = "Bearer YYYY";

        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        BrapiPaginationManager pageMan = new BrapiPaginationManager(0, 1000);
        // Call our get studies endpoint with the same parsing that our classes use.
        this.brAPIService.getStudies(this.programDbId, this.trialDbId, pageMan, new Function<List<BrapiStudyDetails>, Void>() {
            @Override
            public Void apply(List<BrapiStudyDetails> input) {
                // Check that there is atleast one study returned.
                BrapiServiceTest.this.checkGetStudiesResult = input.size() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer error) {
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
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetStudyDetails() {

        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String studyDbId = "1001";
        final String brapiToken = "Bearer YYYY";

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
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
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
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetPlotDetails() {

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String studyDbId = "1001";
        final String brapiToken = "Bearer YYYY";

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getPlotDetails(studyDbId, new BrapiObservationLevel().setObservationLevelName("plot"), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that we are getting some results back
                BrapiServiceTest.this.checkGetPlotDetailsResult = input.getNumberOfPlots() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
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
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetOntology() {

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String brapiToken = "Bearer YYYY";

        BrapiPaginationManager pageMan = new BrapiPaginationManager(0, 1000);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getOntology(pageMan, (traitObjects, integer) -> {
                // Check that we are getting some results back
                BrapiServiceTest.this.checkGetOntologyResult = traitObjects.size() > 0;

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
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
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetTraits() {

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);
        final String studyDbId = "1001";
        final String brapiToken = "Bearer YYYY";

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
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
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
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPutObservations() {

        putObservationsResponse = null;
        final CountDownLatch signal = new CountDownLatch(1);
        final String brapiToken = "Bearer YYYY";

        List<Observation> testObservations = new ArrayList<>();
        Observation testObservation = new Observation();
        testObservation.setCollector("Brapi Test");
        testObservation.setDbId("");
        testObservation.setTimestamp(OffsetDateTime.now());
        testObservation.setUnitDbId("1");
        testObservation.setVariableDbId("MO_123:100002");
        testObservation.setValue("5");
        testObservation.setStudyId("1001");
        testObservations.add(testObservation);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.updateObservations(testObservations,  new Function<List<Observation>, Void>() {
                    @Override
                    public Void apply(final List<Observation> observationDbIds) {

                        putObservationsResponse = observationDbIds;
                        signal.countDown();

                        return null;
                    }
                }, new Function<Integer, Void>() {

                    @Override
                    public Void apply(final Integer code) {

                        signal.countDown();

                        return null;
                    }
                }
        );

        // Wait for our callback and evaluate how we did
        try {
            signal.await();

            assertTrue(putObservationsResponse != null);
            assertTrue(putObservationsResponse.size() == 1);
            assertTrue(putObservationsResponse.get(0).getDbId().equals("1"));
            assertTrue(putObservationsResponse.get(0).getVariableDbId().equals("MO_123:100002"));

        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPostImageMetaData() {

        postImageMetaDataResponse = null;
        final CountDownLatch signal = new CountDownLatch(1);
        final String brapiToken = "Bearer YYYY";

        FieldBookImage image = new FieldBookImage("/path/test.jpg", missingImage);
        image.setUnitDbId("1");

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.postImageMetaData(image, new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage response) {

                        postImageMetaDataResponse = response;
                        signal.countDown();

                        return null;
                    }
                }, new Function<Integer, Void>() {

                    @Override
                    public Void apply(final Integer code) {

                        signal.countDown();

                        return null;
                    }
                }
        );

        // Wait for our callback and evaluate how we did
        try {
            signal.await();

            assertTrue(postImageMetaDataResponse != null);

        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }
}
