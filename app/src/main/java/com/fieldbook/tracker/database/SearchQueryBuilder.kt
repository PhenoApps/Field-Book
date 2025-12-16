package com.fieldbook.tracker.database

import android.content.SharedPreferences
import android.database.DatabaseUtils
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.objects.SearchDialogDataModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.CategoryJsonUtil.Companion.decode
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories

class SearchQueryBuilder(
    private val preferences: SharedPreferences,
    private val database: DataHelper
) {

    companion object {
        const val TAG = "SearchQueryBuilder"
    }

    private val queryBuilder: StringBuilder by lazy {
        StringBuilder()
    }

    private val uniqueName by lazy {
        preferences.getString(
            GeneralKeys.UNIQUE_NAME,
            ""
        )
    }

    private val primaryName by lazy {
        preferences.getString(
            GeneralKeys.PRIMARY_NAME,
            ""
        )
    }

    private val secondaryName by lazy {
        preferences.getString(
            GeneralKeys.SECONDARY_NAME,
            ""
        )
    }

    fun buildSearchQuery(dataSet: MutableList<SearchDialogDataModel>): String {

        queryBuilder.clear()

        // Create the sql query based on user selection
        val attributeSelectStatement =
            """
                SELECT OUP.id, OUP."$uniqueName", OUP."$primaryName", OUP."$secondaryName"
                FROM ObservationUnitProperty AS OUP
                WHERE OUP.id IS NOT NULL
            """.trimIndent()

        val traitSelectStatement =
            """
                SELECT OUP.id, OUP."$uniqueName", OUP."$primaryName", OUP."$secondaryName"
                FROM ObservationUnitProperty AS OUP
                JOIN observations AS O ON O.observation_unit_id = OUP."$uniqueName"
                JOIN observation_variables AS V ON O.observation_variable_db_id = V.internal_id_observation_variable
            """.trimIndent()

        for (i in dataSet.indices) {

            // Builds the SQL query for the 'i'th user selection
            val currentSubQuery = StringBuilder()

            val model: AttributeAdapter.AttributeModel = dataSet[i].attribute
            val column = model.label
            val operator = dataSet[i].imageResourceId
            var searchText = dataSet[i].text

            val traitObject = model.trait?.id?.let { id -> database.getTraitById(id) }

            var isAttribute = false // Checks if 'column' is an attribute or a trait
            var isCategorical = false // Checks if 'column' is a categorical trait

            if (traitObject == null) {
                isAttribute = true
                currentSubQuery.append(attributeSelectStatement).append(" AND ")
                    .append("OUP.\"$column\"")
            } else {
                currentSubQuery.append(traitSelectStatement).append(" AND ")
                    .append("V.observation_variable_name = \"$column\"")

                if (traitObject.format == "categorical" || traitObject.format == "qualitative") {
                    isCategorical = true
                    searchText = encodeCategorical(searchText)
                }
            }

            // This is to prevent crashes when the user uses special characters
            val truncatedSearchText = DatabaseUtils.sqlEscapeString(searchText)

            // Represents the SQL condition based on the operator and search text
            var condition = ""

            // 0: Equals to
            if (operator == R.drawable.ic_tb_equal) {
                condition = if (isAttribute) " = $truncatedSearchText"
                else " AND value = $truncatedSearchText"
            } else if (operator == R.drawable.ic_tb_not_equal) {
                condition = if (isAttribute) " != $truncatedSearchText"
                else " AND value != $truncatedSearchText"
            } else if (operator == R.drawable.ic_tb_contains) {
                if (isAttribute) condition =
                    " LIKE " + DatabaseUtils.sqlEscapeString("%$searchText%")
                else {
                    if (!isCategorical) condition =
                        " AND O.value LIKE " + DatabaseUtils.sqlEscapeString("%$searchText%")
                    else {
                        val decodedT = decode(searchText)[0].value
                        condition =
                            " AND O.value LIKE " + DatabaseUtils.sqlEscapeString("%[{\"label\":\"%$decodedT%\",\"value\":\"%$decodedT%\"}]%")
                    }
                }
            } else if (operator == R.drawable.ic_tb_greater_than) {
                condition = if (isAttribute) " > $truncatedSearchText"
                else " AND CAST(value AS INTEGER) > $truncatedSearchText"
            } else if (operator == R.drawable.ic_tb_less_than) {
                condition = if (isAttribute) " < $truncatedSearchText"
                else " AND CAST(value AS INTEGER) < $truncatedSearchText"
            }

            currentSubQuery.append(condition)

            if (i == 0) queryBuilder.append(currentSubQuery)
            else intersectQuery(currentSubQuery)
        }

        Log.d(TAG, "Query: $queryBuilder")

        return queryBuilder.toString()
    }

    fun intersectQuery(query: StringBuilder?) {
        queryBuilder.append(" INTERSECT ").append(query)
    }

    fun encodeCategorical(t: String?): String {
        return CategoryJsonUtil.Companion.encode(object :
            ArrayList<BrAPIScaleValidValuesCategories?>() {
            init {
                add(BrAPIScaleValidValuesCategories().label(t).value(t))
            }
        } as ArrayList<BrAPIScaleValidValuesCategories>)
    }
}
