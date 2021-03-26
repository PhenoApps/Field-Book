package com.fieldbook.tracker.brapi.service;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

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

import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams;
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.phenotype.ImagesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.TimeAdapter;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.core.response.BrAPIStudySingleResponse;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
import org.brapi.v2.model.pheno.BrAPIImage;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.BrAPIObservationUnitPosition;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.brapi.v2.model.pheno.BrAPIPositionCoordinateTypeEnum;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.brapi.v2.model.pheno.response.BrAPIImageListResponse;
import org.brapi.v2.model.pheno.response.BrAPIImageSingleResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrAPIServiceV2 implements BrAPIService{

    private final Context context;
    private final BrAPIClient apiClient;
    private final ImagesApi imagesApi;
    private final StudiesApi studiesApi;
    private final ProgramsApi programsApi;
    private final TrialsApi trialsApi;
    private final ObservationsApi observationsApi;
    private final ObservationUnitsApi observationUnitsApi;
    private final ObservationVariablesApi traitsApi;

    public BrAPIServiceV2(Context context) {
        this.context = context;
        // Make timeout longer. Set it to 60 seconds for now
        this.apiClient = new BrAPIClient(BrAPIService.getBrapiUrl(context), 60000);

        this.imagesApi = new ImagesApi(apiClient);
        this.studiesApi = new StudiesApi(apiClient);
        this.programsApi = new ProgramsApi(apiClient);
        this.trialsApi = new TrialsApi(apiClient);
        this.traitsApi = new ObservationVariablesApi(apiClient);
        this.observationsApi = new ObservationsApi(apiClient);
        this.observationUnitsApi = new ObservationUnitsApi(apiClient);
    }

    @Override
    public void authorizeClient(){
        try {
            apiClient.authenticate(t -> context.getSharedPreferences("Settings", 0)
                    .getString(GeneralKeys.BRAPI_TOKEN, null));
        } catch (ApiException error) {
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private void updatePageInfo(BrapiPaginationManager paginationManager, BrAPIMetadata metadata){
        if(paginationManager.getContext() != null) { //null check for JUnits
            ((Activity) paginationManager.getContext())
                    .runOnUiThread(() -> paginationManager.updatePageInfo(metadata.getPagination().getTotalPages()));
        }
    }

    public void postImageMetaData(FieldBookImage image,
                                  final Function<FieldBookImage, Void> function,
                                  final Function<Integer, Void> failFunction) {
        try {
            BrapiV2ApiCallBack<BrAPIImageListResponse> callback = new BrapiV2ApiCallBack<BrAPIImageListResponse>() {
                @Override
                public void onSuccess(BrAPIImageListResponse imageResponse, int i, Map<String, List<String>> map) {
                    final BrAPIImage response = imageResponse.getResult().getData().get(0);
                    function.apply(mapToImage(response));
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                }
            };

            BrAPIImage request = mapImage(image);
            imagesApi.imagesPostAsync(Arrays.asList(request), callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }

    }

    private BrAPIImage mapImage(FieldBookImage image) {
        BrAPIImage request = new BrAPIImage();
        request.setAdditionalInfo(image.getAdditionalInfo());
        request.setCopyright(image.getCopyright());
        request.setDescription(image.getDescription());
        request.setDescriptiveOntologyTerms(image.getDescriptiveOntologyTerms());
        request.setImageFileName(image.getFileName());
        request.setImageFileSize((int) image.getFileSize());
        request.setImageHeight(image.getHeight());
        request.setImageName(image.getImageName());
        request.setImageWidth(image.getWidth());
        request.setMimeType(image.getMimeType());
        request.setObservationUnitDbId(image.getUnitDbId());
        // TODO fix these
        //request.setImageLocation(image.getLocation());
        request.setImageTimeStamp(TimeAdapter.convertFrom(image.getTimestamp()));
        return request;
    }

    private FieldBookImage mapToImage(BrAPIImage image) {
        FieldBookImage request = new FieldBookImage();
        request.setAdditionalInfo(image.getAdditionalInfo());
        request.setDescription(image.getDescription());
        request.setDescriptiveOntologyTerms(image.getDescriptiveOntologyTerms());
        request.setFileName(image.getImageFileName());
        request.setFileSize((int) image.getImageFileSize());
        request.setHeight(image.getImageHeight());
        request.setImageName(image.getImageName());
        request.setWidth(image.getImageWidth());
        request.setMimeType(image.getMimeType());
        request.setUnitDbId(image.getObservationUnitDbId());
        // TODO fix these
        //request.setLocation(image.getImageLocation());
        request.setTimestamp(TimeAdapter.convertFrom(image.getImageTimeStamp()));
        return request;
    }

    public void putImageContent(FieldBookImage image,
                                final Function<FieldBookImage, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIImageSingleResponse> callback = new BrapiV2ApiCallBack<BrAPIImageSingleResponse>() {
                @Override
                public void onSuccess(BrAPIImageSingleResponse imageResponse, int i, Map<String, List<String>> map) {
                    final BrAPIImage response = imageResponse.getResult();
                    function.apply(mapToImage(response));
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            imagesApi.imagesImageDbIdImagecontentPutAsync(image.getDbId(), image.getImageData(), callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }

    }

    public void putImage(FieldBookImage image,
                         final Function<FieldBookImage, Void> function,
                         final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIImageSingleResponse> callback = new BrapiV2ApiCallBack<BrAPIImageSingleResponse>() {
                @Override
                public void onSuccess(BrAPIImageSingleResponse imageResponse, int i, Map<String, List<String>> map) {
                    final BrAPIImage response = imageResponse.getResult();
                    function.apply(mapToImage(response));
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            BrAPIImage request = mapImage(image);
            imagesApi.imagesImageDbIdPutAsync(image.getDbId(), request, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }

    }

    public void getPrograms(final BrapiPaginationManager paginationManager,
                            final Function<List<BrapiProgram>, Void> function,
                            final Function<Integer, Void> failFunction) {
        Integer initPage = paginationManager.getPage();
        try {
           BrapiV2ApiCallBack<BrAPIProgramListResponse> callback = new BrapiV2ApiCallBack<BrAPIProgramListResponse>() {
               @Override
               public void onSuccess(BrAPIProgramListResponse programsResponse, int i, Map<String, List<String>> map) {
                   // Cancel processing if the page that was processed is not the page
                   // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                   if (initPage.equals(paginationManager.getPage())) {
                       updatePageInfo(paginationManager, programsResponse.getMetadata());
                       List<BrAPIProgram> programList = programsResponse.getResult().getData();
                       function.apply(mapPrograms(programList));
                   }
               }

               @Override
               public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                   failFunction.apply(error.getCode());
                   Log.e("BrAPIServiceV2", "API Exception", error);
               }
           };
           ProgramQueryParams queryParams = new ProgramQueryParams();
           queryParams.page(paginationManager.getPage()).pageSize(paginationManager.getPageSize());
           programsApi.programsGetAsync(queryParams, callback);
       } catch (ApiException error) {
           failFunction.apply(error.getCode());
           Log.e("BrAPIServiceV2", "API Exception", error);
       }
    }

    private List<BrapiProgram> mapPrograms(List<BrAPIProgram> programList) {
        List<BrapiProgram> brapiPrograms = new ArrayList<>();
        if (programList != null) {
            for (BrAPIProgram program : programList) {
                BrapiProgram brapiProgram = new BrapiProgram();
                brapiProgram.setProgramName(program.getProgramName());
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
            BrapiV2ApiCallBack<BrAPITrialListResponse> callback = new BrapiV2ApiCallBack<BrAPITrialListResponse>() {
                @Override
                public void onSuccess(BrAPITrialListResponse trialsResponse, int i, Map<String, List<String>> map) {
                    // Cancel processing if the page that was processed is not the page
                    // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                    if (initPage.equals(paginationManager.getPage())) {
                        updatePageInfo(paginationManager, trialsResponse.getMetadata());
                        List<BrAPITrial> trialList = trialsResponse.getResult().getData();
                        function.apply(mapTrials(trialList));
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };
            TrialQueryParams queryParams = new TrialQueryParams();
            queryParams.programDbId(programDbId).page(paginationManager.getPage()).pageSize(paginationManager.getPageSize());
            trialsApi.trialsGetAsync(queryParams, callback);
        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private List<BrapiTrial> mapTrials(List<BrAPITrial> trialList) {
        List<BrapiTrial> brapiTrials = new ArrayList<>();
        if (trialList != null) {
            for (BrAPITrial trial : trialList) {
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

            BrapiV2ApiCallBack<BrAPIStudyListResponse> callback = new BrapiV2ApiCallBack<BrAPIStudyListResponse>() {
                @Override
                public void onSuccess(BrAPIStudyListResponse studiesResponse, int i, Map<String, List<String>> map) {
                    // Cancel processing if the page that was processed is not the page
                    // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                    if (initPage.equals(paginationManager.getPage())) {
                        updatePageInfo(paginationManager, studiesResponse.getMetadata());
                        final List<BrapiStudyDetails> studies = new ArrayList<>();
                        final List<BrAPIStudy> studySummaryList = studiesResponse.getResult().getData();
                        for (BrAPIStudy studySummary : studySummaryList) {
                            studies.add(mapStudy(studySummary));
                        }

                        function.apply(studies);
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            StudyQueryParams queryParams = new StudyQueryParams();
            queryParams.active("true").programDbId(programDbId).trialDbId(trialDbId).page(paginationManager.getPage()).pageSize(paginationManager.getPageSize());
            studiesApi.studiesGetAsync(queryParams, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }

    }

    public void getStudyDetails(final String studyDbId,
                                final Function<BrapiStudyDetails, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIStudySingleResponse> callback = new BrapiV2ApiCallBack<BrAPIStudySingleResponse>() {
                @Override
                public void onSuccess(BrAPIStudySingleResponse studyResponse, int i, Map<String, List<String>> map) {

                    final BrapiStudyDetails study = mapStudy(studyResponse.getResult());
                    function.apply(study);

                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            studiesApi.studiesStudyDbIdGetAsync(studyDbId, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private BrapiStudyDetails mapStudy(BrAPIStudy study) {
        BrapiStudyDetails studyDetails = new BrapiStudyDetails();
        studyDetails.setStudyDbId(study.getStudyDbId());
        studyDetails.setStudyName(study.getStudyName());
        studyDetails.setCommonCropName(study.getCommonCropName());
        studyDetails.setStudyDescription(study.getStudyDescription());
        studyDetails.setStudyLocation(study.getLocationName());
        return studyDetails;
    }

    public void getPlotDetails(final String studyDbId,
                               final Function<BrapiStudyDetails, Void> function,
                               final Function<Integer, Void> failFunction) {
        try {
            final Integer[] recursiveCounter = {0};
            final Integer pageSize = 1000;
            final BrapiStudyDetails study = new BrapiStudyDetails();
            study.setAttributes(new ArrayList<>());
            study.setValues(new ArrayList<>());

            BrapiV2ApiCallBack<BrAPIObservationUnitListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationUnitListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationUnitListResponse response, int i, Map<String, List<String>> map) {
                    int page = response.getMetadata().getPagination().getCurrentPage();
                    if(page == 0){
                        //one time code
                        study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                    }

                    //every time
                    mapAttributeValues(study, response.getResult().getData());

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
                            ObservationUnitQueryParams queryParams = new ObservationUnitQueryParams();
                            queryParams.studyDbId(studyDbId).observationUnitLevelName("plot").page(recursiveCounter[0]).pageSize(pageSize);
                            observationUnitsApi.observationunitsGetAsync(queryParams, this);
                        } catch (ApiException error) {
                            failFunction.apply(error.getCode());
                            Log.e("BrAPIServiceV2", "API Exception", error);
                        }
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }

            };

            ObservationUnitQueryParams queryParams = new ObservationUnitQueryParams();
            queryParams.studyDbId(studyDbId).observationUnitLevelName("plot").page(0).pageSize(pageSize);
            observationUnitsApi.observationunitsGetAsync(queryParams, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private void mapAttributeValues(BrapiStudyDetails study, List<BrAPIObservationUnit> data) {
        List<String> attributes = study.getAttributes();
        List<List<String>> attributesTable = new ArrayList<>();

        for (BrAPIObservationUnit unit : data) {
            Map<String, String> attributesMap = new HashMap<>();

            BrAPIObservationUnitPosition pos = unit.getObservationUnitPosition();
            List<BrAPIObservationUnitLevelRelationship> levels = pos.getObservationLevelRelationships();
            levels.add(pos.getObservationLevel());
            for(BrAPIObservationUnitLevelRelationship level: levels){
                String attributeName = level.getLevelName().getBrapiValue();
                attributeName = attributeName.substring(0,1).toUpperCase() + attributeName.substring(1).toLowerCase();
                attributesMap.put(attributeName, level.getLevelCode());
            }

            if (pos.getPositionCoordinateX() != null){
                String rowColStr = getRowColStr(pos.getPositionCoordinateXType());
                if(rowColStr == null){
                    rowColStr = "Row";
                }
                attributesMap.put(rowColStr, pos.getPositionCoordinateX());
            }

            if (pos.getPositionCoordinateY() != null){
                String rowColStr = getRowColStr(pos.getPositionCoordinateYType());
                if(rowColStr == null){
                    rowColStr = "Column";
                }
                attributesMap.put(rowColStr, pos.getPositionCoordinateY());
            }

            if (pos.getEntryType().getBrapiValue() != null)
                attributesMap.put("EntryType", pos.getEntryType().getBrapiValue());
            if (unit.getGermplasmName() != null)
                attributesMap.put("Germplasm", unit.getGermplasmName());
            if (unit.getObservationUnitDbId() != null)
                attributesMap.put("ObservationUnitDbId", unit.getObservationUnitDbId());
            if (unit.getObservationUnitName() != null)
                attributesMap.put("ObservationUnitName", unit.getObservationUnitName());

            List<String> dataRow = new ArrayList<>();
            if(attributes.isEmpty()){
                attributes.addAll(attributesMap.keySet());
                study.setAttributes(attributes);
                dataRow.addAll(attributesMap.values());
            }else{
                for(String attr: attributes){
                    if(attributesMap.containsKey(attr)){
                        dataRow.add(attributesMap.get(attr));
                    }else{
                        dataRow.add("");
                    }
                }
            }
            attributesTable.add(dataRow);
        }

        study.getValues().addAll(attributesTable);
    }

    private String getRowColStr(BrAPIPositionCoordinateTypeEnum type) {
        if(null != type){
            switch (type) {
                case PLANTED_INDIVIDUAL:
                case GRID_COL:
                case MEASURED_COL:
                case LATITUDE:
                    return "Column";
                case PLANTED_ROW:
                case GRID_ROW:
                case MEASURED_ROW:
                case LONGITUDE:
                    return "Row";
            }
        }
        return null;
    }

    public void getOntology(final BrapiPaginationManager paginationManager,
                            final Function<List<TraitObject>, Void> function,
                            final Function<Integer, Void> failFunction) {
        Integer initPage = paginationManager.getPage();
        try {

            BrapiV2ApiCallBack<BrAPIObservationVariableListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationVariableListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationVariableListResponse response, int i, Map<String, List<String>> map) {
                    // Cancel processing if the page that was processed is not the page
                    // that we are currently on. For Example: User taps "Next Page" before brapi call returns data
                    if (initPage.equals(paginationManager.getPage())) {
                        updatePageInfo(paginationManager, response.getMetadata());
                        // Result contains a list of observation variables
                        List<BrAPIObservationVariable> brapiTraitList = response.getResult().getData();
                        final List<TraitObject> traitsList = mapTraits(brapiTraitList);

                        function.apply(traitsList);
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }

            };

            VariableQueryParams queryParams = new VariableQueryParams();
            queryParams.page(paginationManager.getPage()).pageSize(paginationManager.getPageSize());
            traitsApi.variablesGetAsync(queryParams, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }


    private Observation mapToObservation(BrAPIObservation obs){
        Observation newObservation = new Observation();
        newObservation.setDbId(obs.getObservationDbId());
        newObservation.setUnitDbId(obs.getObservationUnitDbId());
        newObservation.setVariableDbId(obs.getObservationVariableDbId());
        return newObservation;
    }

    public void postPhenotypes(List<Observation> observations,
                               final Function<List<Observation>, Void> function,
                               final Function<Integer, Void> failFunction) {
        try {
            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse phenotypesResponse, int i, Map<String, List<String>> map) {
                    List<Observation> newObservations = new ArrayList<>();
                    for(BrAPIObservation obs: phenotypesResponse.getResult().getData()){
                        newObservations.add(mapToObservation(obs));
                    }

                    function.apply(newObservations);
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            List<BrAPIObservation> request = new ArrayList<>();

            for (Observation observation : observations) {
                BrAPIObservation newObservation = new BrAPIObservation();
                newObservation.setCollector(observation.getCollector().trim());
                newObservation.setObservationTimeStamp(TimeAdapter.convertFrom(observation.getTimestamp()));
                newObservation.setObservationUnitDbId(observation.getUnitDbId());
                newObservation.setStudyDbId(observation.getStudyId());
                newObservation.setObservationVariableDbId(observation.getVariableDbId());
                newObservation.setObservationVariableName(observation.getVariableName());
                newObservation.setValue(observation.getValue());

                request.add(newObservation);
            }

            observationsApi.observationsPostAsync(request, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    public void putObservations(List<Observation> observations,
                                final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse observationsResponse, int i, Map<String, List<String>> map) {
                    List<Observation> newObservations = new ArrayList<>();
                    for(BrAPIObservation obs: observationsResponse.getResult().getData()){
                        newObservations.add(mapToObservation(obs));
                    }
                    function.apply(newObservations);
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            Map<String, BrAPIObservation> request = new HashMap<>();

            for (Observation obs : observations) {
                BrAPIObservation o = new BrAPIObservation();
                o.setCollector(obs.getCollector().trim());
                o.setObservationDbId(obs.getDbId());
                o.setObservationTimeStamp(TimeAdapter.convertFrom(obs.getTimestamp()));
                o.setObservationUnitDbId(obs.getUnitDbId());
                o.setObservationVariableDbId(obs.getVariableDbId());
                o.setValue(obs.getValue());

                request.put(obs.getDbId(), o);
            }

            observationsApi.observationsPutAsync(request, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
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

    public void getTraits(final String studyDbId,
                          final Function<BrapiStudyDetails, Void> function,
                          final Function<Integer, Void> failFunction) {
        try {
            final Integer[] recursiveCounter = {0};
            final Integer pageSize = 1000;
            final BrapiStudyDetails study = new BrapiStudyDetails();
            study.setTraits(new ArrayList<>());

            BrapiV2ApiCallBack<BrAPIObservationVariableListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationVariableListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationVariableListResponse response, int i, Map<String, List<String>> map) {
                    //every time
                    study.getTraits().addAll(mapTraits(response.getResult().getData()));
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
                            VariableQueryParams queryParams = new VariableQueryParams();
                            queryParams.studyDbId(studyDbId).page(recursiveCounter[0]).pageSize(pageSize);
                            traitsApi.variablesGetAsync(queryParams, this);
                        } catch (ApiException error) {
                            failFunction.apply(error.getCode());
                            Log.e("BrAPIServiceV2", "API Exception", error);
                        }
                    }
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            VariableQueryParams queryParams = new VariableQueryParams();
            queryParams.studyDbId(studyDbId).page(0).pageSize(pageSize);
            traitsApi.variablesGetAsync(queryParams, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private List<TraitObject> mapTraits(List<BrAPIObservationVariable> variables) {
        List<TraitObject> traits = new ArrayList<>();
        for (BrAPIObservationVariable var : variables) {
            TraitObject trait = new TraitObject();
            trait.setDefaultValue(var.getDefaultValue());

            // Get the synonyms for easier reading. Set it as the trait name.
            String synonym = var.getSynonyms().size() > 0 ? var.getSynonyms().get(0) : null;
            trait.setTrait(getPrioritizedValue(synonym, var.getObservationVariableName()));

            trait.setDetails(var.getTrait().getTraitDescription());
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

                    trait.setCategories(buildCategoryList(var.getScale().getValidValues().getCategories()));
                }
                if (var.getScale().getDataType() != null) {
                    trait.setFormat(convertBrAPIDataType(var.getScale().getDataType().getBrapiValue()));
                } else {
                    trait.setFormat("text");
                }

            }

            // Set some config variables in fieldbook
            trait.setVisible(true);
            trait.setRealPosition("");

            traits.add(trait);
        }
        return traits;
    }

    private String buildCategoryList(List<BrAPIScaleValidValuesCategories> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            sb.append(categories.get(j).getLabel());
            if (j != categories.size() - 1) {
                sb.append("/");
            }
        }
        return sb.toString();
    }

    private String convertBrAPIDataType(String dataType) {
        //TODO: Check these out and make sure they match with fieldbook data types.
        switch (dataType) {
            case "Nominal":
            case "Ordinal":
                // All Field Book categories are ordered, so this works
                return "categorical";
            case "Date":
                return "date";
            case "Numerical":
            case "Duration":
                return "numeric";
            case "Code":
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

            field.setUnique_id("ObservationUnitDbId");
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