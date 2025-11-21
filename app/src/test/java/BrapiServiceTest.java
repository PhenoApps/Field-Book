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
import com.fieldbook.tracker.preferences.PreferenceKeys;

import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.StudiesApi;
import io.swagger.client.model.NewObservationUnitDbIdsResponse;
import io.swagger.client.model.NewObservationUnitRequest;
import io.swagger.client.model.ObservationsResponse;

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
        when(settings.getString(eq(PreferenceKeys.BRAPI_BASE_URL), anyString())).thenReturn(BRAPI_URL);
        when(settings.getString(eq(PreferenceKeys.BRAPI_VERSION), anyString())).thenReturn(brapiVersion);
        when(settings.getString(eq(PreferenceKeys.BRAPI_TOKEN), anyString())).thenReturn(brapiToken);
        when(settings.getString(eq(PreferenceKeys.BRAPI_PAGE_SIZE), anyString())).thenReturn("1000");

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
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertTrue(checkGetStudiesResult.get());
            } else {
                fail("Async action timed out");
            }
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
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertTrue(checkGetStudyDetailsResult.get());
            } else {
                fail("Async action timed out");
            }
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
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertTrue(checkGetPlotDetailsResult.get());
            } else {
                fail("Async action timed out");
            }
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
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertTrue(checkGetOntologyResult.get());
            } else {
                fail("Async action timed out");
            }
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
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertTrue(checkGetTraitsResult.get());
            } else {
                fail("Async action timed out");
            }
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
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertEquals(1, putObservationsResponse.size());

                assertEquals(testObservations.get(0).getDbId(), putObservationsResponse.get(0).getDbId());
                assertEquals(variableDbId, putObservationsResponse.get(0).getVariableDbId());
            } else {
                fail("Async action timed out");
            }
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPostObservationsChunkedMultipleChunks() throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        postObservationsChunked(1000);
    }

    @Test
    public void checkPostObservationsChunkedSingleChunk() throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        postObservationsChunked(1);
    }

    private void postObservationsChunked(int numObs) throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        Map<String, Observation> observationResponses = new ConcurrentHashMap<>();
        final CountDownLatch signal = new CountDownLatch(1);

        List<String> ouIds = generateObservationUnits(numObs);

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
            int testChunkSize = 500;
            // Call our get study details endpoint with the same parsing that our classes use.
            this.brAPIService.createObservationsChunked(testChunkSize, testObservations, (input, completedChunkNum, chunks, done) -> {
                for (Observation o : input) {
                    observationResponses.put(o.getUnitDbId(), o);
                }
                if (done) {
                    signal.countDown();
                }
            }, (failure, chunk, done) -> {
                signal.countDown();
                fail("There were failures saving the data via BrAPI");
            });
        } catch (Exception e) {
            signal.countDown();
        }

        // Wait for our callback and evaluate how we did
        try {
            if(signal.await(5, TimeUnit.MINUTES)) {
                assertFalse("No observations were saved", observationResponses.isEmpty());
                assertEquals("Not all of the observations were saved", numObs, observationResponses.size());

                for(Observation o : testObservations) {
                    Observation responseObs = observationResponses.get(o.getUnitDbId());
                    assertNotNull(responseObs);
                    assertNotNull(responseObs.getDbId());
                    assertTrue(responseObs.getDbId().trim().length() > 0);
                    assertEquals(o.getVariableDbId(), responseObs.getVariableDbId());
                }
            } else {
                fail("Async action timed out");
            }
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    @Test
    public void checkPutObservationsChunkedMultipleChunks() throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        putObservationsChunked(1000);
    }

    @Test
    public void checkPutObservationsChunkedSingleChunk() throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        putObservationsChunked(1);
    }

    private void putObservationsChunked(int numObs) throws ApiException, org.brapi.client.v2.model.exceptions.ApiException {
        Map<String, Observation> createdObservations = new ConcurrentHashMap<>();
        final CountDownLatch createSignal = new CountDownLatch(1);
        final CountDownLatch updateSignal = new CountDownLatch(1);

        List<String> ouIds = generateObservationUnits(numObs);

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

        int testChunkSize = 500;

        try {
            // Call our get study details endpoint with the same parsing that our classes use.
            this.brAPIService.createObservationsChunked(testChunkSize, testObservations, (input, completedChunkNum, chunks, done) -> {
                for (Observation o : input) {
                    createdObservations.put(o.getUnitDbId(), o);
                }
                if (done) {
                    createSignal.countDown();
                }
            }, (failure, chunk, done) -> {
                createSignal.countDown();
                fail("There were failures saving the data via BrAPI");
            });
        } catch (Exception e) {
            createSignal.countDown();
        }

        try {
            if(createSignal.await(5, TimeUnit.MINUTES)) {
                for(Observation o : testObservations) {
                    if(createdObservations.containsKey(o.getUnitDbId())) {
                        o.setDbId(createdObservations.get(o.getUnitDbId()).getDbId());
                        o.setValue(o.getValue() + "-updated");

                        createdObservations.put(o.getUnitDbId(), o);
                    } else {
                        fail("observation was not created");
                    }
                }

                try {
                    // Call our get study details endpoint with the same parsing that our classes use.
                    this.brAPIService.updateObservationsChunked(testChunkSize, new ArrayList<>(createdObservations.values()), (input, completedChunkNum, chunks, done) -> {
                        if (done) {
                            updateSignal.countDown();
                        }
                    }, (code, chunk, done) -> {
                        updateSignal.countDown();
                        fail("There were failures saving the data via BrAPI");
                    });
                } catch (Exception e) {
                    updateSignal.countDown();
                }
            }
        } catch (InterruptedException e) {
            fail("Async action timed out");
        }

        // Wait for our callback and evaluate how we did
        try {
            if(updateSignal.await(5, TimeUnit.MINUTES)) {
                Map<String, Observation> updatedObservations = new HashMap<>();
                if(brapiVersion.equals("V1")) {
                    ApiClient apiClient = new ApiClient().setBasePath(BRAPI_URL+"/brapi/v1");
                    apiClient.setReadTimeout(1000 * 1000);
                    StudiesApi obsAPI = new StudiesApi(apiClient);
                    ObservationsResponse observationsResponse = obsAPI.studiesStudyDbIdObservationsGet(studyDbId, null, 0, 100000, brapiToken);
                    for(io.swagger.client.model.Observation o : observationsResponse.getResult().getData()) {
                        if(createdObservations.containsKey(o.getObservationUnitDbId())) {
                            Observation obs = new Observation();
                            obs.setDbId(o.getObservationDbId());
                            obs.setUnitDbId(o.getObservationUnitDbId());
                            obs.setVariableDbId(o.getObservationVariableDbId());
                            obs.setValue(o.getValue());

                            updatedObservations.put(obs.getUnitDbId(), obs);
                        }
                    }
                } else {
                    BrAPIClient brAPIClient = new BrAPIClient(BRAPI_URL+"/brapi/v2", 1000 * 1000);
                    brAPIClient.authenticate(t -> brapiToken);
                    ObservationsApi obsAPI = new ObservationsApi(brAPIClient);
                    ObservationQueryParams observationQueryParams = new ObservationQueryParams().studyDbId(studyDbId);
                    observationQueryParams.pageSize(100000);
                    ApiResponse<BrAPIObservationListResponse> ouResp = obsAPI.observationsGet(observationQueryParams);
                    for(BrAPIObservation o : ouResp.getBody().getResult().getData()) {
                        if(createdObservations.containsKey(o.getObservationUnitDbId())) {
                            Observation obs = new Observation();
                            obs.setDbId(o.getObservationDbId());
                            obs.setUnitDbId(o.getObservationUnitDbId());
                            obs.setVariableDbId(o.getObservationVariableDbId());
                            obs.setValue(o.getValue());

                            updatedObservations.put(obs.getUnitDbId(), obs);
                        }
                    }
                }

                assertFalse("No observations were saved", updatedObservations.isEmpty());
                assertEquals("Not all of the observations were saved", numObs, updatedObservations.size());

                for(Observation o : testObservations) {
                    Observation responseObs = updatedObservations.get(o.getUnitDbId());
                    assertNotNull(responseObs);
                    assertNotNull(responseObs.getDbId());
                    assertTrue(responseObs.getDbId().trim().length() > 0);
                    assertEquals(o.getVariableDbId(), responseObs.getVariableDbId());
                    assertEquals("Value was not updated", o.getValue(), responseObs.getValue());
                }
            } else {
                fail("Async action timed out");
            }
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    private List<String> generateObservationUnits(int numObs) throws org.brapi.client.v2.model.exceptions.ApiException, ApiException {
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

        return ouIds;
    }

    @Test
    public void checkPostImageMetaData() {

        Context context = mock(Context.class);

        final FieldBookImage[] postImageMetaDataResponse = {null};
        final CountDownLatch signal = new CountDownLatch(1);

        FieldBookImage image = new FieldBookImage(context, "/path/test.jpg","" ,Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
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
            if(signal.await(1, TimeUnit.MINUTES)) {
                assertNotNull("No images were saved", postImageMetaDataResponse[0]);
            } else {
                fail("Async action timed out");
            }

        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }
}
