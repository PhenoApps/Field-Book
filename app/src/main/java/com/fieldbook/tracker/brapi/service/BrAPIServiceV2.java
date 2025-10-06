package com.fieldbook.tracker.brapi.service;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.brapi.ApiError;
import com.fieldbook.tracker.brapi.ApiErrorCode;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiProgram;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.BrapiTrial;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.brapi.service.core.CropService;
import com.fieldbook.tracker.brapi.service.core.ProgramService;
import com.fieldbook.tracker.brapi.service.core.SeasonService;
import com.fieldbook.tracker.brapi.service.core.StudyService;
import com.fieldbook.tracker.brapi.service.core.TrialService;
import com.fieldbook.tracker.brapi.service.germ.GermplasmService;
import com.fieldbook.tracker.brapi.service.pheno.ObservationUnitService;
import com.fieldbook.tracker.brapi.service.pheno.ObservationVariableService;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.dao.ObservationUnitDao;
import com.fieldbook.tracker.database.dao.ObservationVariableDao;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.ImportFormat;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.FailureFunction;
import com.fieldbook.tracker.utilities.SuccessFunction;
import com.fieldbook.tracker.utilities.SynonymsUtil;
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
import org.brapi.client.v2.modules.core.CommonCropNamesApi;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.SeasonsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.phenotype.ImagesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.BrAPISearchRequestParametersPaging;
import org.brapi.v2.model.TimeAdapter;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.core.response.BrAPIStudySingleResponse;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIGermplasmSynonyms;
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
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponseResult;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.OffsetDateTime;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class BrAPIServiceV2 extends AbstractBrAPIService implements BrAPIService {

    public static final String ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES = "observationLevelNames";
    protected final StudiesApi studiesApi;
    protected final ProgramsApi programsApi;
    protected final TrialsApi trialsApi;
    //used to identify field book db id in external references
    private final String fieldBookReferenceSource = "Field Book Upload";
    private final Context context;
    private final BrAPIClient apiClient;
    private final ImagesApi imagesApi;
    private final ObservationsApi observationsApi;
    private final ObservationUnitsApi observationUnitsApi;
    private final GermplasmApi germplasmApi;
    private final ObservationVariablesApi traitsApi;
    private final SeasonsApi seasonsApi;
    private final CommonCropNamesApi cropsApi;

    public final ProgramService programService;
    public final SeasonService seasonService;
    public final CropService cropService;
    public final TrialService trialService;
    public final StudyService studyService;
    public final ObservationVariableService observationVariableService;
    public final ObservationUnitService observationUnitService;
    public final GermplasmService germplasmService;

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
        this.seasonsApi = new SeasonsApi(apiClient);
        this.cropsApi = new CommonCropNamesApi(apiClient);

        this.programService = new ProgramService.Default(this.programsApi);
        this.studyService = new StudyService.Default(this.studiesApi);
        this.seasonService = new SeasonService.Default(this.seasonsApi);
        this.cropService = new CropService.Default(this.cropsApi);
        this.trialService = new TrialService.Default(this.trialsApi);
        this.observationVariableService = new ObservationVariableService.Default(this.traitsApi);
        this.observationUnitService = new ObservationUnitService.Default(this.observationUnitsApi);
        this.germplasmService = new GermplasmService.Default(this.germplasmApi);
    }

    @Override
    public void authorizeClient() {
        try {
            apiClient.authenticate(t -> PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PreferenceKeys.BRAPI_TOKEN, null));
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

    private void updatePageInfo(BrapiPaginationManager paginationManager, BrAPIMetadata metadata) {
        if (paginationManager.getContext() != null) { //null check for JUnits
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
            imagesApi.imagesPostAsync(Collections.singletonList(request), callback);

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
        if (image.getImageFileSize() != null) request.setFileSize(image.getImageFileSize());
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
                brapiProgram.programName = program.getProgramName();
                brapiProgram.programDbId = program.getProgramDbId();
                brapiProgram.programType = program.getProgramType();
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
                brapiTrial.trialName = name;
                brapiTrial.trialDbId = trial.getTrialDbId();
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
        studyDetails.setTrialName(study.getTrialName());
        return studyDetails;
    }

    public void getPlotDetails(final String studyDbId,
                               BrapiObservationLevel observationLevel, final Function<BrapiStudyDetails, Void> function,
                               final Function<Integer, Void> failFunction) {
        try {
            final Integer pageSize = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50"));
            final BrapiStudyDetails study = new BrapiStudyDetails();
            study.setAttributes(new ArrayList<>());
            study.setValues(new ArrayList<>());
            List<BrAPIObservationUnit> allAttributeValues = new ArrayList<>();
            List<String> allGermplasmDbIds = new ArrayList<>();

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
                    if (page == 0) {
                        //one time code
                        study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                    }

                    Optional.ofNullable(response.getResult())
                            .map(BrAPIObservationUnitListResponseResult::getData)
                            .ifPresent(allAttributeValues::addAll);

                    queryParams.page(queryParams.page() + 1);

                    // Extract germplasmDbIds from observationUnit info
                    List<String> germplasmDbIds = response.getResult().getData().stream()
                            .filter(unit -> unit.getGermplasmDbId() != null)
                            .map(BrAPIObservationUnit::getGermplasmDbId)
                            .collect(Collectors.toList());
                    allGermplasmDbIds.addAll(germplasmDbIds);

                    // Stop after 50 iterations (for safety)
                    // Stop if the current page is the last page according to the server
                    // Stop if there are no more contents
                    if ((queryParams.page() > 50)
                            || (page >= (response.getMetadata().getPagination().getTotalPages() - 1))
                            || (response.getResult().getData().isEmpty())) {
                        List<String> uniqueDbIds = new ArrayList<>(new HashSet<>(allGermplasmDbIds));
                        Map<String, BrAPIGermplasm> data = getGermplasmDetails(uniqueDbIds, failFunction);
                        mapAttributeValues(study, allAttributeValues, data);
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

    private void mapAttributeValues(BrapiStudyDetails study, List<BrAPIObservationUnit> data, @Nullable Map<String, BrAPIGermplasm> germplasmDetailsMap) {

        Map<String, Map<String, String>> unitAttributes = new LinkedHashMap<>(); // Map to store attributes for each unit
        Log.d("BrAPIServiceV2", "Mapping attribute values for " + data.size() + " observation units.");

        for (BrAPIObservationUnit unit : data) {

            String unitDbId = unit.getObservationUnitDbId();
            // Create the unit's attributes hashmap
            Map<String, String> attributesMap = unitAttributes.computeIfAbsent(unitDbId, k -> new LinkedHashMap<>());

            if (unit.getGermplasmName() != null) {
                attributesMap.put("Germplasm", unit.getGermplasmName());
            }

            BrAPIObservationUnitPosition pos = unit.getObservationUnitPosition();
            if (pos != null) {

                List<BrAPIObservationUnitLevelRelationship> levels = pos.getObservationLevelRelationships();
                levels.add(pos.getObservationLevel());

                for (BrAPIObservationUnitLevelRelationship level : levels) {
                    if (level.getLevelName() != null) {
                        String attributeName = level.getLevelName();
                        attributeName = attributeName.substring(0, 1).toUpperCase() + attributeName.substring(1).toLowerCase();
                        attributesMap.put(attributeName, level.getLevelCode());
                    }
                }

                if (pos.getPositionCoordinateX() != null) {
                    String rowColStr = getRowColStr(pos.getPositionCoordinateXType());
                    if (rowColStr == null) {
                        rowColStr = "Row";
                    }
                    attributesMap.put(rowColStr, pos.getPositionCoordinateX());
                }

                if (pos.getPositionCoordinateY() != null) {
                    String rowColStr = getRowColStr(pos.getPositionCoordinateYType());
                    if (rowColStr == null) {
                        rowColStr = "Column";
                    }
                    attributesMap.put(rowColStr, pos.getPositionCoordinateY());
                }

                if (pos.getEntryType() != null && pos.getEntryType().getBrapiValue() != null) {
                    attributesMap.put("EntryType", pos.getEntryType().getBrapiValue());
                }
            }

            if (germplasmDetailsMap != null && unit.getGermplasmDbId() != null) {
                // find matching germplasm in germplasmDetailsMap and extract synonyms and pedigree
                BrAPIGermplasm matchingGermplasm = germplasmDetailsMap.get(unit.getGermplasmDbId());

                if (matchingGermplasm != null) {
                    Log.d("BrAPIServiceV2", "Processing germplasm ID: " + unit.getGermplasmDbId());
                    if (matchingGermplasm.getPedigree() != null) {
                        attributesMap.put("Pedigree", matchingGermplasm.getPedigree());
                    }

                    List<BrAPIGermplasmSynonyms> synonymsList = matchingGermplasm.getSynonyms();
                    if (synonymsList != null) {
                        List<String> processedSynonyms = new ArrayList<>();

                        for (BrAPIGermplasmSynonyms synonymObj : synonymsList) {
                            if (synonymObj != null && synonymObj.getSynonym() != null) {
                                // Escape any double quotes within synonyms
                                String synonym = synonymObj.getSynonym().replace("\"", "\"\"");
                                processedSynonyms.add(synonym);
                            }
                        }

                        if (!processedSynonyms.isEmpty()) {
                            String synonymsString = processedSynonyms.size() > 1 ? String.join("; ", processedSynonyms) : processedSynonyms.get(0);
                            attributesMap.put("Synonyms", synonymsString);
                        }
                    }
                }
            }
            if (unit.getObservationUnitDbId() != null) {
                attributesMap.put("ObservationUnitDbId", unit.getObservationUnitDbId());
            }
            if (unit.getObservationUnitName() != null) {
                attributesMap.put("ObservationUnitName", unit.getObservationUnitName());
            }
        }

        // Extract a list of unique attribute names from the unitAttributes
        Set<String> uniqueAttributes = new LinkedHashSet<>();
        for (Map<String, String> attributesMap : unitAttributes.values()) {
            uniqueAttributes.addAll(attributesMap.keySet());
        }
        study.setAttributes(new ArrayList<>(uniqueAttributes));
        Log.d("BrAPIServiceV2", "Added attributes to the study. Current attributes are: " + study.getAttributes());

        // Assemble the attributesTable
        List<List<String>> attributesTable = new ArrayList<>();
        for (BrAPIObservationUnit unit : data) {
            String unitDbId = unit.getObservationUnitDbId();
            Map<String, String> attributesMap = unitAttributes.get(unitDbId);

            // Create a data row for each unit using the unique attribute names
            List<String> dataRow = new ArrayList<>();
            for (String attr : uniqueAttributes) {
                dataRow.add(attributesMap.getOrDefault(attr, ""));
            }
            attributesTable.add(dataRow);
            Log.d("BrAPIServiceV2", "Added new data row to attributes table: " + dataRow);
        }

        // Save the attributesTable to the study
        study.getValues().addAll(attributesTable);
        Log.d("BrAPIServiceV2", "Updated study with mapped attributes");
    }

    public Map<String, BrAPIGermplasm> getGermplasmDetails(List<String> allGermplasmDbIds, final Function<Integer, Void> failFunction) {
        final Integer pageSize = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50"));
        BrAPIGermplasmSearchRequest germplasmBody = new BrAPIGermplasmSearchRequest();
        List<String> doubledGermplasmDbIds = new ArrayList<>(allGermplasmDbIds);
        doubledGermplasmDbIds.addAll(allGermplasmDbIds);
        germplasmBody.setGermplasmDbIds(doubledGermplasmDbIds);
        germplasmBody.page(0).pageSize(pageSize);
        Log.d("BrAPIServiceV2", "Retrieving germplasm details for " + allGermplasmDbIds.size() + " DB IDs");

        BiConsumer<List<?>, Map<String, BrAPIGermplasm>> germplasmMapper = (data, map) -> {
            data.forEach(item -> {
                if (item instanceof BrAPIGermplasm) {
                    BrAPIGermplasm germplasm = (BrAPIGermplasm) item;
                    map.put(germplasm.getGermplasmDbId(), germplasm);
                }
            });
        };

        return executeBrapiSearch(
                germplasmApi::searchGermplasmPost, // Using lambda for explicit type
                germplasmApi::searchGermplasmSearchResultsDbIdGet, // Using lambda for explicit type
                germplasmBody,
                germplasmMapper,
                failFunction,
                pageSize
        );
    }

    public <T extends BrAPISearchRequestParametersPaging, R, U> Map<String, U> executeBrapiSearch(
            GenericSearchCallFunction<T, R> searchCallFunction,
            GenericSearchCallWithDbIdFunction<R> searchCallWithDbIdFunction,
            T searchRequestBody,
            BiConsumer<List<?>, Map<String, U>> mapper,
            Function<Integer, Void> failFunction,
            final Integer pageSize
    ) {
        Map<String, U> resultMap = new HashMap<>();
        String searchResultsDbId = null;
        Integer currentPage = 0;

        try {
            ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<R>, Optional<BrAPIAcceptedSearchResponse>>> response = searchCallFunction.apply(searchRequestBody);
            validateResponse(response);

            // Process initial response
            Optional<R> listResultOpt = response.getBody().getLeft();
            Optional<BrAPIAcceptedSearchResponse> searchResponseOpt = response.getBody().getRight();

            if (listResultOpt.isPresent()) {
                processResponse(response, currentPage, mapper, resultMap);
                currentPage++;
            } else if (searchResponseOpt.isPresent()) {
                searchResultsDbId = searchResponseOpt.get().getResult().getSearchResultsDbId();
            } else {
                throw new ApiException("Response body is missing result", response.getStatusCode(), response.getHeaders(), null);
            }

            while (searchResultsDbId != null || currentPage > 0) {
                if (searchResultsDbId != null) {
                    response = searchCallWithDbIdFunction.apply(searchResultsDbId, currentPage, pageSize);
                } else {
                    searchRequestBody.setPage(currentPage);
                    response = searchCallFunction.apply(searchRequestBody);
                }

                validateResponse(response);

                boolean hasMore = processResponse(response, currentPage, mapper, resultMap);
                if (!hasMore) break;
                currentPage++;
            }
        } catch (ApiException apiException) {
            Log.e("BrAPIServiceV2", "API Exception: " + apiException.getMessage() +
                    " Code: " + apiException.getCode() +
                    " Endpoint: " + (apiException.getResponseBody() != null ? apiException.getResponseBody() : "Unknown"));

            failFunction.apply(apiException.getCode());
        }

        return resultMap;
    }

    public <T extends BrAPISearchRequestParametersPaging, R, U> Pair<Integer, Map<String, U>> executeBrapiSearchByPage(
            GenericSearchCallFunction<T, R> searchCallFunction,
            GenericSearchCallWithDbIdFunction<R> searchCallWithDbIdFunction,
            T searchRequestBody,
            BiConsumer<List<?>, Map<String, U>> mapper,
            Function<Integer, Void> failFunction
    ) {
        Map<String, U> resultMap = new HashMap<>();
        String searchResultsDbId = null;
        Integer totalPages = 0;

        try {
            ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<R>, Optional<BrAPIAcceptedSearchResponse>>> response = searchCallFunction.apply(searchRequestBody);
            validateResponse(response);

            // Process initial response
            Optional<R> listResultOpt = response.getBody().getLeft();
            Optional<BrAPIAcceptedSearchResponse> searchResponseOpt = response.getBody().getRight();

            BrAPIMetadata metadata = null;

            if (listResultOpt.isPresent()) {
                BrAPIStudyListResponse brApiResponse = (BrAPIStudyListResponse) listResultOpt.get();
                metadata = brApiResponse.getMetadata();
            } else if (searchResponseOpt.isPresent()) {
                metadata = searchResponseOpt.get().getMetadata();
                searchResultsDbId = searchResponseOpt.get().getResult().getSearchResultsDbId();
            } else {
                throw new ApiException("Response body is missing result", response.getStatusCode(), response.getHeaders(), null);
            }

            if (searchResultsDbId != null) {
                response = searchCallWithDbIdFunction.apply(searchResultsDbId, searchRequestBody.getPage(), searchRequestBody.getPageSize());
            } else {
                response = searchCallFunction.apply(searchRequestBody);
            }

            validateResponse(response);

            Optional<R> apiResponse = response.getBody().getLeft();
            if (apiResponse != null && apiResponse.isPresent()) {
                BrAPIStudyListResponse brApiResponse = (BrAPIStudyListResponse) apiResponse.get();
                //metadata = brApiResponse.getMetadata();
                processResponse(response, searchRequestBody.getPage(), mapper, resultMap);
            }

            if (metadata != null && metadata.getPagination() != null) {

                if (metadata.getPagination().getTotalPages() != null) {

                    Log.d("PAGES", "Total Pages: " + metadata.getPagination().getTotalPages());
                    totalPages = metadata.getPagination().getTotalPages();

                }
            }

        } catch (ApiException apiException) {
            Log.e("BrAPIServiceV2", "API Exception: " + apiException.getMessage() +
                    " Code: " + apiException.getCode() +
                    " Endpoint: " + (apiException.getResponseBody() != null ? apiException.getResponseBody() : "Unknown"));

            failFunction.apply(apiException.getCode());
        }

        return new Pair<>(totalPages, resultMap);
    }

    private void validateResponse(ApiResponse<?> response) throws ApiException {
        if (response == null) {
            throw new ApiException("No response received", 0, null, null);
        } else if (response.getBody() == null) {
            throw new ApiException("Response is missing body", response.getStatusCode(), response.getHeaders(), null);
        }
    }

    private <R, U> boolean processResponse(ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<R>, Optional<BrAPIAcceptedSearchResponse>>> response, Integer currentPage, BiConsumer<List<?>, Map<String, U>> mapper, Map<String, U> resultMap) {
        if (response.getBody().getLeft().isPresent()) {
            resultMap.putAll(getListResultAsMap(response, mapper));
            BrAPIResponse listResponse = (BrAPIResponse) response.getBody().getLeft().get();
            return hasMorePages(listResponse, currentPage);
        } else {
            return false;
        }
    }

    private boolean hasMorePages(BrAPIResponse listResponse, Integer currentPage) {
        return listResponse.getMetadata() != null
                && listResponse.getMetadata().getPagination() != null
                && listResponse.getMetadata().getPagination().getCurrentPage() < listResponse.getMetadata().getPagination().getTotalPages() - 1
                && currentPage < listResponse.getMetadata().getPagination().getTotalPages() - 1; // additional check for when server pagination is wrong
    }

    private <R, U> Map<String, U> getListResultAsMap(
            ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<R>, Optional<BrAPIAcceptedSearchResponse>>> response,
            BiConsumer<List<?>, Map<String, U>> mapper) {

        Map<String, U> resultMap = new HashMap<>();

        Optional<R> optionalResponseResult = response.getBody().getLeft();
        optionalResponseResult.ifPresent(responseResult -> {
            BrAPIResponse brApiResponse = (BrAPIResponse) responseResult;
            Object resultObj = brApiResponse.getResult();
            BrAPIResponseResult<?> result = (BrAPIResponseResult<?>) resultObj;

            if (result.getData() != null) {
                List<?> data = result.getData();
                mapper.accept(data, resultMap);
            }
        });

        return resultMap;
    }

    private String getRowColStr(BrAPIPositionCoordinateTypeEnum type) {
        if (null != type) {
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
     *
     * @param studyDbId
     * @param observationVariableDbIds
     * @param paginationManager
     * @param function
     * @param failFunction
     */
    public void getObservations(final String studyDbId, final List<String> observationVariableDbIds,
                                BrapiPaginationManager paginationManager, final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction) {

        try {
            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse response, int i, Map<String, List<String>> map) {

                    paginationManager.updateTotalPages(response.getMetadata().getPagination().getTotalPages());

                    if (response.getResult() != null) {

                        Map<String, String> extVariableDbIdMap = getExtVariableDbIdMapping();
                        // Result contains a list of observation variables
                        List<BrAPIObservation> brapiObservationList = response.getResult().getData();
                        final List<Observation> observationList = mapObservations(brapiObservationList, extVariableDbIdMap, observationVariableDbIds);

                        function.apply(observationList);

                        System.out.println("TotalNumber of Observation records: " + response.getMetadata().getPagination().getTotalCount());

                        //Slide pagingation up 1 this is handled within function
                        paginationManager.moveToNextPage();

                        //Check if next recursion is valid
                        if (paginationManager.getPage() < paginationManager.getTotalPages()) {
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

    private Map<String, String> getExtVariableDbIdMapping() {
        List<TraitObject> traits = ObservationVariableDao.Companion.getAllTraitObjects();
        Map<String, String> externalIdToInternalMap = new HashMap<>();
        for (TraitObject trait : traits) {
            String dbId = trait.getId();
            externalIdToInternalMap.put(trait.getExternalDbId(), dbId);
        }
        return externalIdToInternalMap;
    }

    private Map<String, String> getExtUnitDbIdMapping() {
        ObservationUnitModel[] observationUnits = ObservationUnitDao.Companion.getAll();
        Map<String, String> externalIdToInternalMap = new HashMap<>();
        for (ObservationUnitModel model : observationUnits) {
            externalIdToInternalMap.put(model.getObservation_unit_db_id(), String.valueOf(model.getInternal_id_observation_unit()));
        }
        return externalIdToInternalMap;
    }

    /**
     * Function to map the observations from Brapi to the Fieldbook Observation variable.
     *
     * @param brapiObservationList
     * @return list of Fieldbook Observation objects
     */
    private List<Observation> mapObservations(List<BrAPIObservation> brapiObservationList, Map<String, String> extVariableDbIdMap, List<String> validVariableDbIds) {
        List<Observation> outputList = new ArrayList<>();
        for (BrAPIObservation brapiObservation : brapiObservationList) {

            if (!validVariableDbIds.contains(brapiObservation.getObservationVariableDbId())) {
                continue;
            }

            Observation newObservation = new Observation();
            newObservation.setStudyId(brapiObservation.getStudyDbId());
            newObservation.setVariableName(brapiObservation.getObservationVariableName());
            newObservation.setDbId(brapiObservation.getObservationDbId());

            String internalUnitId = brapiObservation.getObservationUnitDbId();//extUnitDbIdMap.getOrDefault(brapiObservation.getObservationUnitDbId(), null);
            newObservation.setUnitDbId(internalUnitId);

            //need to get out the internal observation variable DB ID or else we will store the wrong thing in the table
            String internalVarId = extVariableDbIdMap.getOrDefault(brapiObservation.getObservationVariableDbId(), null);
            newObservation.setVariableDbId(internalVarId);

            newObservation.setValue(brapiObservation.getValue());

            if (brapiObservation.getObservationTimeStamp() != null) {
                newObservation.setTimestamp(TimeAdapter.convertFrom(brapiObservation.getObservationTimeStamp()));
            }

            newObservation.setLastSyncedTime(OffsetDateTime.now()); // Use current time as sync time

            //search imported obs references for first field book id
            List<BrAPIExternalReference> references = brapiObservation.getExternalReferences();
            if (references != null && !references.isEmpty()) {
                for (BrAPIExternalReference ref : references) {
                    String source = ref.getReferenceSource();
                    if (source != null && source.equals(fieldBookReferenceSource)) {
                        String id = ref.getReferenceID();
                        if (id != null && !id.isEmpty()) {
                            newObservation.setFieldBookDbId(id);
                            break;
                        }

                        String refId = ref.getReferenceId();
                        if (refId != null && !refId.isEmpty()) {
                            newObservation.setFieldBookDbId(refId);
                            break;
                        }
                    }
                }
            }

            //Make sure we are on the right experiment level.
            // This will cause bugs if there have been plot and plant level traits found as the observations retrieves all of them
            if (internalUnitId != null) {
                outputList.add(newObservation);
            }

        }

        return outputList;
    }

    public void createObservations(List<Observation> observations,
                                   final Function<List<Observation>, Void> function,
                                   final Function<Integer, Void> failFunction) {

        ArrayList<String> validObservationVariableDbIds = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.getVariableDbId() != null && !observation.getVariableDbId().isEmpty()) {
                validObservationVariableDbIds.add(observation.getVariableDbId());
            }
        }

        try {
            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse phenotypesResponse, int i, Map<String, List<String>> map) {

                    try {

                        List<Observation> newObservations = new ArrayList<>();
                        if (phenotypesResponse.getResult() != null && phenotypesResponse.getResult().getData() != null) {
                            newObservations.addAll(
                                    mapObservations(
                                            phenotypesResponse.getResult().getData(),
                                            getExtVariableDbIdMapping(),
                                            validObservationVariableDbIds
                                    )
                            );
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

            ArrayList<String> validObservationVariableDbIds = new ArrayList<>();
            for (Observation observation : observations) {
                if (observation.getVariableDbId() != null && !observation.getVariableDbId().isEmpty()) {
                    validObservationVariableDbIds.add(observation.getVariableDbId());
                }
            }

            BrapiV2ApiCallBack<BrAPIObservationListResponse> callback = new BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                @Override
                public void onSuccess(BrAPIObservationListResponse observationsResponse, int i, Map<String, List<String>> map) {
                    try {
                        List<Observation> newObservations = new ArrayList<>();
                        if (observationsResponse.getResult() != null && observationsResponse.getResult().getData() != null) {
                            newObservations.addAll(
                                    mapObservations(
                                            observationsResponse.getResult().getData(),
                                            getExtVariableDbIdMapping(),
                                            validObservationVariableDbIds
                                    )
                            );
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

    private BrAPIObservation convertToBrAPIObservation(Observation observation) {
        BrAPIObservation newObservation = new BrAPIObservation();
        String collectorName = observation.getCollector();

        newObservation.setCollector(collectorName != null ? collectorName.trim() : "");
        newObservation.setObservationDbId(observation.getDbId());
        newObservation.setObservationTimeStamp(TimeAdapter.convertFrom(observation.getTimestamp()));
        newObservation.setObservationUnitDbId(observation.getUnitDbId());
        newObservation.setStudyDbId(observation.getStudyId());
        newObservation.setObservationVariableDbId(observation.getVariableDbId());
        newObservation.setObservationVariableName(observation.getVariableName());
        newObservation.setValue(observation.getValue());

        BrAPIExternalReference reference = new BrAPIExternalReference();
        reference.setReferenceId(observation.getFieldbookDbId());
        reference.setReferenceID(observation.getFieldbookDbId()); // Keep obsolete referenceID
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
                    if ((recursiveCounter[0] > 50)
                            || (page >= (response.getMetadata().getPagination().getTotalPages() - 1))
                            || (response.getResult().getData().size() == 0)) {
                        // Stop recursive loop
                        function.apply(study);
                    } else {
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
        int positionCount = 1;
        for (BrAPIObservationVariable var : variables) {

            TraitObject trait = new TraitObject();

            if (var.getDefaultValue() != null) {
                trait.setDefaultValue(var.getDefaultValue());
            }

            // Get the synonyms for easier reading. Set it as the trait name.
            String name = var.getObservationVariableName();
            trait.setName(name);
            trait.setAlias(name);

            List<String> brapiSynonyms = var.getSynonyms() != null ? var.getSynonyms() : new ArrayList<>();
            trait.setSynonyms(SynonymsUtil.INSTANCE.addAliasToSynonyms(name, brapiSynonyms));

            //v5.1.0 bugfix branch update, getPrioritizedValue can return null, trait name should never be null
            // Skip the trait if there brapi trait field isn't present
            if (var.getTrait() == null) {
                variablesMissingTrait += 1;
                continue;
            }

            trait.setDetails(var.getTrait().getTraitDescription());
            // Get database id of external system to sync to enabled pushing through brAPI
            trait.setExternalDbId(var.getObservationVariableDbId());

            // Need to set where we are getting the data from so we don't push to a different
            // external link than where the trait was retrieved from.
            if (BrAPIService.getHostUrl(context) != null) {
                String hostUrl = BrAPIService.getHostUrl(context);
                if (hostUrl != null) {
                    trait.setTraitDataSource(hostUrl);
                }
            } else {
                // return null to indicate we couldn't process the traits
                return null;
            }

            // Parse out the scale of the variable
            if (var.getScale() != null) {
                if (var.getScale().getValidValues() != null) {

                    if (var.getScale().getValidValues().getMinimumValue() != null) {
                        trait.setMinimum(var.getScale().getValidValues().getMinimumValue());
                    } else {
                        trait.setMinimum("");
                    }

                    if (var.getScale().getValidValues().getMaximumValue() != null) {
                        trait.setMaximum(var.getScale().getValidValues().getMaximumValue());
                    } else {
                        trait.setMaximum("");
                    }

                    if (var.getScale().getValidValues().getCategories() != null) {
                        trait.setCategories(buildCategoryList(var.getScale().getValidValues().getCategories()));
                        //For categorical traits, include label value pairs in details
                        String details = trait.getDetails() + "\nCategories: ";
                        details += buildCategoryDescriptionString(var.getScale().getValidValues().getCategories());
                        trait.setDetails(details);
                    }

                }
                if (var.getScale().getDataType() != null) {
                    trait.setFormat(convertBrAPIDataType(var.getScale().getDataType().getBrapiValue()));
                } else {
                    trait.setFormat("text");
                }
                //For categorical traits, include label value pairs in details
                if (trait.getFormat().equals("categorical")) {
                    String details = trait.getDetails();

                    if (var.getScale() != null &&
                            var.getScale().getValidValues() != null &&
                            var.getScale().getValidValues().getCategories() != null &&
                            !var.getScale().getValidValues().getCategories().isEmpty()) {

                        List<BrAPIScaleValidValuesCategories> categories = var.getScale().getValidValues().getCategories();
                        details += "\nCategories: " + buildCategoryDescriptionString(categories);
                    }
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
                Type listType = new TypeToken<List<String>>() {
                }.getType();
                trait.setObservationLevelNames(new Gson().fromJson(observationVariableNames, listType));
            }

            // Set some config variables in fieldbook
            trait.setVisible(true);
            trait.setRealPosition(positionCount);
            positionCount++;

            traits.add(trait);
        }

        return Pair.create(traits, variablesMissingTrait);
    }

    private String buildCategoryDescriptionString(List<BrAPIScaleValidValuesCategories> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            sb.append(categories.get(j).getValue() + "=" + categories.get(j).getLabel());
            if (j != categories.size() - 1) {
                sb.append("; ");
            }
        }
        return sb.toString();
    }

    /**
     * Called in map traits, this will build a JSON string that encodes
     * value/label category pairs.
     *
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
        switch (dataType.toLowerCase()) {
            case "nominal":
            case "ordinal":
            case "categorical":
            case "qualitative":
            case "multicat":
                // All Field Book categories are ordered, so this works
                return "categorical";
            case "date":
                return "date";
            case "numerical":
            case "duration":
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
            case "location":
                return "location";
            case "barcode":
                return "barcode";
            case "gnss":
                return "gnss";
            case "zebra label printer":
            case "zebra label print":
                return "zebra label print";
            case "usb camera":
                return "usb camera";
            case "code":
            case "text":
            default:
                return "text";
        }
    }

    public BrapiControllerResponse saveStudyDetails(BrapiStudyDetails studyDetails, BrapiObservationLevel selectedObservationLevel, String primaryId, String secondaryId, String sortOrder) {

        DataHelper dataHelper = new DataHelper(context);

        String observationLevel;
        if (selectedObservationLevel == null) observationLevel = "plot";
        else observationLevel = selectedObservationLevel.getObservationLevelName();
        try {
            FieldObject field = new FieldObject();
            field.setStudyDbId(studyDetails.getStudyDbId());
            field.setName(studyDetails.getStudyName());
            field.setAlias(studyDetails.getStudyName());
            field.setSpecies(studyDetails.getCommonCropName());
            field.setEntryCount(studyDetails.getNumberOfPlots().toString());
            field.setObservationLevel(observationLevel);
            field.setDataSourceFormat(ImportFormat.BRAPI);
            field.setTrialName(studyDetails.getTrialName());
            // Get our host url
            if (BrAPIService.getHostUrl(context) != null) {
                field.setDataSource(BrAPIService.getHostUrl(context));
            } else {
                // Return an error notifying user we can't save this field
                return new BrapiControllerResponse(false, "Host is null");
            }

            field.setUniqueId("ObservationUnitDbId");
            field.setPrimaryId(primaryId);
            field.setSecondaryId(secondaryId);
            field.setSortColumnsStringArray(sortOrder);

            // Do a pre-check to see if the field exists so we can show an error
            int FieldUniqueStatus = dataHelper.checkBrapiStudyUnique(field.getObservationLevel(), field.getStudyDbId());
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
                Integer idColumn = studyDetails.getAttributes().indexOf("ObservationUnitName");
                checkMap.put(dataRow.get(idColumn), dataRow.get(idColumn));
            }

            if (!dataHelper.checkUnique(checkMap)) {
                return new BrapiControllerResponse(false, this.notUniqueIdMessage);
            }


            DataHelper.db.beginTransaction();
            // All checks finished, insert our data.
            int studyId = dataHelper.createField(field, studyDetails.getAttributes(), true);
            field.setStudyId(studyId);

            boolean fail = false;
            String failMessage = "";

            // We want the saving of plots and traits wrap together in a transaction
            // so if they fail, the field can be deleted.
            try {

                int plotId = studyDetails.getAttributes().indexOf("Plot");

                System.out.println("Size of study details: " + studyDetails.getValues().size());

                for (List<String> dataRow : studyDetails.getValues()) {
                    dataHelper.createFieldData(studyId, studyDetails.getAttributes(), dataRow);
                    Log.d("BrAPIServiceV2", "Saving: Attributes: " + studyDetails.getAttributes());
                    Log.d("BrAPIServiceV2", "Saving: dataRow: " + dataRow);
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
//                    System.out.println("Saving: studyId: "+studyId);
//                    TraitObject trait = ObservationVariableDao.Companion.getTraitByName(obs.getVariableName());
////                    System.out.println("SavingL TraitId: "+trait.getId());
//                    dataHelper.setTraitObservations(studyId, obs);
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
                addStudyToGroup(field, dataHelper);
                return new BrapiControllerResponse(true, "", field);
            }


        } catch (Exception e) {
            return new BrapiControllerResponse(false, e.toString());
        }
    }

    /**
     * Assign group_name based on trial_name
     */
    private void addStudyToGroup(FieldObject field, DataHelper dataHelper) {
        if (field.getTrialName() != null && !field.getTrialName().isEmpty()) {
            Integer groupId = dataHelper.createOrGetStudyGroup(field.getTrialName());
            field.setGroupId(groupId);

            dataHelper.updateStudyGroup(field.getStudyId(), groupId);
        }
    }

    @FunctionalInterface
    public interface GenericSearchCallFunction<T, R> {
        ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<R>, Optional<BrAPIAcceptedSearchResponse>>> apply(T searchRequestBody) throws ApiException;
    }

    @FunctionalInterface
    public interface GenericSearchCallWithDbIdFunction<R> {
        ApiResponse<org.apache.commons.lang3.tuple.Pair<Optional<R>, Optional<BrAPIAcceptedSearchResponse>>> apply(String searchResultsDbId, Integer currentPage, Integer pageSize) throws ApiException;
    }
}