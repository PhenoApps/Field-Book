package com.fieldbook.tracker.brapi.service;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.model.BrapiProgram;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.BrapiTrial;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ImagesApi;
import io.swagger.client.api.ObservationVariablesApi;
import io.swagger.client.api.ObservationsApi;
import io.swagger.client.api.PhenotypesApi;
import io.swagger.client.api.ProgramsApi;
import io.swagger.client.api.StudiesApi;

import io.swagger.client.api.TrialsApi;
import io.swagger.client.model.Image;
import io.swagger.client.model.ImageResponse;
import io.swagger.client.model.Metadata;
import io.swagger.client.model.NewImageRequest;
import io.swagger.client.model.NewObservationDbIdsObservations;
import io.swagger.client.model.NewObservationDbIdsResponse;
import io.swagger.client.model.NewObservationsRequest;
import io.swagger.client.model.NewObservationsRequestObservations;
import io.swagger.client.model.ObservationUnit;
import io.swagger.client.model.ObservationUnitsResponse1;
import io.swagger.client.model.ObservationVariable;
import io.swagger.client.model.ObservationVariablesResponse;
import io.swagger.client.model.Program;
import io.swagger.client.model.ProgramsResponse;
import io.swagger.client.model.StudiesResponse;
import io.swagger.client.model.Study;
import io.swagger.client.model.StudyObservationVariablesResponse;
import io.swagger.client.model.StudyResponse;
import io.swagger.client.model.StudySummary;
import io.swagger.client.model.TrialSummary;
import io.swagger.client.model.TrialsResponse;

public class BrAPIServiceV1 implements BrAPIService {
    private final Context context;
    private final ImagesApi imagesApi;
    private final StudiesApi studiesApi;
    private final ProgramsApi programsApi;
    private final TrialsApi trialsApi;
    private final PhenotypesApi phenotypesApi;
    private final ObservationsApi observationsApi;
    private final ObservationVariablesApi traitsApi;

    public BrAPIServiceV1(Context context) {
        this.context = context;
        ApiClient apiClient = new ApiClient().setBasePath(BrAPIService.getBrapiUrl(context));
        apiClient.setReadTimeout(getTimeoutValue(context) * 1000);

        this.imagesApi = new ImagesApi(apiClient);
        this.studiesApi = new StudiesApi(apiClient);
        this.programsApi = new ProgramsApi(apiClient);
        this.trialsApi = new TrialsApi(apiClient);
        this.traitsApi = new ObservationVariablesApi(apiClient);
        this.phenotypesApi = new PhenotypesApi(apiClient);
        this.observationsApi = new ObservationsApi(apiClient);
    }

    private Integer getTimeoutValue(Context context) {
        String timeoutString = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_TIMEOUT, "120");

        int timeout = 120;

        try {
            if (timeoutString != null) {
                timeout = Integer.parseInt(timeoutString);
            }
        } catch (NumberFormatException nfe) {
            String message = nfe.getLocalizedMessage();
            if (message != null) {
                Log.d("FieldBookError", nfe.getLocalizedMessage());
            } else {
                Log.d("FieldBookError", "Timeout Preference number format error.");
            }
            nfe.printStackTrace();
        }

