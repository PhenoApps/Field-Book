# Field Book Import Test Files

This directory contains test files for validating the import functionality of Field Book.
The expected choices during import are `plot_id` as unique_id, `plot` as primary_id, and `replicate` as secondary_id.
Each file tests different error conditions and validation checks.

## CSV Test Files

### valid_import.csv
- **Purpose**: Baseline test with valid data
- **Expected Behavior**: Successful import with no errors
- 
### missing_unique_id.csv
- **Purpose**: Tests handling of rows with missing unique ID
- **Observed Behavior**: 
  - The import process skips rows with missing `plot_id` values
  - Import continues processing other rows
  - Error is reported at the end of import

### missing_primary_id.csv
- **Purpose**: Tests handling of rows with missing primary ID
- **Observed Behavior**: 
  - The import process skips the row with missing `plot` value (25TEST010_0403)
  - Import continues processing other rows
  - Error is reported at the end of import

### missing_secondary_id_test.csv
- **Purpose**: Tests handling of rows with missing secondary ID
- **Observed Behavior**: 
  - The import process skips the row with missing `replicate` value (25TEST010_0403)
  - Import continues processing other rows
  - Error is reported at the end of import

### duplicate_unique_id.csv
- **Purpose**: Tests handling of duplicate unique identifiers
- **Expected Behavior**:
  - Error: "Duplicate unique identifier '[value]' in column '[column]'"
  - Import should fail

### disallowed_character_in_id.csv
- **Purpose**: Tests handling of disallowed characters in identifier values
- **Expected Behavior**:
  - Error: "Special character '/' found in '[value]' in column '[column]' at line [line number]"
  - Import should fail

### disallowed_character_in_header.csv
- **Purpose**: Tests handling of disallowed characters in column headers
- **Expected Behavior**:
  - Special characters in column headers should be replaced with underscores
  - Import should succeed with modified column names

### duplicate_column_name.csv
- **Purpose**: Tests handling of duplicate column names
- **Expected Behavior**:
  - Warning about duplicate column names
  - Only the first instance of duplicate column names is used
  - Import should succeed if required columns are unique

### empty_column_name.csv
- **Purpose**: Tests handling of empty column names
- **Expected Behavior**:
  - Empty column names should be skipped during import
  - Import should succeed if required columns have names

### empty_column_data.csv
- **Purpose**: Tests handling of empty column data
- **Expected Behavior**:
  - Empty cell values should be imported as empty strings
  - Import should succeed

### missing_rows_and_cells.csv
- **Purpose**: Tests handling of missing rows and cells
- **Expected Behavior**:
  - Empty rows should be skipped
  - Rows with missing required fields should be skipped
  - Import should succeed with valid rows

## Excel-Specific Test Files

### merged_cells.xlsx
- **Purpose**: Tests handling of merged cells in Excel
- **Expected Behavior**:
  - Values from merged cells should be properly extracted
  - Import should handle merged cells gracefully

### unique_id_formula.xlsx
- **Purpose**: Tests handling of formula cells in the unique ID column
- **Expected Behavior**:
  - Formula results should be extracted, not the formula text
  - Import should succeed with evaluated values

## Known Issues and Edge Cases

1. When multiple errors exist in a file, only the first encountered error will be reported.
2. Special characters '/' and '\' are specifically disallowed in unique identifiers.
3. Empty values in required fields (unique ID, primary ID, secondary ID) will cause import to fail.
4. Duplicate column names will be reported but may not prevent import if they're not essential columns.

## How to Test

1. Go to the Field Book app and initiate an import
2. Select one of these test files
3. Observe the error message or successful import
4. Compare with the expected behavior documented here