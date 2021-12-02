import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1;
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.preferences.GeneralKeys;

import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;
import org.threeten.bp.OffsetDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.StudiesApi;
import io.swagger.client.model.NewObservationUnitDbIdsResponse;
import io.swagger.client.model.NewObservationUnitRequest;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class BrapiServiceTest {

    private static final String BRAPI_URL = "https://test-server.brapi.org";

    private BrAPIService brAPIService;
    private final String programDbId;
    private final String trialDbId;
    private final String brapiVersion;
    private final String brapiToken;
    private final String studyDbId;
    private final String variableDbId;
    private final String ouDbId;

    public BrapiServiceTest(String brapiVersion, String brapiToken, String studyDbId, String programDbId, String trialDbId, String variableDbId, String ouDbId) {
        this.brapiVersion = brapiVersion;
        this.brapiToken = brapiToken;
        this.studyDbId = studyDbId;
        this.programDbId = programDbId;
        this.trialDbId = trialDbId;
        this.variableDbId = variableDbId;
        this.ouDbId = ouDbId;
    }

    @Parameters(name = "BrAPI Version = {0}")
    public static Collection versions() {
        return Arrays.asList(new Object[][] {{"V1", "YYYY", "1001", "1", "101", "MO_123:100002", "1"},
                {"V2", "YYYY", "study1", "program1", "trial1", "variable2", "observation_unit1"}});
    }

    @Before
    public void setUp() {
        Context context = mock(Context.class);
        SharedPreferences settings = mock(SharedPreferences.class);
        when(context.getSharedPreferences(eq("Settings"), anyInt())).thenReturn(settings);
        when(settings.getString(eq(GeneralKeys.BRAPI_BASE_URL), anyString())).thenReturn(BRAPI_URL);
        when(settings.getString(eq(GeneralKeys.BRAPI_VERSION), anyString())).thenReturn(brapiVersion);
        when(settings.getString(eq(GeneralKeys.BRAPI_TOKEN), anyString())).thenReturn(brapiToken);
        when(settings.getString(eq(GeneralKeys.BRAPI_PAGE_SIZE), anyString())).thenReturn("1000");

        // Instantiate our brapi service class
        if(brapiVersion.equals("V2")) {
            this.brAPIService = new BrAPIServiceV2(context);
        } else {
            this.brAPIService = new BrAPIServiceV1(context);
        }
    }

    @Test
    public void checkGetStudies() {
        AtomicBoolean checkGetStudiesResult = new AtomicBoolean(false);

        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        BrapiPaginationManager pageMan = new BrapiPaginationManager(0, 1000);
        // Call our get studies endpoint with the same parsing that our classes use.
        this.brAPIService.getStudies(null, this.trialDbId, pageMan, new Function<List<BrapiStudyDetails>, Void>() {
            @Override
            public Void apply(List<BrapiStudyDetails> input) {
                // Check that there is atleast one study returned.
                checkGetStudiesResult.set(input.size() > 0);

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer error) {
                checkGetStudiesResult.set(false);
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(checkGetStudiesResult.get());
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetStudyDetails() {

        AtomicBoolean checkGetStudyDetailsResult = new AtomicBoolean(false);

        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getStudyDetails(studyDbId, new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that the study db id we passed is what we are getting back
                checkGetStudyDetailsResult.set(input.getStudyDbId().equals(studyDbId));

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
                checkGetStudyDetailsResult.set(false);
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(checkGetStudyDetailsResult.get());
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetPlotDetails() {

        AtomicBoolean checkGetPlotDetailsResult = new AtomicBoolean(false);

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getPlotDetails(studyDbId, new BrapiObservationLevel().setObservationLevelName("plot"), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that we are getting some results back
                checkGetPlotDetailsResult.set(input.getNumberOfPlots() > 0);

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
                checkGetPlotDetailsResult.set(false);
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(checkGetPlotDetailsResult.get());
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetOntology() {

        AtomicBoolean checkGetOntologyResult = new AtomicBoolean(false);

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        BrapiPaginationManager pageMan = new BrapiPaginationManager(0, 1000);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getOntology(pageMan, (traitObjects, integer) -> {
                // Check that we are getting some results back
                checkGetOntologyResult.set(traitObjects.size() > 0);

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
                checkGetOntologyResult.set(false);
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(checkGetOntologyResult.get());
        } catch (InterruptedException e) {
            fail(e.toString());
        }

    }

    @Test
    public void checkGetTraits() {

        AtomicBoolean checkGetTraitsResult = new AtomicBoolean(false);

        // Call our get plot details endpoint with the same parsing that our classes use.
        // Set up our signal to wait for the callback to be called.
        final CountDownLatch signal = new CountDownLatch(1);

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.getTraits(studyDbId, new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                // Check that we are getting some results back
                checkGetTraitsResult.set(input.getTraits().size() > 0);

                // Notify the countdown that we are finish
                signal.countDown();

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
                checkGetTraitsResult.set(false);
                // Notify the countdown that we are finish
                signal.countDown();
                return null;
            }
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();
            assertTrue(checkGetTraitsResult.get());
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPutObservations() {

        List<Observation> putObservationsResponse = new ArrayList<>();
        final CountDownLatch signal = new CountDownLatch(1);

        List<Observation> testObservations = new ArrayList<>();
        Observation testObservation = new Observation();
        testObservation.setCollector("Brapi Test");
//        testObservation.setDbId("");
        testObservation.setTimestamp(OffsetDateTime.now());
        testObservation.setUnitDbId(ouDbId);
        testObservation.setVariableDbId(variableDbId);
        testObservation.setValue("1");
        testObservation.setStudyId(studyDbId);
        testObservations.add(testObservation);

        this.brAPIService.createObservations(testObservations, input -> {
            if(input.size() > 0) {
                testObservations.get(0).setDbId(input.get(0).getDbId());
                testObservations.get(0).setValue("5");

                this.brAPIService.updateObservations(testObservations,  new Function<List<Observation>, Void>() {
                            @Override
                            public Void apply(final List<Observation> observationDbIds) {

                                putObservationsResponse.addAll(observationDbIds);
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
            } else {
                fail();
            }
            return null;
        }, input -> {
            fail();
            return null;
        });

        // Wait for our callback and evaluate how we did
        try {
            signal.await();

            assertEquals(1, putObservationsResponse.size());

            assertEquals(testObservations.get(0).getDbId(), putObservationsResponse.get(0).getDbId());
            assertEquals(variableDbId, putObservationsResponse.get(0).getVariableDbId());

        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPostObservationsChunked() throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        int numObs = 1;
        Map<String, Observation> observationResponses = new ConcurrentHashMap<>();
        final CountDownLatch signal = new CountDownLatch(1);

        List<String> ouIds = new ArrayList<>();
        if(brapiVersion.equals("V1")) {
            ApiClient apiClient = new ApiClient().setBasePath(BRAPI_URL+"/brapi/v1");
            apiClient.setReadTimeout(1000 * 1000);
            StudiesApi ouAPI = new StudiesApi(apiClient);
            List<NewObservationUnitRequest> ouRequests = new ArrayList<>();
            for (int i = 0; i < numObs; i++) {
                NewObservationUnitRequest ou = new NewObservationUnitRequest();
                ou.observationUnitName(UUID.randomUUID().toString());
                ouRequests.add(ou);
            }

            NewObservationUnitDbIdsResponse ouResp = ouAPI.studiesStudyDbIdObservationunitsPut(studyDbId, ouRequests, "Bearer "+brapiToken);
            ouIds = ouResp.getResult().getObservationUnitDbIds();
        } else {
            BrAPIClient brAPIClient = new BrAPIClient(BRAPI_URL+"/brapi/v2", 1000 * 1000);
            brAPIClient.authenticate(t -> brapiToken);
            ObservationUnitsApi ouAPI = new ObservationUnitsApi(brAPIClient);
            List<BrAPIObservationUnit> ouRequests = new ArrayList<>();
            for (int i = 0; i < numObs; i++) {
                BrAPIObservationUnit ou = new BrAPIObservationUnit();
                ou.observationUnitName(UUID.randomUUID().toString());
                ou.studyDbId(studyDbId);
                ouRequests.add(ou);
            }

            ApiResponse<BrAPIObservationUnitListResponse> ouResp = ouAPI.observationunitsPost(ouRequests);
            for(BrAPIObservationUnit ou : ouResp.getBody().getResult().getData()) {
                ouIds.add(ou.getObservationUnitDbId());
            }
        }

        List<Observation> testObservations = new ArrayList<>();
        for(int i = 0; i < numObs; i++) {
            Observation testObservation = new Observation();
            testObservation.setDbId(UUID.randomUUID().toString());
            testObservation.setCollector(UUID.randomUUID().toString());
            testObservation.setTimestamp(OffsetDateTime.now());
            testObservation.setUnitDbId(ouIds.get(i));
            testObservation.setVariableDbId(variableDbId);
            testObservation.setValue(UUID.randomUUID().toString());
            testObservation.setStudyId(studyDbId);

            testObservations.add(testObservation);
        }

        try {
            // Call our get study details endpoint with the same parsing that our classes use.
            this.brAPIService.createObservationsChunked(testObservations, (input, completedChunkNum, chunks, done) -> {
                System.out.println("a chunk was saved: " + completedChunkNum);
                for (Observation o : input) {
                    observationResponses.put(o.getUnitDbId(), o);
                }
                if (done) {
                    System.out.println("got the done signal");
                    signal.countDown();
                }
            }, failure -> {
                System.out.println("Error occurred: " + failure);
                signal.countDown();
                fail("There were failures saving the data via BrAPI");
                return null;
            });
        } catch (Exception e) {
            signal.countDown();
        }

        // Wait for our callback and evaluate how we did
        try {
            signal.await();

            assertFalse("No observations were saved", observationResponses.isEmpty());
            assertEquals("Not all of the observations were saved", numObs, observationResponses.size());

            for(Observation o : testObservations) {
                Observation responseObs = observationResponses.get(o.getUnitDbId());
                assertNotNull(responseObs);
                assertNotNull(responseObs.getDbId());
                assertTrue(responseObs.getDbId().trim().length() > 0);
                assertEquals(o.getVariableDbId(), responseObs.getVariableDbId());
            }

        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPostImageMetaData() {

        final FieldBookImage[] postImageMetaDataResponse = {null};
        final CountDownLatch signal = new CountDownLatch(1);

        FieldBookImage image = new FieldBookImage("/path/test.jpg", Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
        image.setUnitDbId(ouDbId);
        image.setTimestamp(OffsetDateTime.now());

        // Call our get study details endpoint with the same parsing that our classes use.
        this.brAPIService.postImageMetaData(image, new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage response) {

                        postImageMetaDataResponse[0] = response;
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

            assertNotNull("No images were saved", postImageMetaDataResponse[0]);

        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }
}
