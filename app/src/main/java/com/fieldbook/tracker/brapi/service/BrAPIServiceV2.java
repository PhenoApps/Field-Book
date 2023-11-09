package com.fieldbook.tracker.brapi.service;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.ApiError;
import com.fieldbook.tracker.brapi.ApiErrorCode;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiProgram;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.BrapiTrial;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.FailureFunction;
import com.fieldbook.tracker.utilities.SuccessFunction;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams;
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.phenotype.ImagesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.TimeAdapter;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.core.response.BrAPIStudySingleResponse;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.brapi.v2.model.pheno.BrAPIImage;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.BrAPIObservationUnitPosition;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.brapi.v2.model.pheno.BrAPIPositionCoordinateTypeEnum;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.brapi.v2.model.pheno.response.BrAPIImageListResponse;
import org.brapi.v2.model.pheno.response.BrAPIImageSingleResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasmSynonyms;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.BiFunction;

public class BrAPIServiceV2 extends AbstractBrAPIService implements BrAPIService {

    private static final String ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES = "observationLevelNames";

    //used to identify field book db id in external references
    private final String fieldBookReferenceSource = "Field Book Upload";

    private final Context context;
    private final BrAPIClient apiClient;
    private final ImagesApi imagesApi;
    private final StudiesApi studiesApi;
    private final ProgramsApi programsApi;
    private final TrialsApi trialsApi;
    private final ObservationsApi observationsApi;
    private final ObservationUnitsApi observationUnitsApi;
    private final GermplasmApi germplasmApi;
    private final ObservationVariablesApi traitsApi;

    public BrAPIServiceV2(Context context) {
        this.context = context;
        // Make timeout longer. Set it to 60 seconds for now
        this.apiClient = new BrAPIClient(BrAPIService.getBrapiUrl(context), BrAPIService.getTimeoutValue(context) * 1000);

        this.imagesApi = new ImagesApi(apiClient);
        this.studiesApi = new StudiesApi(apiClient);
        this.programsApi = new ProgramsApi(apiClient);
        this.trialsApi = new TrialsApi(apiClient);
        this.traitsApi = new ObservationVariablesApi(apiClient);
        this.observationsApi = new ObservationsApi(apiClient);
        this.observationUnitsApi = new ObservationUnitsApi(apiClient);
        this.germplasmApi = new GermplasmApi(apiClient);
    }

