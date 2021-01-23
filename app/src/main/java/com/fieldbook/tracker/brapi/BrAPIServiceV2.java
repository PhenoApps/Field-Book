package com.fieldbook.tracker.brapi;

import android.util.Log;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.TraitObject;

import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.phenotype.ImagesApi;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIProgramSingleResponse;
import org.brapi.v2.model.pheno.BrAPIImage;
import org.brapi.v2.model.pheno.response.BrAPIImageListResponse;
import org.brapi.v2.model.pheno.response.BrAPIImageSingleResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BrAPIServiceV2 implements BrAPIService{

    public static String exportTarget = "export";
    static String notUniqueFieldMessage = "not_unique";
    static String notUniqueIdMessage = "not_unique_id";
    private DataHelper dataHelper;
    private ImagesApi imagesApi;
    private StudiesApi studiesApi;
    private ProgramsApi programsApi;
    private TrialsApi trialsApi;
    private ObservationsApi observationsApi;
    private ObservationVariablesApi traitsApi;
    private String brapiBaseURL;

    public BrAPIServiceV2(String brapiBaseURL, DataHelper dataHelper) {
        this.dataHelper = dataHelper;
        this.brapiBaseURL = brapiBaseURL;

        BrAPIClient apiClient = new BrAPIClient(brapiBaseURL);

        // Make timeout longer. Set it to 60 seconds for now
        apiClient.setReadTimeout(60000);

        this.imagesApi = new ImagesApi(apiClient);
        this.studiesApi = new StudiesApi(apiClient);
        this.programsApi = new ProgramsApi(apiClient);
        this.trialsApi = new TrialsApi(apiClient);
        this.traitsApi = new ObservationVariablesApi(apiClient);
        this.observationsApi = new ObservationsApi(apiClient);
    }

    public String getHostUrl(String brapiURL) {

        try {
            URL externalUrl = new URL(brapiURL);
            return externalUrl.getHost();
        } catch (MalformedURLException e) {
            Log.e("error-ghu", e.toString());
            return null;
        }
    }

    public void postImageMetaData(com.fieldbook.tracker.brapi.Image image, String brapiToken,
                                  final Function<BrAPIImage, Object> function,
                                  final Function<Integer, Void> failFunction) {

        try {
            BrapiV2ApiCallBack<BrAPIImageListResponse> callback = new BrapiV2ApiCallBack<BrAPIImageListResponse>() {
                @Override
                public void onSuccess(BrAPIImageListResponse imageResponse, int i, Map<String, List<String>> map) {
                    final BrAPIImage response = imageResponse.getResult();
                    function.apply(response);
                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    final ApiException error = e;
                    Integer code = new Integer(error.getCode());
                    failFunction.apply(code);
                }
            };

            BrAPIImage request = mapImage(image);
            imagesApi.getApiClient().authenticate(token -> brapiToken);
            imagesApi.imagesPostAsync(request, callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    private BrAPIImage mapImage(com.fieldbook.tracker.brapi.Image image) {
        BrAPIImage request = new BrAPIImage();
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

    public void putImageContent(com.fieldbook.tracker.brapi.Image image, String brapiToken,
                                final Function<BrAPIImage, Void> function,
                                final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIImageSingleResponse> callback = new BrapiV2ApiCallBack<BrAPIImageSingleResponse>() {
                @Override
                public void onSuccess(BrAPIImageSingleResponse imageResponse, int i, Map<String, List<String>> map) {

                    final BrAPIImage response = imageResponse.getResult();
                    function.apply(response);

                }

                @Override
                public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                    final ApiException error = e;
                    Integer code = new Integer(error.getCode());
                    failFunction.apply(code);
                }
            };

            imagesApi.getApiClient().authenticate(token -> brapiToken);
            imagesApi.imagesImageDbIdImagecontentPutAsync(image.getDbId(), image.getImageData(), callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    public void putImage(com.fieldbook.tracker.brapi.Image image, String brapiToken,
                         final Function<BrAPIImage, Void> function,
                         final Function<Integer, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<BrAPIImageSingleResponse> callback = new BrapiV2ApiCallBack<BrAPIImageSingleResponse>() {
                @Override
                public void onSuccess(BrAPIImageSingleResponse imageResponse, int i, Map<String, List<String>> map) {

                    //function.apply(imageResponse.getresult());
                    function.apply(imageResponse.getResult());

                }

                @Override
                public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                    // report failure
                    final ApiException error = e;
                    Integer code = new Integer(error.getCode());
                    failFunction.apply(code);
                }
            };

            BrAPIImage request = mapImage(image);
            imagesApi.getApiClient().authenticate(token -> brapiToken);
            imagesApi.imagesImageDbIdPutAsync(image.getDbId(), request, callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    public void getPrograms(final String brapiToken,
                            final Function<List<BrapiProgram>, Void> function,
                            final Function<ApiException, Void> failFunction) {
       try {
           BrapiV2ApiCallBack<BrAPIProgramListResponse> callback = new BrapiV2ApiCallBack<BrAPIProgramListResponse>() {
               @Override
               public void onSuccess(BrAPIProgramListResponse programsResponse, int i, Map<String, List<String>> map) {
                   List<BrAPIProgram> programList = programsResponse.getResult().getData();
                   function.apply(mapPrograms(programList));
               }

               @Override
               public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                   failFunction.apply(error);
               }
           };
           ProgramQueryParams queryParams = new ProgramQueryParams();
           queryParams.page(0).pageSize(1000);
           programsApi.getApiClient().authenticate(token -> brapiToken);
           programsApi.programsGetAsync(queryParams, callback);
       } catch (ApiException e) {
           e.printStackTrace();
       }
    }

    private List<BrapiProgram> mapPrograms(List<BrAPIProgram> programList) {
        List<BrapiProgram> brapiPrograms = new ArrayList<>();
        if (programList != null) {
            for (BrAPIProgram program : programList) {
                BrapiProgram brapiProgram = new BrapiProgram();
                String name = program.getProgramName();
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

    public void getTrials(final String brapiToken, String programDbId, final Function<List<BrapiTrial>, Void> function, final Function<ApiException, Void> failFunction) {
        try {
            BrapiV2ApiCallBack<TrialsResponse> callback = new BrapiV2ApiCallBack<TrialsResponse>() {
                @Override
                public void onSuccess(TrialsResponse trialsResponse, int i, Map<String, List<String>> map) {
                    List<TrialSummary> trialList = trialsResponse.getResult().getData();
                    function.apply(mapTrials(trialList));
                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    failFunction.apply(error);
                }
            };
            trialsApi.trialsGetAsync(null, programDbId, null, null, null, null,
                    0, 1000, brapiToken, callback);
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

    public void getStudies(final String brapiToken, String programDbId, String trialDbId, final Function<List<BrapiStudySummary>, Void> function, final Function<ApiException, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<StudiesResponse> callback = new BrapiV2ApiCallBack<StudiesResponse>() {
                @Override
                public void onSuccess(StudiesResponse studiesResponse, int i, Map<String, List<String>> map) {
                    final List<BrapiStudySummary> studies = new ArrayList<>();
                    final List<StudySummary> studySummaryList = studiesResponse.getResult().getData();
                    for (StudySummary studySummary : studySummaryList) {
                        studies.add(mapStudy(studySummary));
                    }

                    function.apply(studies);

                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {

                    // Close our current study and report failure
                    failFunction.apply(error);

                }
            };

            studiesApi.studiesGetAsync(
                    null, null, null, programDbId,
                    null, null, trialDbId, null, null,
                    null, null, null, null,
                    0, 1000, brapiToken, callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    private BrapiStudySummary mapStudy(StudySummary studySummary) {
        BrapiStudySummary study = new BrapiStudySummary();
        study.setStudyDbId(studySummary.getStudyDbId());
        study.setStudyName(studySummary.getStudyName());
        return study;
    }

    public void getStudyDetails(final String brapiToken, final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<ApiException, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<StudyResponse> callback = new BrapiV2ApiCallBack<StudyResponse>() {
                @Override
                public void onSuccess(StudyResponse studyResponse, int i, Map<String, List<String>> map) {

                    final BrapiStudyDetails study = mapStudy(studyResponse.getResult());
                    function.apply(study);

                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    // Close our current study and report failure
                    failFunction.apply(error);

                }
            };

            studiesApi.studiesStudyDbIdGetAsync(
                    studyDbId, brapiToken, callback);

        } catch (ApiException e) {
            failFunction.apply(e);
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

    public void getPlotDetails(final String brapiToken, final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<ApiException, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<ObservationUnitsResponse1> callback = new BrapiV2ApiCallBack<ObservationUnitsResponse1>() {
                @Override
                public void onSuccess(ObservationUnitsResponse1 response, int i, Map<String, List<String>> map) {
                    final BrapiStudyDetails study = new BrapiStudyDetails();
                    study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                    study.setAttributes(mapAttributes(response.getResult().getData().get(0)));
                    study.setValues(mapAttributeValues(study.getAttributes(), response.getResult().getData()));

                    function.apply(study);

                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    // Close our current study and report failure
                    failFunction.apply(error);

                }

            };

            studiesApi.studiesStudyDbIdObservationunitsGetAsync(
                    studyDbId, "plot", 0, 1000,
                    brapiToken, callback);

        } catch (ApiException e) {
            failFunction.apply(e);
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

    public void getOntology(final String brapiToken, Integer page, Integer pageSize, final Function<BrapiListResponse<TraitObject>, Void> function, final Function<ApiException, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<ObservationVariablesResponse> callback = new BrapiV2ApiCallBack<ObservationVariablesResponse>() {
                @Override
                public void onSuccess(ObservationVariablesResponse response, int i, Map<String, List<String>> map) {

                    // Result contains a list of observation variables
                    List<ObservationVariable> brapiTraitList = response.getResult().getData();
                    final Metadata metadata = response.getMetadata();
                    final List<TraitObject> traitsList = mapTraits(brapiTraitList);

                    // Check if our traits list was processed correctly. Right now, will be null if host not found.
                    if (traitsList == null) {
                        failFunction.apply(new ApiException("Could not assign host url to new data."));
                    }

                    final BrapiListResponse<TraitObject> traitResponse = new BrapiListResponse<>();
                    traitResponse.setData(traitsList);
                    traitResponse.setMetadata(metadata);

                    function.apply(traitResponse);

                }

                @Override
                public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
                    // Close our current study and report failure
                    failFunction.apply(error);
                }

            };

            // Set defaults for page and pageSize if not specified.
            if (page == null) {
                page = 0;
            }
            if (pageSize == null) {
                pageSize = 50;
            }

            traitsApi.variablesGetAsync(page, pageSize, brapiToken, null,
                    null, callback);

        } catch (ApiException e) {
            Log.e("error-go", e.toString());
            failFunction.apply(e);
        }
    }

    public void postPhenotypes(List<Observation> observations, String brapiToken,
                               final Function<List<NewObservationDbIdsObservations>, Void> function,
                               final Function<Integer, Void> failFunction) {

        try {

            BrapiV2ApiCallBack<NewObservationDbIdsResponse> callback = new BrapiV2ApiCallBack<NewObservationDbIdsResponse>() {
                @Override
                public void onSuccess(NewObservationDbIdsResponse phenotypesResponse, int i, Map<String, List<String>> map) {

                    final List<NewObservationDbIdsObservations> observationDbIds = phenotypesResponse.getResult().getObservations();
                    function.apply(observationDbIds);

                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {

                    final ApiException error = e;
                    Integer code = new Integer(error.getCode());
                    failFunction.apply(code);

                }
            };

            PhenotypesRequest request = new PhenotypesRequest();

            // TODO: group by study and observationunit db ids
            for (Observation observation : observations) {
                PhenotypesRequestObservation request_observation = new PhenotypesRequestObservation();
                request_observation.setCollector(observation.getCollector().trim());
                request_observation.setObservationDbId(""); // new entry only for post
                request_observation.setObservationTimeStamp(observation.getTimestamp());
                request_observation.setObservationVariableDbId(observation.getVariableDbId());
                request_observation.setObservationVariableName(observation.getVariableName());
                request_observation.season("Spring 2018"); // workaround for test server
                request_observation.setValue(observation.getValue());

                PhenotypesRequestData request_data = new PhenotypesRequestData();
                request_data.addObservationsItem(request_observation);
                request_data.setObservationUnitDbId(observation.getUnitDbId());
                request_data.setStudyDbId(observation.getStudyId());

                request.addDataItem(request_data);
            }

            phenotypesApi.phenotypesPostAsync(request, null, brapiToken, callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    // will only ever have one study in current architecture
    public void putObservations(List<Observation> observations, String brapiToken,
                                final Function<List<NewObservationDbIdsObservations>, Void> function,
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

            BrapiV2ApiCallBack<NewObservationDbIdsResponse> callback = new BrapiV2ApiCallBack<NewObservationDbIdsResponse>() {
                @Override
                public void onSuccess(NewObservationDbIdsResponse observationsResponse, int i, Map<String, List<String>> map) {

                    final List<NewObservationDbIdsObservations> observationDbIds = observationsResponse.getResult().getObservations();
                    function.apply(observationDbIds);

                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {

                    final ApiException error = e;
                    Integer code = new Integer(error.getCode());
                    failFunction.apply(code);

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

                observationsApi.studiesStudyDbIdObservationsPutAsync(study, request, brapiToken, callback);
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

    public void getTraits(final String brapiToken, final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<ApiException, Void> failFunction) {
        try {

            BrapiV2ApiCallBack<StudyObservationVariablesResponse> callback = new BrapiV2ApiCallBack<StudyObservationVariablesResponse>() {
                @Override
                public void onSuccess(StudyObservationVariablesResponse response, int i, Map<String, List<String>> map) {
                    final BrapiStudyDetails study = new BrapiStudyDetails();
                    study.setTraits(mapTraits(response.getResult().getData()));

                    function.apply(study);

                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {

                    failFunction.apply(e);

                }
            };
            studiesApi.studiesStudyDbIdObservationvariablesGetAsync(
                    studyDbId, 0, 200,
                    brapiToken, callback);
        } catch (ApiException e) {
            failFunction.apply(e);
            e.printStackTrace();
        }
    }

    private List<TraitObject> mapTraits(List<ObservationVariable> variables) {
        List<TraitObject> traits = new ArrayList<>();
        for (ObservationVariable var : variables) {
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
            if (getHostUrl(this.brapiBaseURL) != null) {
                trait.setTraitDataSource(getHostUrl(this.brapiBaseURL));
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
                    trait.setFormat(convertBrAPIDataType(var.getScale().getDataType().getValue()));
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

    private String buildCategoryList(List<String> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            sb.append(categories.get(j));
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

        try {
            FieldObject field = new FieldObject();
            field.setExp_name(studyDetails.getStudyName());
            field.setExp_alias(studyDetails.getStudyDbId()); //hack for now to get in table alias not used for anything
            field.setExp_species(studyDetails.getCommonCropName());
            field.setCount(studyDetails.getNumberOfPlots().toString());

            // Get our host url
            if (getHostUrl(this.brapiBaseURL) != null) {
                field.setExp_source(getHostUrl(this.brapiBaseURL));
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