        return timeout;
    }

    @Override
    public void authorizeClient() {

    }

    private String getBrapiToken() {
        SharedPreferences preferences = context.getSharedPreferences("Settings", 0);
        return "Bearer " + preferences.getString(GeneralKeys.BRAPI_TOKEN, "");
    }

    public void postImageMetaData(FieldBookImage image,
                                  final Function<FieldBookImage, Void> function,
                                  final Function<Integer, Void> failFunction) {

        try {
            BrapiV1ApiCallBack<ImageResponse> callback = new BrapiV1ApiCallBack<ImageResponse>() {
                @Override
                public void onSuccess(ImageResponse imageResponse, int i, Map<String, List<String>> map) {
                    final Image response = imageResponse.getResult();
                    function.apply(mapToImage(response));
                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(e.getCode());
                }
            };

            NewImageRequest request = mapImage(image);
            imagesApi.imagesPostAsync(request, getBrapiToken(), callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    private NewImageRequest mapImage(FieldBookImage image) {
        NewImageRequest request = new NewImageRequest();
        request.setAdditionalInfo(image.getAdditionalInfo());
        request.setCopyright(image.getCopyright());
        request.setDescription(image.getDescription());
        request.setDescriptiveOntologyTerms(image.getDescriptiveOntologyTerms());
        request.setImageFileName(image.getFileName());
        request.setImageFileSize((int) image.getFileSize());
        request.setImageHeight(image.getHeight());
        request.setImageLocation(image.getLocation());
        request.setImageName(image.getImageName());
        request.setImageTimeStamp(image.getTimestamp());
        request.setImageWidth(image.getWidth());
        request.setMimeType(image.getMimeType());
        request.setObservationUnitDbId(image.getUnitDbId());
        return request;
    }

    private FieldBookImage mapToImage(Image image) {
        FieldBookImage request = new FieldBookImage(image);
        request.setAdditionalInfo(image.getAdditionalInfo());
        request.setDescription(image.getDescription());
        request.setDescriptiveOntologyTerms(image.getDescriptiveOntologyTerms());
        request.setFileName(image.getImageFileName());
        request.setFileSize((int) image.getImageFileSize());
        request.setHeight(image.getImageHeight());
        request.setLocation(image.getImageLocation());
        request.setImageName(image.getImageName());
        request.setTimestamp(image.getImageTimeStamp());
        request.setWidth(image.getImageWidth());
        request.setMimeType(image.getMimeType());
        request.setUnitDbId(image.getObservationUnitDbId());
        request.setDbId(image.getImageDbId());
        return request;
    }

    public void putImageContent(FieldBookImage image,
                                final Function<FieldBookImage, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV1ApiCallBack<ImageResponse> callback = new BrapiV1ApiCallBack<ImageResponse>() {
                @Override
                public void onSuccess(ImageResponse imageResponse, int i, Map<String, List<String>> map) {

                    final Image response = imageResponse.getResult();
                    function.apply(mapToImage(response));

                }

                @Override
                public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                    failFunction.apply(e.getCode());
                }
            };

            imagesApi.imagesImageDbIdImagecontentPutAsync(image.getDbId(), image.getImageData(), getBrapiToken(), callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    public void putImage(FieldBookImage image,
                         final Function<FieldBookImage, Void> function,
                         final Function<Integer, Void> failFunction) {
        try {

            BrapiV1ApiCallBack<ImageResponse> callback = new BrapiV1ApiCallBack<ImageResponse>() {
                @Override
                public void onSuccess(ImageResponse imageResponse, int i, Map<String, List<String>> map) {
                    function.apply(mapToImage(imageResponse.getResult()));

                }

                @Override
                public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                    failFunction.apply(e.getCode());
                }
            };

            NewImageRequest request = mapImage(image);
            imagesApi.imagesImageDbIdPutAsync(image.getDbId(), request, getBrapiToken(), callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    private void updatePageInfo(BrapiPaginationManager paginationManager, Metadata metadata){
        if(paginationManager.getContext() != null) { //null check for JUnits
            ((Activity) paginationManager.getContext())
                    .runOnUiThread(() -> paginationManager.updatePageInfo(metadata.getPagination().getTotalPages()));
        }
    }

    public void getPrograms(final  BrapiPaginationManager paginationManager,
                            final Function<List<BrapiProgram>, Void> function,
                            final Function<Integer, Void> failFunction) {
        Integer initPage = paginationManager.getPage();
        try {
           BrapiV1ApiCallBack<ProgramsResponse> callback = new BrapiV1ApiCallBack<ProgramsResponse>() {
               @Override
               public void onSuccess(ProgramsResponse programsResponse, int i, Map<String, List<String>> map) {
                   // Cancel processing if the page that was processed is not the page
                   // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                   if (initPage.equals(paginationManager.getPage())) {
                       updatePageInfo(paginationManager, programsResponse.getMetadata());
                       List<Program> programList = programsResponse.getResult().getData();
                       function.apply(mapPrograms(programList));
                   }
               }

               @Override
               public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                   failFunction.apply(error.getCode());
               }
           };
           programsApi.programsGetAsync(null, null, null,
                   paginationManager.getPage(), paginationManager.getPageSize(), getBrapiToken(), callback);
       } catch (ApiException e) {
           e.printStackTrace();
       }
    }

    private List<BrapiProgram> mapPrograms(List<Program> programList) {
        List<BrapiProgram> brapiPrograms = new ArrayList<>();
        if (programList != null) {
            for (Program program : programList) {
                BrapiProgram brapiProgram = new BrapiProgram();
                String name = program.getName();
                String programName = program.getProgramName();
                if (programName != null && !programName.isEmpty()) {
                    brapiProgram.setProgramName(programName);
                } else {
                    brapiProgram.setProgramName(name);
                }
                brapiProgram.setProgramDbId(program.getProgramDbId());
                brapiPrograms.add(brapiProgram);
            }
        }
        return brapiPrograms;
    }

    public void getTrials(String programDbId, BrapiPaginationManager paginationManager,
                          final Function<List<BrapiTrial>, Void> function,
                          final Function<Integer, Void> failFunction) {
        Integer initPage = paginationManager.getPage();
        try {
            BrapiV1ApiCallBack<TrialsResponse> callback = new BrapiV1ApiCallBack<TrialsResponse>() {
                @Override
                public void onSuccess(TrialsResponse trialsResponse, int i, Map<String, List<String>> map) {
                    // Cancel processing if the page that was processed is not the page
                    // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                    if (initPage.equals(paginationManager.getPage())) {
                        updatePageInfo(paginationManager, trialsResponse.getMetadata());
                        List<TrialSummary> trialList = trialsResponse.getResult().getData();
                        function.apply(mapTrials(trialList));
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                }
            };
            trialsApi.trialsGetAsync(null, programDbId, null, null, null, null,
                    paginationManager.getPage(), paginationManager.getPageSize(), getBrapiToken(), callback);
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    private List<BrapiTrial> mapTrials(List<TrialSummary> trialList) {
        List<BrapiTrial> brapiTrials = new ArrayList<>();
        if (trialList != null) {
            for (TrialSummary trial : trialList) {
                BrapiTrial brapiTrial = new BrapiTrial();
                String name = trial.getTrialName();
                brapiTrial.setTrialName(name);
                brapiTrial.setTrialDbId(trial.getTrialDbId());
                brapiTrials.add(brapiTrial);
            }
        }
        return brapiTrials;
    }

    public void getStudies(String programDbId, String trialDbId, BrapiPaginationManager paginationManager,
                           final Function<List<BrapiStudyDetails>, Void> function,
                           final Function<Integer, Void> failFunction) {
        Integer initPage = paginationManager.getPage();
        try {
            BrapiV1ApiCallBack<StudiesResponse> callback = new BrapiV1ApiCallBack<StudiesResponse>() {
                @Override
                public void onSuccess(StudiesResponse studiesResponse, int i, Map<String, List<String>> map) {
                    // Cancel processing if the page that was processed is not the page
                    // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                    if (initPage.equals(paginationManager.getPage())) {
                        updatePageInfo(paginationManager, studiesResponse.getMetadata());
                        final List<BrapiStudyDetails> studies = new ArrayList<>();
                        final List<StudySummary> studySummaryList = studiesResponse.getResult().getData();
                        for (StudySummary studySummary : studySummaryList) {
                            studies.add(mapStudy(studySummary));
                        }
                        function.apply(studies);
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                }
            };

            studiesApi.studiesGetAsync(
                    null, null, null, programDbId,
                    null, null, trialDbId, null, null,
                    null, true, null, null,
                    paginationManager.getPage(), paginationManager.getPageSize(), getBrapiToken(), callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    private BrapiStudyDetails mapStudy(StudySummary studySummary) {
        BrapiStudyDetails study = new BrapiStudyDetails();
        study.setStudyDbId(studySummary.getStudyDbId());
        study.setStudyName(studySummary.getStudyName());
        return study;
    }

    public void getStudyDetails(final String studyDbId,
                                final Function<BrapiStudyDetails, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV1ApiCallBack<StudyResponse> callback = new BrapiV1ApiCallBack<StudyResponse>() {
                @Override
                public void onSuccess(StudyResponse studyResponse, int i, Map<String, List<String>> map) {

                    final BrapiStudyDetails study = mapStudy(studyResponse.getResult());
                    function.apply(study);

                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                }
            };

            studiesApi.studiesStudyDbIdGetAsync(
                    studyDbId, getBrapiToken(), callback);

        } catch (ApiException e) {
            failFunction.apply(e.getCode());
            e.printStackTrace();
        }
    }

    private BrapiStudyDetails mapStudy(Study study) {
        BrapiStudyDetails studyDetails = new BrapiStudyDetails();
        studyDetails.setStudyDbId(study.getStudyDbId());
        studyDetails.setStudyName(study.getStudyName());
        studyDetails.setCommonCropName(study.getCommonCropName());
        studyDetails.setStudyDescription(study.getStudyDescription());
        studyDetails.setStudyLocation(study.getLocation().getLocationName());
        return studyDetails;
    }

    public void getPlotDetails(final String studyDbId,
                               final Function<BrapiStudyDetails, Void> function,
                               final Function<Integer, Void> failFunction) {
        try {
            final Integer[] recursiveCounter = {0};
            final Integer pageSize = 1000;
            final BrapiStudyDetails study = new BrapiStudyDetails();
            study.setValues(new ArrayList<>());

            BrapiV1ApiCallBack<ObservationUnitsResponse1> callback = new BrapiV1ApiCallBack<ObservationUnitsResponse1>() {
                @Override
                public void onSuccess(ObservationUnitsResponse1 response, int i, Map<String, List<String>> map) {

                    //bugfix for index out of bounds occurring when no data is in response
                    if (response.getResult().getData().size() > 0) {

                        final BrapiStudyDetails study = new BrapiStudyDetails();
                        study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                        study.setAttributes(mapAttributes(response.getResult().getData().get(0)));
                        study.setValues(mapAttributeValues(study.getAttributes(), response.getResult().getData()));

                        function.apply(study);

                        int page = response.getMetadata().getPagination().getCurrentPage();
                        if(page == 0){
                            //one time code
                            //sometimes getData() size is 0 which causes an index out of bounds exception
                            //error can be reproduced by trying to import Study 10 from the default brapi server
                            try {
                                study.setAttributes(mapAttributes(response.getResult().getData().get(0)));
                                study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                            } catch (IndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                        }

                        recursiveCounter[0] = recursiveCounter[0] + 1;

                        // Stop after 50 iterations (for safety)
                        // Stop if the current page is the last page according to the server
                        // Stop if there are no more contents
                        if((recursiveCounter[0] > 50)
                                || (page >= (response.getMetadata().getPagination().getTotalPages() - 1))
                                || (response.getResult().getData().size() == 0)){
                            // Stop recursive loop
                            function.apply(study);
                        }else {
                            try {
                                studiesApi.studiesStudyDbIdObservationunitsGetAsync(
                                        studyDbId, "plot", recursiveCounter[0], pageSize,
                                        getBrapiToken(), this);
                            } catch (ApiException e) {
                                failFunction.apply(e.getCode());
                                e.printStackTrace();
                            }
                        }
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                }

            };

            studiesApi.studiesStudyDbIdObservationunitsGetAsync(
                    studyDbId, "plot", 0, pageSize,
                    getBrapiToken(), callback);

        } catch (ApiException e) {
            failFunction.apply(e.getCode());
            e.printStackTrace();
        }
    }

    private List<String> mapAttributes(ObservationUnit unit) {
        List<String> attributes = new ArrayList<>();
        if (checkField(unit.getX(), unit.getPositionCoordinateX()))
            attributes.add("Row");
        if (checkField(unit.getY(), unit.getPositionCoordinateY()))
            attributes.add("Column");
        if (checkField(unit.getBlockNumber()))
            attributes.add("Block");
        if (checkField(unit.getReplicate()))
            attributes.add("Replicate");
        if (checkField(unit.getEntryNumber(), unit.getEntryType()))
            attributes.add("Entry");
        if (checkField(unit.getPlotNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId()))
            attributes.add("Plot");
        if (checkField(unit.getPlantNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId()))
            attributes.add("Plant");
        if (checkField(unit.getGermplasmName(), unit.getGermplasmDbId()))
            attributes.add("Germplasm");
        if (checkField(unit.getPedigree()))
            attributes.add("Pedigree");

        // We always add the observationUnitDbId and observationName
        attributes.add("observationUnitDbId");
        attributes.add("observationUnitName");

        return attributes;

    }

    private List<List<String>> mapAttributeValues(List<String> attributes, List<ObservationUnit> data) {
        List<List<String>> attributesTable = new ArrayList<>();
        for (ObservationUnit unit : data) {
            List<String> dataRow = new ArrayList<>();
            for (String attribute : attributes) {
                if (attribute.equalsIgnoreCase("Row"))
                    addAttributeDataItem(dataRow, unit.getX(), unit.getPositionCoordinateX());
                else if (attribute.equalsIgnoreCase("Column"))
                    addAttributeDataItem(dataRow, unit.getY(), unit.getPositionCoordinateY());
                else if (attribute.equalsIgnoreCase("Block"))
                    addAttributeDataItem(dataRow, unit.getBlockNumber());
                else if (attribute.equalsIgnoreCase("Replicate"))
                    addAttributeDataItem(dataRow, unit.getReplicate());
                else if (attribute.equalsIgnoreCase("Entry"))
                    addAttributeDataItem(dataRow, unit.getEntryNumber(), unit.getEntryType());
                else if (attribute.equalsIgnoreCase("Plot"))
                    addAttributeDataItem(dataRow, unit.getPlotNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId());
                else if (attribute.equalsIgnoreCase("Plant"))
                    addAttributeDataItem(dataRow, unit.getPlantNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId());
                else if (attribute.equalsIgnoreCase("Germplasm"))
                    addAttributeDataItem(dataRow, unit.getGermplasmName(), unit.getGermplasmDbId());
                else if (checkField(unit.getPedigree()) && attribute.equalsIgnoreCase("Pedigree"))
                    addAttributeDataItem(dataRow, unit.getPedigree());

            }

            // ObservationUnitDbId and observationUnitName will always go in the attributes
            addAttributeDataItem(dataRow, unit.getObservationUnitDbId());
            addAttributeDataItem(dataRow, unit.getObservationUnitName());

            attributesTable.add(dataRow);
        }

        return attributesTable;
    }

    private void addAttributeDataItem(List<String> dataRow, String... values) {
        String goodValue = getPrioritizedValue(values);
        if (goodValue != null) {
            dataRow.add(goodValue);
        }
    }

    private boolean checkField(String... values) {
        return getPrioritizedValue(values) != null;
    }

    public void getOntology(BrapiPaginationManager paginationManager,
                            final BiFunction<List<TraitObject>, Integer, Void> function,
                            final Function<Integer, Void> failFunction) {
        Integer initPage = paginationManager.getPage();
        try {
            BrapiV1ApiCallBack<ObservationVariablesResponse> callback = new BrapiV1ApiCallBack<ObservationVariablesResponse>() {
                @Override
                public void onSuccess(ObservationVariablesResponse response, int i, Map<String, List<String>> map) {
                    // Cancel processing if the page that was processed is not the page
                    // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                    if (initPage.equals(paginationManager.getPage())) {
                        updatePageInfo(paginationManager, response.getMetadata());
                        // Result contains a list of observation variables
                        List<ObservationVariable> brapiTraitList = response.getResult().getData();
                        final Pair<List<TraitObject>, Integer> traitsResult = mapTraits(brapiTraitList);

                        function.apply(traitsResult.first, traitsResult.second);
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                }

            };

            traitsApi.variablesGetAsync(paginationManager.getPage(), paginationManager.getPageSize(),
                    getBrapiToken(), null,null, callback);

        } catch (ApiException e) {
            Log.e("error-go", e.toString());
            failFunction.apply(e.getCode());
        }
    }

    private Observation mapToObservation(NewObservationDbIdsObservations obs){
        Observation newObservation = new Observation();
        newObservation.setDbId(obs.getObservationDbId());
        newObservation.setUnitDbId(obs.getObservationUnitDbId());
        newObservation.setVariableDbId(obs.getObservationVariableDbId());
        return newObservation;
    }

    public void createObservations(List<Observation> observations,
                                   final Function<List<Observation>, Void> function,
                                   final Function<Integer, Void> failFunction) {
        updateObservations(observations, function, failFunction);
    }

    // will only ever have one study in current architecture
    public void updateObservations(List<Observation> observations,
                                final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction) {

        // group observations by studyid
        HashMap<String, List<Observation>> studyObservations = new HashMap<>();
        for (Observation observation : observations) {
            List<Observation> observationList = studyObservations.get(observation.getStudyId());
            if (observationList == null) {
                observationList = new ArrayList<>();
            }

            observationList.add(observation);
            studyObservations.put(observation.getStudyId(), observationList);
        }

        try {

            BrapiV1ApiCallBack<NewObservationDbIdsResponse> callback = new BrapiV1ApiCallBack<NewObservationDbIdsResponse>() {
                @Override
                public void onSuccess(NewObservationDbIdsResponse observationsResponse, int i, Map<String, List<String>> map) {
                    List<Observation> newObservations = new ArrayList<>();
                    for(NewObservationDbIdsObservations obs: observationsResponse.getResult().getObservations()){
                        newObservations.add(mapToObservation(obs));
                    }
                    function.apply(newObservations);

                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(e.getCode());
                }
            };

            Set<String> studies = studyObservations.keySet();

            for (String study : studies) {
                //get list of observations for study
                List<Observation> studyObs = studyObservations.get(study);

                List<NewObservationsRequestObservations> request_observations = new ArrayList<>();

                for (Observation obs : studyObs) {
                    NewObservationsRequestObservations o = new NewObservationsRequestObservations();
                    o.setCollector(obs.getCollector().trim());
                    o.setObservationDbId(obs.getDbId());
                    o.setObservationTimeStamp(obs.getTimestamp());
                    o.setObservationUnitDbId(obs.getUnitDbId());
                    o.setObservationVariableDbId(obs.getVariableDbId());
                    o.setValue(obs.getValue());

                    request_observations.add(o);
                }

                NewObservationsRequest request = new NewObservationsRequest();
                request.setObservations(request_observations);

                observationsApi.studiesStudyDbIdObservationsPutAsync(study, request, getBrapiToken(), callback);
            }

        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    private String getPrioritizedValue(String... values) {
        String returnValue = null;
        for (String val : values) {
            if (val != null && !val.isEmpty()) {
                returnValue = val;
                break;
            }
        }
        return returnValue;
    }

    public void getTraits(String studyDbId,
                          final Function<BrapiStudyDetails, Void> function,
                          final Function<Integer, Void> failFunction) {
        try {
            final Integer[] recursiveCounter = {0};
            final Integer pageSize = 1000;
            final BrapiStudyDetails study = new BrapiStudyDetails();
            study.setTraits(new ArrayList<>());

            BrapiV1ApiCallBack<StudyObservationVariablesResponse> callback = new BrapiV1ApiCallBack<StudyObservationVariablesResponse>() {
                @Override
                public void onSuccess(StudyObservationVariablesResponse response, int i, Map<String, List<String>> map) {
                    //every time
                    study.getTraits().addAll(mapTraits(response.getResult().getData()).first);
                    recursiveCounter[0] = recursiveCounter[0] + 1;

                    int page = response.getMetadata().getPagination().getCurrentPage();

                    // Stop after 50 iterations (for safety)
                    // Stop if the current page is the last page according to the server
                    // Stop if there are no more contents
                    if((recursiveCounter[0] > 50)
                            || (page >= (response.getMetadata().getPagination().getTotalPages() - 1))
                            || (response.getResult().getData().size() == 0)){
                        // Stop recursive loop
                        function.apply(study);
                    }else {
                        try {
                            studiesApi.studiesStudyDbIdObservationvariablesGetAsync(
                                    studyDbId, recursiveCounter[0], pageSize,
                                    getBrapiToken(), this);
                        } catch (ApiException e) {
                            failFunction.apply(e.getCode());
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                }
            };
            studiesApi.studiesStudyDbIdObservationvariablesGetAsync(
                    studyDbId, 0, pageSize,
                    getBrapiToken(), callback);
        } catch (ApiException e) {
            failFunction.apply(e.getCode());
            e.printStackTrace();
        }
    }

    private Pair<List<TraitObject>, Integer> mapTraits(List<ObservationVariable> variables) {
        List<TraitObject> traits = new ArrayList<>();
        Integer variablesMissingTrait = 0;
        for (ObservationVariable var : variables) {

            // Skip the trait if there brapi trait field isn't present
            if (var.getTrait() == null) {
                variablesMissingTrait += 1;
                continue;
            }

            TraitObject trait = new TraitObject();
            trait.setDefaultValue(var.getDefaultValue());

            // Get the synonyms for easier reading. Set it as the trait name.
            String synonym = var.getSynonyms().size() > 0 ? var.getSynonyms().get(0) : null;
            trait.setTrait(getPrioritizedValue(synonym, var.getName()));

            trait.setDetails(var.getTrait().getDescription());
            // Get database id of external system to sync to enabled pushing through brAPI
            trait.setExternalDbId(var.getObservationVariableDbId());

            // Need to set where we are getting the data from so we don't push to a different
            // external link than where the trait was retrieved from.
            if (BrAPIService.getHostUrl(context) != null) {
                trait.setTraitDataSource(BrAPIService.getHostUrl(context));
            } else {
                // return null to indicate we couldn't process the traits
                return null;
            }

            // Parse out the scale of the variable
            if (var.getScale() != null) {
                if (var.getScale().getDataType() != null) {
                    trait.setFormat(convertBrAPIDataType(var.getScale().getDataType().getValue()));
                } else {
                    trait.setFormat("text");
                }
                if (var.getScale().getValidValues() != null) {

                    if (var.getScale().getValidValues().getMin() != null) {
                        trait.setMinimum(var.getScale().getValidValues().getMin().toString());
                    } else {
                        // Fieldbook requires empty string for min and maxes.
                        trait.setMinimum("");
                    }

                    if (var.getScale().getValidValues().getMax() != null) {
                        trait.setMaximum(var.getScale().getValidValues().getMax().toString());
                    } else {
                        trait.setMaximum("");
                    }

                    if (trait.getFormat().equals("categorical")) {
                        String details = trait.getDetails() + "\nCategories: ";
                        details += buildCategoryDescriptionString(var.getScale().getValidValues().getCategories());
                        trait.setDetails(details);
                        trait.setCategories(buildCategoryList(var.getScale().getValidValues().getCategories()));
                    }
                }
            }

            // Set some config variables in fieldbook
            trait.setVisible(true);
            trait.setRealPosition(0);

            traits.add(trait);
        }

        return Pair.create(traits, variablesMissingTrait);
    }

    private String buildCategoryDescriptionString(List<String> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            sb.append(categories.get(j).trim());
            if (j != categories.size() - 1) {
                sb.append("; ");
            }
        }
        return sb.toString();
    }

    private String buildCategoryList(List<String> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            String value;
            // parse out only value of specified as according to BrAPI, value=meaning
            String parts[] = categories.get(j).split("=");
            if (parts.length > 1) {
                value = parts[0].trim();
            }
            else {
                value = categories.get(j).trim();
            }
            sb.append(value);
            if (j != categories.size() - 1) {
                sb.append("/");
            }
        }
        return sb.toString();
    }

    private String convertBrAPIDataType(String dataType) {
        //TODO: Check these out and make sure they match with fieldbook data types.
        switch (dataType) {
            case "Code":
                // Not the ideal solution for this conversion
                return "text";
            case "Nominal":
                return "categorical";
            case "Date":
                return "date";
            case "Numerical":
                return "numeric";
            case "Ordinal":
                // All Field Book categories are ordered, so this works
                return "categorical";
            case "Duration":
                return "numeric";
            case "Text":
            default:
                return "text";
        }
    }

    public BrapiControllerResponse saveStudyDetails(BrapiStudyDetails studyDetails) {

        DataHelper dataHelper = new DataHelper(context);
        try {
            FieldObject field = new FieldObject();
            field.setExp_name(studyDetails.getStudyName());
            field.setExp_alias(studyDetails.getStudyDbId()); //hack for now to get in table alias not used for anything
            field.setExp_species(studyDetails.getCommonCropName());
            field.setCount(studyDetails.getNumberOfPlots().toString());

            // Get our host url
            if (BrAPIService.getHostUrl(context) != null) {
                field.setExp_source(BrAPIService.getHostUrl(context));
            } else {
                // Return an error notifying user we can't save this field
                return new BrapiControllerResponse(false, "Host is null");
            }

            field.setUnique_id("observationUnitDbId");
            field.setPrimary_id("Row");
            field.setSecondary_id("Column");
            field.setExp_sort("Plot");

            // Do a pre-check to see if the field exists so we can show an error
            Integer FieldUniqueStatus = dataHelper.checkFieldName(field.getExp_name());
            if (FieldUniqueStatus != -1) {
                return new BrapiControllerResponse(false, this.notUniqueFieldMessage);
            }

            // Check that there are not duplicate unique ids in the database
            HashMap<String, String> checkMap = new HashMap<>();

            // Construct our map to check for uniques
            for (List<String> dataRow : studyDetails.getValues()) {
                Integer idColumn = studyDetails.getAttributes().indexOf("Plot");
                checkMap.put(dataRow.get(idColumn), dataRow.get(idColumn));
            }

            if (!dataHelper.checkUnique(checkMap)) {
                return new BrapiControllerResponse(false, this.notUniqueIdMessage);
            }


            DataHelper.db.beginTransaction();
            // All checks finished, insert our data.
            int expId = dataHelper.createField(field, studyDetails.getAttributes());

            Boolean fail = false;
            String failMessage = "";

            // We want the saving of plots and traits wrap together in a transaction
            // so if they fail, the field can be deleted.
            try {
                for (List<String> dataRow : studyDetails.getValues()) {
                    dataHelper.createFieldData(expId, studyDetails.getAttributes(), dataRow);
                }

                // Insert the traits already associated with this study
                for (TraitObject t : studyDetails.getTraits()) {
                    dataHelper.insertTraits(t);
                }

                // If we haven't thrown an error by now, we are good.
                DataHelper.db.setTransactionSuccessful();

            } catch (Exception e) {
                // Delete our field if our traits or fields failed to insert
                fail = true;
                failMessage = e.toString();
            }

            DataHelper.db.endTransaction();
            dataHelper.close();
            dataHelper.open();

            if (fail) {
                return new BrapiControllerResponse(false, failMessage);
            } else {
                return new BrapiControllerResponse(true, "");
            }


        } catch (Exception e) {
            return new BrapiControllerResponse(false, e.toString());
        }
    }
}