    @Override
    public void authorizeClient(){
        try {
            apiClient.authenticate(t -> context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)
                    .getString(GeneralKeys.BRAPI_TOKEN, null));
        } catch (ApiException error) {
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    @Override
    public void getObservationLevels(String programDbId, SuccessFunction<List<BrapiObservationLevel>> successFn, FailureFunction<ApiError> failFn) {
        BrapiV2ApiCallBack<BrAPIObservationLevelListResponse> callBack = new BrapiV2ApiCallBack<BrAPIObservationLevelListResponse>() {
            @Override
            public void onSuccess(BrAPIObservationLevelListResponse brAPIObservationLevelListResponse, int i, Map<String, List<String>> map) {
                List<BrapiObservationLevel> observationLevels = new ArrayList<>();
                Collections.sort(brAPIObservationLevelListResponse.getResult().getData(), Comparator.comparing(BrAPIObservationUnitHierarchyLevel::getLevelOrder));

                for (BrAPIObservationUnitHierarchyLevel level : brAPIObservationLevelListResponse.getResult().getData()) {
                    observationLevels.add(new BrapiObservationLevel().setObservationLevelName(level.getLevelName()));
                }

                successFn.apply(observationLevels);
            }

            @Override
            public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                Log.e("BrAPIServiceV2", "Error fetching observation levels", error);
                ApiErrorCode e = ApiErrorCode.processErrorCode(error.getCode());
                failFn.apply(new ApiError().setErrorCode(e).setResponseBody(error.getResponseBody()));
            }
        };

        try {
            observationUnitsApi.observationlevelsGetAsync(null, null, programDbId, 0, 1000, callBack);
        } catch (ApiException e) {
            Log.e("BrAPIServiceV2", "Error sending BrAPI Request to fetch observation levels", e);
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
        request.setDescription(image.getDescription());
        request.setDescriptiveOntologyTerms(image.getDescriptiveOntologyTerms());
        request.setFileName(image.getImageFileName());
        if (image.getImageFileSize() != null) request.setFileSize((int) image.getImageFileSize());
        if (image.getImageHeight() != null) request.setHeight(image.getImageHeight());
        if (image.getImageWidth() != null) request.setWidth(image.getImageWidth());
        request.setImageName(image.getImageName());
        request.setMimeType(image.getMimeType());
        request.setUnitDbId(image.getObservationUnitDbId());
        request.setDbId(image.getImageDbId());
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
                               BrapiObservationLevel observationLevel, final Function<BrapiStudyDetails, Void> function,
                               final Function<Integer, Void> failFunction) {
        try {
            final Integer pageSize = Integer.parseInt(context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)
                    .getString(GeneralKeys.BRAPI_PAGE_SIZE, "50"));
            final BrapiStudyDetails study = new BrapiStudyDetails();
            study.setAttributes(new ArrayList<>());
            study.setValues(new ArrayList<>());
            List<BrAPIObservationUnit> allAttributeValues = new ArrayList<>();
            Set<String> allGermplasmNames = new HashSet<>();

            ObservationUnitQueryParams queryParams = new ObservationUnitQueryParams();
            queryParams.studyDbId(studyDbId);
            if (observationLevel != null && observationLevel.getObservationLevelName() != null) {
                queryParams.observationUnitLevelName(observationLevel.getObservationLevelName());
            } else queryParams.observationUnitLevelName("plot");
            queryParams.page(0).pageSize(pageSize);

            BrapiV2ApiCallBack<BrAPIObservationUnitListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationUnitListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationUnitListResponse response, int i, Map<String, List<String>> map) {
                    int page = response.getMetadata().getPagination().getCurrentPage();
                    if(page == 0){
                        //one time code
                        study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                    }
                    allAttributeValues.addAll(response.getResult().getData());

                    queryParams.page(queryParams.page() + 1);

                    // Extract germplasm names
                    Set<String> germplasmNames = (response.getResult().getData().stream()
                            .filter(unit -> unit.getGermplasmName() != null)
                            .map(BrAPIObservationUnit::getGermplasmName)
                            .collect(Collectors.toSet()));
//                    logger.info("Germplasm names are: " + germplasmNames);
                    allGermplasmNames.addAll(germplasmNames);

                    // Stop after 50 iterations (for safety)
                    // Stop if the current page is the last page according to the server
                    // Stop if there are no more contents
                    if((queryParams.page() > 50)
                            || (page >= (response.getMetadata().getPagination().getTotalPages() - 1))
                            || (response.getResult().getData().size() == 0)){
                        List<BrAPIGermplasm> germplasmDetails;
                        germplasmDetails = getGermplasmDetails(allGermplasmNames, failFunction);
                        mapAttributeValues(study, allAttributeValues, germplasmDetails);
                        function.apply(study);

                    } else {
                        try {
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

            observationUnitsApi.observationunitsGetAsync(queryParams, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private void mapAttributeValues(BrapiStudyDetails study, List<BrAPIObservationUnit> data, List<BrAPIGermplasm> germplasmDetails) {
        Logger logger = Logger.getLogger(getClass().getName());

        Map<String, Map<String, String>> unitAttributes = new HashMap<>(); // Map to store attributes for each unit

        for (BrAPIObservationUnit unit : data) {

            String unitDbId = unit.getObservationUnitDbId();
            // Create the unit's attributes hashmap
            unitAttributes.putIfAbsent(unitDbId, new HashMap<>());
            Map<String, String> attributesMap = unitAttributes.get(unitDbId);

            BrAPIObservationUnitPosition pos = unit.getObservationUnitPosition();
            if (pos != null) {

                List<BrAPIObservationUnitLevelRelationship> levels = pos.getObservationLevelRelationships();
                levels.add(pos.getObservationLevel());

                for(BrAPIObservationUnitLevelRelationship level: levels){
                    if(level.getLevelName() != null) {
                        String attributeName = level.getLevelName();
                        attributeName = attributeName.substring(0, 1).toUpperCase() + attributeName.substring(1).toLowerCase();
                        attributesMap.put(attributeName, level.getLevelCode());
//                        logger.info("Mapped attribute " + attributeName + " to " + level.getLevelCode());
                    }
                }

                if (pos.getPositionCoordinateX() != null){
                    String rowColStr = getRowColStr(pos.getPositionCoordinateXType());
                    if(rowColStr == null){
                        rowColStr = "Row";
                    }
                    attributesMap.put(rowColStr, pos.getPositionCoordinateX());
//                    logger.info("Mapped X-coordinate " + rowColStr + " to " + pos.getPositionCoordinateX());
                }

                if (pos.getPositionCoordinateY() != null){
                    String rowColStr = getRowColStr(pos.getPositionCoordinateYType());
                    if(rowColStr == null){
                        rowColStr = "Column";
                    }
                    attributesMap.put(rowColStr, pos.getPositionCoordinateY());
//                    logger.info("Mapped Y-coordinate " + rowColStr + " to " + pos.getPositionCoordinateY());
                }

                if (pos.getEntryType() != null && pos.getEntryType().getBrapiValue() != null) {
                    attributesMap.put("EntryType", pos.getEntryType().getBrapiValue());
//                    logger.info("Mapped EntryType to " + pos.getEntryType().getBrapiValue());
                }
            }
            if (unit.getGermplasmName() != null) {
                attributesMap.put("Germplasm", unit.getGermplasmName());
//                logger.info("Mapped Germplasm to " + unit.getGermplasmName());
            }
            if (unit.getGermplasmDbId() != null) {
                // find matching germplasm in germplasmDetails and extract synonyms and pedigree
                BrAPIGermplasm matchingGermplasm = findGermplasmByDbId(unit.getGermplasmDbId(), germplasmDetails);

                if (matchingGermplasm != null) {
                    // Extract pedigree and synonyms from the matching germplasm if defined
                    if (matchingGermplasm.getPedigree() != null) {
                        attributesMap.put("Pedigree", matchingGermplasm.getPedigree());
//                        logger.info("Mapped Pedigree to " + matchingGermplasm.getPedigree());
                    }

                    List<BrAPIGermplasmSynonyms> synonymsList = matchingGermplasm.getSynonyms();
                    if (synonymsList != null && !synonymsList.isEmpty()) {
                        List<String> synonyms = new ArrayList<>();
                        for (BrAPIGermplasmSynonyms synonym : synonymsList) {
                            synonyms.add(synonym.getSynonym());
                        }
                        attributesMap.put("Synonyms", String.join(",", synonyms));
//                        logger.info("Mapped Synonyms to " + String.join(",", synonyms));
                    }
                }
            }
            if (unit.getObservationUnitDbId() != null) {
                attributesMap.put("ObservationUnitDbId", unit.getObservationUnitDbId());
//                logger.info("Mapped ObservationUnitDbId to " + unit.getObservationUnitDbId());
            }
            if (unit.getObservationUnitName() != null) {
                attributesMap.put("ObservationUnitName", unit.getObservationUnitName());
//                logger.info("Mapped ObservationUnitName to " + unit.getObservationUnitName());
            }
        }

        // Extract a list of unique attribute names from the unitAttributes
        Set<String> uniqueAttributes = new HashSet<>();
        for (Map<String, String> attributesMap : unitAttributes.values()) {
            uniqueAttributes.addAll(attributesMap.keySet());
        }
        study.setAttributes(new ArrayList<>(uniqueAttributes)); // Set the unique attribute names
        logger.info("Added attributes to the study. Current attributes: " + study.getAttributes());

        // Assemble the attributesTable
        List<List<String>> attributesTable = new ArrayList<>();
        for (BrAPIObservationUnit unit : data) {
            String unitDbId = unit.getObservationUnitDbId();

            // Get the unit's attributes hashmap
            Map<String, String> attributesMap = unitAttributes.get(unitDbId);

            // Create a data row for each unit using the unique attribute names
            List<String> dataRow = new ArrayList<>();
            for (String attr : uniqueAttributes) {
                dataRow.add(attributesMap.getOrDefault(attr, ""));
            }
            attributesTable.add(dataRow);
            logger.info("Added new data row to attributes table: " + dataRow);
        }

        // Save the attributesTable to the study or perform any other necessary action
        study.getValues().addAll(attributesTable);
        logger.info("Updated study values with attributes table.");
    }

    // Helper method to find germplasm by dbId in the germplasmDetails list
    private BrAPIGermplasm findGermplasmByDbId(String germplasmDbId, List<BrAPIGermplasm> germplasmDetails) {
        for (BrAPIGermplasm germplasm : germplasmDetails) {
            if (germplasm.getGermplasmDbId().equals(germplasmDbId)) {
                return germplasm;
            }
        }
        return null; // Germplasm with the given dbId not found
    }

    public List<BrAPIGermplasm> getGermplasmDetails(Set<String> allGermplasmNames, final Function<Integer, Void> failFunction) {
        List<BrAPIGermplasm> germplasmDetails = new ArrayList<>();
        try {
            final Integer pageSize = Integer.parseInt(context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)
                    .getString(GeneralKeys.BRAPI_PAGE_SIZE, "50"));

            BrAPIGermplasmSearchRequest body = new BrAPIGermplasmSearchRequest();
            List<String> germplasmNamesList = new ArrayList<>(allGermplasmNames);
//            List<BrAPIGermplasm> germplasmDetails = new ArrayList<>();
            String searchResultsDbId;

            body.setGermplasmNames(germplasmNamesList);
            body.page(0).pageSize(pageSize);
            Logger logger = Logger.getLogger(getClass().getName());

            logger.info("All germplasm names are: " + allGermplasmNames);

            ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>>> response = germplasmApi.searchGermplasmPost(body);
            if (response.getBody().getLeft().isPresent()) { // Handle case where results are returned immediately
                BrAPIGermplasmListResponse listResponse = response.getBody().getLeft().get();
//                logger.info("Retrieved BrAPIGermplasmListResponse: " + listResponse);
                germplasmDetails = getListResult(response);
                if(hasMorePages(listResponse)) {
                    int currentPage = listResponse.getMetadata().getPagination().getCurrentPage() + 1;
                    int totalPages = listResponse.getMetadata().getPagination().getTotalPages();

                    while (currentPage < totalPages) {
                        body.setPage(currentPage);
                        response = germplasmApi.searchGermplasmPost(body);
                        if (response.getBody().getLeft().isPresent()) {
                            germplasmDetails.addAll(getListResult(response));
                        }
                        currentPage++;
                    }
                }
            } else { // Handle case where searchResultDbId is returned, follow up with call to searchGermplasmSearchResultsDbIdGet
                BrAPIAcceptedSearchResponse searchResponse = response.getBody().getRight().get();
//                logger.info("Retrieved BrAPIAcceptedSearchResponse: " + searchResponse);
                searchResultsDbId = searchResponse.getResult().getSearchResultsDbId();
                ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>>> getResponse = germplasmApi.searchGermplasmSearchResultsDbIdGet(searchResultsDbId, 0, pageSize);
                if (getResponse.getBody().getLeft().isPresent()) { // Should have this now for sure
                    BrAPIGermplasmListResponse listResponse = getResponse.getBody().getLeft().get();
//                    logger.info("Finally retrieved BrAPIGermplasmListResponse: " + listResponse);
                    germplasmDetails = getListResult(getResponse);
                    if(hasMorePages(listResponse)) {
                        int currentPage = listResponse.getMetadata().getPagination().getCurrentPage() + 1;
                        int totalPages = listResponse.getMetadata().getPagination().getTotalPages();

                        while (currentPage < totalPages) {
                            getResponse = germplasmApi.searchGermplasmSearchResultsDbIdGet(searchResultsDbId, currentPage, pageSize);
                            if (getResponse.getBody().getLeft().isPresent()) {
                                germplasmDetails.addAll(getListResult(getResponse));
                            }
                            currentPage++;
                        }
                    }
                }
            }

//            logger.info("Final germplasmDetails are: " + germplasmDetails);
            return germplasmDetails;

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
        return germplasmDetails;
    }

    private boolean hasMorePages(BrAPIResponse listResponse) {
        return listResponse.getMetadata() != null
                && listResponse.getMetadata().getPagination() != null
                && listResponse.getMetadata().getPagination().getCurrentPage() < listResponse.getMetadata().getPagination().getTotalPages() - 1;
    }

    private <T, V> List<V> getListResult(ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse) {
        BrAPIResponse listResponse = (BrAPIResponse) searchGetResponse.getBody().getLeft().get();
        BrAPIResponseResult responseResult = (BrAPIResponseResult) listResponse.getResult();
        return responseResult != null ? responseResult.getData() :
                new ArrayList<>();
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
                            final BiFunction<List<TraitObject>, Integer, Void> function,
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

                        try {
                            final Pair<List<TraitObject>, Integer> traitsResult = mapTraits(brapiTraitList);
                            function.apply(traitsResult.first, traitsResult.second);
                        } catch (JSONException e) {
                            failFunction.apply(-1);
                            Log.e("BrAPIServiceV2", "Trait mapping failed", e);
                        }
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
    /**
     * Function to pull out the observations for a given study.
     * This method will do an async call to the BrAPI validated server and will get the Observations
     * @param studyDbId
     * @param observationVariableDbIds
     * @param paginationManager
     * @param function
     * @param failFunction
     */
    public void getObservations(final String studyDbId, final List<String> observationVariableDbIds,
                                BrapiPaginationManager paginationManager, final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction ) {

        try {
            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse response, int i, Map<String, List<String>> map) {

                    paginationManager.updateTotalPages(response.getMetadata().getPagination().getTotalPages());

                    if (response.getResult() != null) {

                        // Result contains a list of observation variables
                        List<BrAPIObservation> brapiObservationList = response.getResult().getData();
                        final List<Observation> observationList = mapObservations(brapiObservationList);

                        function.apply(observationList);

                        System.out.println("TotalNumber of Observation records: "+response.getMetadata().getPagination().getTotalCount());

                        //Slide pagingation up 1 this is handled within function
                        paginationManager.moveToNextPage();

                        //Check if next recursion is valid
                        if(paginationManager.getPage() < paginationManager.getTotalPages()) {
                            //recurse
                            getObservations(studyDbId, observationVariableDbIds, paginationManager, function, failFunction);
                        }
                    }
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }

            };


            // Need to make one of these: ObservationQueryParams()
            // protected java.lang.String observationDbId;
            //    protected java.lang.String observationUnitDbId;
            //    protected java.lang.String germplasmDbId;
            //    protected java.lang.String observationVariableDbId;
            //    protected java.lang.String studyDbId;
            //    protected java.lang.String locationDbId;
            //    protected java.lang.String trialDbId;
            //    protected java.lang.String programDbId;
            //    protected java.lang.String seasonDbId;
            //    protected java.lang.String observationUnitLevelName;
            //    protected java.lang.String observationUnitLevelOrder;
            //    protected java.lang.String observationUnitLevelCode;
            //    protected java.lang.String observationTimeStampRangeStart;
            //    protected java.lang.String observationTimeStampRangeEnd;
            //    protected java.lang.String externalReferenceID;
            //    protected java.lang.String externalReferenceSource;
            ObservationQueryParams queryParams = new ObservationQueryParams();
            queryParams.studyDbId(studyDbId).page(paginationManager.getPage()).pageSize(paginationManager.getPageSize());

//            System.out.println(queryParams.toString());
            //This kicks off the async thread with the parameters and the callback
            observationsApi.observationsGetAsync(queryParams, callback);

        } catch (ApiException e) {
            Log.e("error-go", e.toString());
            failFunction.apply(e.getCode());
        }
    }

    private Observation mapToObservation(BrAPIObservation obs){
        Observation newObservation = new Observation();
        newObservation.setDbId(obs.getObservationDbId());
        newObservation.setUnitDbId(obs.getObservationUnitDbId());
        newObservation.setVariableDbId(obs.getObservationVariableDbId());

        //search imported obs references for first field book id
        List<BrAPIExternalReference> references = obs.getExternalReferences();
        if (references != null && !references.isEmpty()) {
            for (BrAPIExternalReference ref : references) {
                String source = ref.getReferenceSource();
                if (source != null && source.equals(fieldBookReferenceSource)) {
                    String id = ref.getReferenceID();
                    if (id != null && !id.isEmpty()) {
                        newObservation.setFieldBookDbId(id);
                        break;
                    }
                }
            }
        }

        return newObservation;
    }

    /**
     * Function to map the observations from Brapi to the Fieldbook Observation variable.
     * @param brapiObservationList
     * @return list of Fieldbook Observation objects
     */
    private List<Observation> mapObservations(List<BrAPIObservation> brapiObservationList) {
        List<Observation> outputList = new ArrayList<>();
        for(BrAPIObservation brapiObservation : brapiObservationList) {
            Observation newObservation = new Observation();

            newObservation.setVariableName(brapiObservation.getObservationVariableName());
            newObservation.setDbId(brapiObservation.getObservationDbId());
            newObservation.setUnitDbId(brapiObservation.getObservationUnitDbId());
            newObservation.setVariableDbId(brapiObservation.getObservationVariableDbId());
            newObservation.setValue(brapiObservation.getValue());

            outputList.add(newObservation);

        }
        return outputList;
    }

    public void createObservations(List<Observation> observations,
                               final Function<List<Observation>, Void> function,
                               final Function<Integer, Void> failFunction) {
        try {
            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse phenotypesResponse, int i, Map<String, List<String>> map) {

                    try {

                        List<Observation> newObservations = new ArrayList<>();
                        if(phenotypesResponse.getResult() != null && phenotypesResponse.getResult().getData() != null) {
                            for (BrAPIObservation obs : phenotypesResponse.getResult().getData()) {
                                newObservations.add(mapToObservation(obs));
                            }
                        }

                        function.apply(newObservations);

                    } catch (Exception e) {
                        e.printStackTrace();
                        failFunction.apply(ApiErrorCode.ERROR_READING_RESPONSE.getCode()); //Cause of the error is unknown, but it is probably not a 4XX error
                    }
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            List<BrAPIObservation> request = new ArrayList<>();

            for (Observation observation : observations) {
                BrAPIObservation newObservation = convertToBrAPIObservation(observation);
                request.add(newObservation);
            }

            observationsApi.observationsPostAsync(request, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    public void updateObservations(List<Observation> observations,
                                final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse observationsResponse, int i, Map<String, List<String>> map) {
                    try {
                        List<Observation> newObservations = new ArrayList<>();
                        if (observationsResponse.getResult() != null && observationsResponse.getResult().getData() != null) {
                            for (BrAPIObservation obs : observationsResponse.getResult().getData()) {
                                newObservations.add(mapToObservation(obs));
                            }
                        }
                        function.apply(newObservations);

                    } catch (Exception e) {
                        e.printStackTrace();
                        failFunction.apply(ApiErrorCode.ERROR_READING_RESPONSE.getCode()); //Cause of the error is unknown, but it is probably not a 4XX error
                    }
                }

                @Override
                public void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders) {
                    failFunction.apply(error.getCode());
                    Log.e("BrAPIServiceV2", "API Exception", error);
                }
            };

            Map<String, BrAPIObservation> request = new HashMap<>();

            for (Observation obs : observations) {
                BrAPIObservation o = convertToBrAPIObservation(obs);
                request.put(obs.getDbId(), o);
            }

            observationsApi.observationsPutAsync(request, callback);

        } catch (ApiException error) {
            failFunction.apply(error.getCode());
            Log.e("BrAPIServiceV2", "API Exception", error);
        }
    }

    private BrAPIObservation convertToBrAPIObservation(Observation observation){
        BrAPIObservation newObservation = new BrAPIObservation();
        newObservation.setCollector(observation.getCollector().trim());
        newObservation.setObservationDbId(observation.getDbId());
        newObservation.setObservationTimeStamp(TimeAdapter.convertFrom(observation.getTimestamp()));
        newObservation.setObservationUnitDbId(observation.getUnitDbId());
        newObservation.setStudyDbId(observation.getStudyId());
        newObservation.setObservationVariableDbId(observation.getVariableDbId());
        newObservation.setObservationVariableName(observation.getVariableName());
        newObservation.setValue(observation.getValue());

        BrAPIExternalReference reference = new BrAPIExternalReference();
        reference.setReferenceID(observation.getFieldbookDbId());
        reference.setReferenceSource(fieldBookReferenceSource);

        newObservation.setExternalReferences(Collections.singletonList(reference));

        return newObservation;
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

                    try {
                        study.getTraits().addAll(mapTraits(response.getResult().getData()).first);
                    } catch (JSONException error) {
                        failFunction.apply(-1);
                        Log.e("BrAPIServiceV2", "Traits failed to map", error);
                    }
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

    private Pair<List<TraitObject>, Integer> mapTraits(List<BrAPIObservationVariable> variables) throws JSONException {
        List<TraitObject> traits = new ArrayList<>();
        int variablesMissingTrait = 0;
        for (BrAPIObservationVariable var : variables) {

            TraitObject trait = new TraitObject();
            trait.setDefaultValue(var.getDefaultValue());

            // Get the synonyms for easier reading. Set it as the trait name.
            String synonym = var.getSynonyms().size() > 0 ? var.getSynonyms().get(0) : null;
            trait.setTrait(getPrioritizedValue(synonym, var.getObservationVariableName())); //This will default to the Observation Variable Name if available.

            //v5.1.0 bugfix branch update, getPrioritizedValue can return null, trait name should never be null
            // Skip the trait if there brapi trait field isn't present
            if (var.getTrait() == null || trait.getTrait() == null) {
                variablesMissingTrait += 1;
                continue;
            }

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
                //For categorical traits, include label value pairs in details
                if (trait.getFormat().equals("categorical")) {
                    String details = trait.getDetails() + "\nCategories: ";
                    details += buildCategoryDescriptionString(var.getScale().getValidValues().getCategories());
                    trait.setDetails(details);

//                    try {
//                        trait.setAdditionalInfo(buildCategoryValueLabelJsonStr(var.getScale().getValidValues().getCategories()));
//                    } catch (Exception e) {
//                        Log.d("FieldBookError", "Error parsing trait label/value.");
//                    }
                }

            }

            // The BMS implementation of BrAPI 2.x Variables includes an Observation Variable with observationLevelNames metadata in the additionalInfo field.
            // This metadata helps identify the level(s) at which a variable is utilized within a study/field. The information will be utilized to filter the variables
            // based on the selected Observation Level during BrAPI Field Import, ensuring that only relevant variables will be processed.
            if (var.getAdditionalInfo() != null && var.getAdditionalInfo().has(ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES)) {
                JsonArray observationVariableNames = var.getAdditionalInfo().getAsJsonArray(ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES);
                // Convert the JsonArray to a List<String>
                Type listType = new TypeToken<List<String>>() {}.getType();
                trait.setObservationLevelNames(new Gson().fromJson(observationVariableNames, listType));
            }

            // Set some config variables in fieldbook
            trait.setVisible(true);
            trait.setRealPosition(0);

            traits.add(trait);
        }

        return Pair.create(traits, variablesMissingTrait);
    }

    private String buildCategoryDescriptionString(List<BrAPIScaleValidValuesCategories> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            sb.append(categories.get(j).getValue()+"="+categories.get(j).getLabel());
            if (j != categories.size() - 1) {
                sb.append("; ");
            }
        }
        return sb.toString();
    }

    /**
     * Called in map traits, this will build a JSON string that encodes
     * value/label category pairs.
     * @param categories the list of = delimited value/label pairs
     * @return the json encoded valid values BrAPI object
     * @throws JSONException captured in mapTraits
     */
    private String buildCategoryValueLabelJsonStr(List<BrAPIScaleValidValuesCategories> categories) throws JSONException {
        JSONObject catObj = new JSONObject();
        JSONArray cats = new JSONArray();
        for (int j = 0; j < categories.size(); ++j) {
            JSONObject valueLabel = new JSONObject();
            valueLabel.put("value", categories.get(j).getValue());
            valueLabel.put("label", categories.get(j).getLabel());
            cats.put(valueLabel);
        }
        catObj.put("catValueLabel", cats);
        return catObj.toString();
    }

    private String buildCategoryList(List<BrAPIScaleValidValuesCategories> categories) {
        try {
            return CategoryJsonUtil.Companion.encode(new ArrayList<>(categories));
        } catch (Exception e) {
            return buildCategoryListOld(categories);
        }
    }

    private String buildCategoryListOld(List<BrAPIScaleValidValuesCategories> categories) {
        StringBuilder sb = new StringBuilder();
        if (categories != null) {
            for (int j = 0; j < categories.size(); ++j) {
                // Use the "value" like brapi v1
                sb.append(categories.get(j).getValue());
                if (j != categories.size() - 1) {
                    sb.append("/");
                }
            }
        }
        return sb.toString();
    }

    /**
     * @param dataType
     * @return
     */
    private String convertBrAPIDataType(String dataType) {
        //TODO: Check these out and make sure they match with fieldbook data types.
        switch (dataType) {
            case "Nominal":
            case "Ordinal":
            case "categorical":
            case "qualitative":
                // All Field Book categories are ordered, so this works
                return "categorical";
            case "date":
            case "Date":
                return "date";
            case "Numerical":
            case "Duration":
            case "numeric":
                return "numeric";
            case "rust rating":
            case "disease rating":
                return "disease rating";
            case "percent":
                return "percent";
            case "boolean":
                return "boolean";
            case "photo":
                return "photo";
            case "audio":
                return "audio";
            case "counter":
                return "counter";
            case "multicat":
                return "multicat";
            case "location":
                return "location";
            case "barcode":
                return "barcode";
            case "gnss":
                return "gnss";
            case "zebra label print":
                return "zebra label print";
            case "usb camera":
                return "usb camera";
            case "Code":
            case "Text":
            default:
                return "text";
        }
    }

    public BrapiControllerResponse saveStudyDetails(BrapiStudyDetails studyDetails, BrapiObservationLevel selectedObservationLevel, String primaryId, String secondaryId) {

        DataHelper dataHelper = new DataHelper(context);

        String observationLevel;
        if (selectedObservationLevel == null) observationLevel = "Plot";
        else observationLevel = selectedObservationLevel.getObservationLevelName().substring(0, 1).toUpperCase() + selectedObservationLevel.getObservationLevelName().substring(1);
        try {
            FieldObject field = new FieldObject();
            field.setExp_name(studyDetails.getStudyName());
            field.setExp_alias(studyDetails.getStudyDbId()); //hack for now to get in table alias not used for anything
            field.setExp_species(studyDetails.getCommonCropName());
            field.setCount(studyDetails.getNumberOfPlots().toString());
            field.setObservation_level(observationLevel);

            // Get our host url
            if (BrAPIService.getHostUrl(context) != null) {
                field.setExp_source(BrAPIService.getHostUrl(context));
            } else {
                // Return an error notifying user we can't save this field
                return new BrapiControllerResponse(false, "Host is null");
            }

            field.setUnique_id("ObservationUnitDbId");
            field.setPrimary_id(primaryId);
            field.setSecondary_id(secondaryId);

            // Do a pre-check to see if the field exists so we can show an error
            int FieldUniqueStatus = dataHelper.checkFieldNameAndObsLvl(field.getExp_name(), field.getObservation_level());
            if (FieldUniqueStatus != -1) {
                return new BrapiControllerResponse(false, this.notUniqueFieldMessage);
            }

            // Check that there are not duplicate unique ids in the database
            HashMap<String, String> checkMap = new HashMap<>();

            if (studyDetails.getValues().isEmpty()) {
                return new BrapiControllerResponse(false, this.noPlots);
            }

            // Construct our map to check for uniques
            for (List<String> dataRow : studyDetails.getValues()) {
                Integer idColumn = studyDetails.getAttributes().indexOf(observationLevel);
                checkMap.put(dataRow.get(idColumn), dataRow.get(idColumn));
            }

            if (!dataHelper.checkUnique(checkMap)) {
                return new BrapiControllerResponse(false, this.notUniqueIdMessage);
            }


            DataHelper.db.beginTransaction();
            // All checks finished, insert our data.
            int expId = dataHelper.createField(field, studyDetails.getAttributes());

            boolean fail = false;
            String failMessage = "";

            // We want the saving of plots and traits wrap together in a transaction
            // so if they fail, the field can be deleted.
            try {

                int plotId = studyDetails.getAttributes().indexOf("Plot");

                System.out.println("Size of study details: "+studyDetails.getValues().size());

                for (List<String> dataRow : studyDetails.getValues()) {
                    dataHelper.createFieldData(expId, studyDetails.getAttributes(), dataRow);
                    System.out.println("Saving: Attributes: "+studyDetails.getAttributes());
                    System.out.println("Saving: dataRow: "+dataRow);
                }

                // Insert the traits already associated with this study
                for (TraitObject t : studyDetails.getTraits()) {
                    dataHelper.insertTraits(t);
                }

//                for(Observation obs : studyDetails.getObservations()) {
//                    System.out.println("****************************");
//                    System.out.println("Saving: varName: "+obs.getVariableName());
//                    System.out.println("Saving: value: "+obs.getValue());
//                    System.out.println("Saving: studyId: "+obs.getStudyId());
//                    System.out.println("Saving: unitDBId: "+obs.getUnitDbId());
//                    System.out.println("Saving: varDbId: "+obs.getVariableDbId());
//                    System.out.println("Saving: StudyId: "+studyDetails.getStudyDbId());
//                    System.out.println("Saving: expId: "+expId);
//                    TraitObject trait = ObservationVariableDao.Companion.getTraitByName(obs.getVariableName());
////                    System.out.println("SavingL TraitId: "+trait.getId());
//                    dataHelper.setTraitObservations(expId, obs);
//                }

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