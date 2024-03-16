package com.fieldbook.tracker.database;

import android.content.SharedPreferences;
import android.database.DatabaseUtils;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.objects.SearchDialogDataModel;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
import java.util.List;

public class SearchQueryBuilder {
    public static String TICK = "\"";
    private final CollectActivity originActivity;
    private final List<SearchDialogDataModel> dataSet;
    private StringBuilder queryBuilder;

    public SearchQueryBuilder(CollectActivity originActivity, List<SearchDialogDataModel> dataSet) {
        this.originActivity = originActivity;
        this.dataSet = dataSet;
    }


    public String buildSearchQuery(){
        SharedPreferences ep = originActivity.getPreferences();
        queryBuilder = new StringBuilder();

        // Create the sql query based on user selection
        String attributeSelectStatement = "select ObservationUnitProperty.id, ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + ", " + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.PRIMARY_NAME, "") + TICK + "," + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.SECONDARY_NAME, "") + TICK + " from ObservationUnitProperty where ObservationUnitProperty.id is not null";
        String traitSelectStatement = "select ObservationUnitProperty.id, ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + ", " + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.PRIMARY_NAME, "") + TICK + "," + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.SECONDARY_NAME, "") + TICK + " from observation_variables, ObservationUnitProperty, observations where observations.observation_unit_id = ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + " and observations.observation_variable_name = observation_variables.observation_variable_name and observations.observation_variable_field_book_format = observation_variables.observation_variable_field_book_format";

        for (int i = 0; i < dataSet.size(); i++) {

            // Builds the SQL query for the 'i'th user selection
            StringBuilder currentSubQuery = new StringBuilder();

            String column = dataSet.get(i).getAttribute();
            int operator = dataSet.get(i).getImageResourceId();
            String searchText = dataSet.get(i).getText();

            TraitObject traitObject = originActivity.getDatabase().getTraitByName(column);

            boolean isAttribute = false; // Checks if 'column' is an attribute or a trait
            boolean isCategorical = false; // Checks if 'column' is a categorical trait

            if (traitObject == null) {
                isAttribute = true;
                currentSubQuery.append(attributeSelectStatement).append(" and ").append("ObservationUnitProperty." + TICK + column + TICK);
            } else {
                currentSubQuery.append(traitSelectStatement).append(" and ").append("observation_variables.observation_variable_name = " + TICK + column + TICK);

                if (traitObject.getFormat().equals("categorical") || traitObject.getFormat().equals("multicat") || traitObject.getFormat().equals("qualitative")) {
                    isCategorical = true;
                    searchText = encodeCategorical(searchText);
                }
            }

            // This is to prevent crashes when the user uses special characters
            String truncatedSearchText = DatabaseUtils.sqlEscapeString(searchText);

            // Represents the SQL condition based on the operator and search text
            String condition = "";

            // 0: Equals to
            if (operator == R.drawable.ic_tb_equal) {
                if (isAttribute) condition = " = " + truncatedSearchText;
                else condition = " and value = " + truncatedSearchText;
            }

            // 1: Not equals to
            else if (operator == R.drawable.ic_tb_not_equal) {
                if (isAttribute) condition = " != " + truncatedSearchText;
                else condition = " and value != " + truncatedSearchText;
            }

            // 2: Contains
            else if (operator == R.drawable.ic_tb_contains) {
                if (isAttribute)
                    condition = " like " + DatabaseUtils.sqlEscapeString("%" + searchText + "%");
                else {
                    if (!isCategorical)
                        condition = " and observations.value like " + DatabaseUtils.sqlEscapeString("%" + searchText + "%");
                    else {
                        String decodedT = CategoryJsonUtil.Companion.decode(searchText).get(0).getValue();
                        condition = " and observations.value like " + DatabaseUtils.sqlEscapeString("%[{\"label\":\"%" + decodedT + "%\",\"value\":\"%" + decodedT + "%\"}]%");
                    }
                }
            }

            // 3: More than
            else if (operator == R.drawable.ic_tb_greater_than) {
                if (isAttribute) condition = " > " + truncatedSearchText;
                else condition = " and CAST(value AS INTEGER) > " + truncatedSearchText;
            }

            // 4: less than
            else if (operator == R.drawable.ic_tb_less_than) {
                if (isAttribute) condition = " < " + truncatedSearchText;
                else condition = " and CAST(value AS INTEGER) < " + truncatedSearchText;
            }

            currentSubQuery.append(condition);

            if (i == 0) queryBuilder.append(currentSubQuery);
            else intersectQuery(currentSubQuery);
        }
        return queryBuilder.toString();
    }

    public void intersectQuery(StringBuilder query)
    {
        queryBuilder.append(" INTERSECT ").append(query);
    }

    public void unionQuery(String query)
    {
        queryBuilder.append(" UNION ").append(query);
    }

    public void exceptQuery(String query)
    {
        queryBuilder.append(" EXCEPT ").append(query);
    }

    public String encodeCategorical(String t) {
        return CategoryJsonUtil.Companion.encode(new ArrayList<BrAPIScaleValidValuesCategories>() {{
            add(new BrAPIScaleValidValuesCategories().label(t).value(t));
        }});
    }

}
