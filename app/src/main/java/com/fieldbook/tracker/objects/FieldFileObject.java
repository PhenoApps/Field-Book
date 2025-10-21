package com.fieldbook.tracker.objects;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;

import androidx.annotation.Nullable;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.CSVReader;
import com.fieldbook.tracker.utilities.StringUtil;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

//TODO when merged with xlsx edit getColumnSet
public class FieldFileObject {
    public static FieldFileBase create(final Context ctx, final Uri path,
                                       final InputStream inputStream, @Nullable String cloudName) {

        String ext = getExtension(path.toString());
        if (cloudName != null) {
            ext = getExtension(cloudName);
        }

        switch (ext) {
            case "csv":
                return new FieldFileCSV(ctx, path);
            case "xls":
                return new FieldFileExcel(ctx, path);
            case "xlsx":
                return new FieldFileXlsx(ctx, path);
            default:
                return new FieldFileOther(ctx, path);
        }
    }

    public static String getExtension(final String path) {
        final int first = path.lastIndexOf(".") + 1;
        return path.substring(first).toLowerCase();
    }

    public static String getExtensionFromClass(FieldFileBase fieldFile) {

        if (fieldFile instanceof FieldFileCSV) {
            return "csv";
        } else if (fieldFile instanceof FieldFileExcel) {
            return "xls";
        } else if (fieldFile instanceof FieldFileXlsx) {
            return "xlsx";
        } else {
            return "";
        }
    }

    public abstract static class FieldFileBase {
        boolean openFail;
        boolean specialCharactersFail;
        private CharSequence lastErrorMessage = "";
        private final Uri path_;
        private final Context ctx;

        private String name;

        FieldFileBase(final Context ctx, final Uri path) {
            this.ctx = ctx;
            path_ = path;
            openFail = false;
            specialCharactersFail = false;
        }

        protected void setLastError(CharSequence message) {
            this.lastErrorMessage = message;
            Log.e("FieldFileBase", message.toString());
        }

        public CharSequence getLastError() {
            return this.lastErrorMessage;
        }

        // Helper method to check unique values
        protected boolean checkUnique(HashMap<String, String> check, String value, String columnLabel, int rowIndex) {
            String fixFileMessage = ctx.getString(R.string.import_runnable_create_field_fix_file);

            if (check.containsKey(value)) {
                String duplicateErrorMessage = ctx.getString(
                        R.string.import_runnable_create_field_duplicate_unique_identifier, value, columnLabel, + 1
                );
                setLastError(StringUtil.INSTANCE.applyBoldStyleToString(
                        String.format("%s\n\n%s", duplicateErrorMessage, fixFileMessage),
                        value, columnLabel
                ));
                return false;
            }

            for (char specialChar : new char[]{'/', '\\'}) {
                if (value.contains(String.valueOf(specialChar))) {
                    String specialCharErrorMessage = ctx.getString(
                            R.string.import_runnable_create_field_special_character_error, value, columnLabel, rowIndex + 1, specialChar
                    );
                    setLastError(StringUtil.INSTANCE.applyBoldStyleToString(
                            String.format("%s\n\n%s", specialCharErrorMessage, fixFileMessage),
                            value, columnLabel
                    ));
                    specialCharactersFail = true;
                    return false;
                }
            }

            check.put(value, value);
            return true;
        }

        public final InputStream getInputStream() {
            try {
                return this.ctx.getContentResolver().openInputStream(this.path_);
            } catch (IOException io) {
                io.printStackTrace();
            }
            return null;
        }

        public final String getStringPath() { return path_.toString(); }

        public final Uri getPath() {
            return path_;
        }

        public final String getStem() {

            String stem = getFileStem();

            if (stem.contains(".")) {

                int dotIndex = stem.lastIndexOf(".");

                stem = stem.substring(0, dotIndex);

            }

            return stem;
        }

        public final String getFileStem() {
            try {
                final String path = path_.toString();
                //uri separated by query param separator %2F, not encoded with keys
                final String token = "%2F";
                final int tokenSize = token.length();
                final int first = path.lastIndexOf(token) + tokenSize;
                final int last = path.lastIndexOf(".");
                String fileStem = path.substring(first, last);
                if (path_.getScheme().equals("content")) {

                    try (Cursor c = ctx.getContentResolver().query(path_, null, null, null, null)) {

                        if (c != null && c.moveToFirst()) {

                            int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                            if (index > 0) {

                                fileStem = c.getString(index);

                            }
                        }
                    } catch (Exception e) {

                        e.printStackTrace();
                    }

                }
                return fileStem;
            } catch (Exception e) {
                e.printStackTrace();
                return UUID.randomUUID().toString();
            }
        }

        public final boolean hasSpecialCharacters() {
            return specialCharactersFail;
        }

        public FieldObject createFieldObject() {
            FieldObject f = new FieldObject();
            if (name == null) {
                f.setName(this.getStem());
                f.setAlias(this.getStem());
                f.setDataSource(this.getFileStem());
                f.setDataSourceFormat(ImportFormat.fromString(getExtension(this.getFileStem())));
            } else {
                f.setName(name);
                f.setAlias(name);
                f.setDataSource(name + "." + getExtensionFromClass(this));
                f.setDataSourceFormat(ImportFormat.fromString(getExtensionFromClass(this)));
            }
            return f;
        }

        public boolean getOpenFailed() {
            return openFail;
        }

        public void setName(String name) { this.name = name; }

        public String getName() { return name; }

        abstract public boolean isCSV();

        abstract public boolean isExcel();

        abstract public boolean isOther();

        abstract public String[] getColumns();

        abstract public HashMap<String, String> getColumnSet(String unique, int idColPosition);

        // read file
        abstract public void open();

        abstract public String[] readNext();

        abstract public void close();
    }

    public static class FieldFileCSV extends FieldFileBase {
        CSVReader cr;

        FieldFileCSV(final Context ctx, final Uri path) {
            super(ctx, path);
        }

        public boolean isCSV() {
            return true;
        }

        public boolean isExcel() {
            return false;
        }

        public boolean isOther() {
            return false;
        }

        public String[] getColumns() {
            try {
                openFail = false;
                InputStreamReader isr = new InputStreamReader(super.getInputStream());
                CSVReader cr = new CSVReader(isr);
                return cr.readNext();
            } catch (Exception ignore) {
                openFail = true;
                return new String[0];
            }
        }

        public HashMap<String, String> getColumnSet(String columnLabel, int idColPosition) {
            HashMap<String, String> check = new HashMap<>();
            try {
                openFail = false;
                InputStreamReader isr = new InputStreamReader(super.getInputStream());
                CSVReader cr = new CSVReader(isr);
                String[] columns = cr.readNext();
                int rowIndex = 0;

                while (columns != null) {
                    columns = cr.readNext();
                    rowIndex++;
                    if (columns != null) {
                        if (!checkUnique(check, columns[idColPosition], columnLabel, rowIndex)) {
                            close();
                            return null; // Return null to indicate an error has occurred
                        }
                    }
                }
            } catch (Exception e) {
                openFail = true;
                e.printStackTrace();
                setLastError("Failed to process file: " + e.getMessage());
                return null;
            } finally {
                close();
            }
            return check;
        }

        public void open() {
            try {
                openFail = false;
                InputStreamReader isr = new InputStreamReader(super.getInputStream());
                cr = new CSVReader(isr);
            } catch (Exception e) {
                openFail = true;
                e.printStackTrace();
            }
        }

        public String[] readNext() {
            try {
                return cr.readNext();
            } catch (Exception e) {
                openFail = true;
                e.printStackTrace();
                return new String[0];
            }
        }

        public void close() {
            try {
                cr.close();
            } catch (Exception e) {
                openFail = true;
                e.printStackTrace();
            }
        }
    }

    public static class FieldFileExcel extends FieldFileBase {
        private Workbook wb;
        private int current_row;

        DataFormatter formatter = new DataFormatter();

        FieldFileExcel(final Context ctx, final Uri path) {
            super(ctx, path);
        }

        public boolean isCSV() {
            return false;
        }

        public boolean isExcel() {
            return true;
        }

        public boolean isOther() {
            return false;
        }

        public String[] getColumns() {
            try {
                openFail = false;
                InputStream is = super.getInputStream();
                if (is != null) {
                    wb = new XSSFWorkbook(is);
                    Sheet sheet = wb.getSheetAt(0);
                    Row headerRow = sheet.getRow(0);
                    if (headerRow == null) return new String[0];

                    String[] importColumns = new String[headerRow.getLastCellNum()];
                    for (int cn = 0; cn < headerRow.getLastCellNum(); cn++) {
                        Cell cell = headerRow.getCell(cn, Row.RETURN_BLANK_AS_NULL);
                        importColumns[cn] = (cell == null) ? "" : formatter.formatCellValue(cell);
                    }
                    return importColumns;
                }
            } catch (Exception ignore) {
                openFail = true;
            }
            return new String[0];
        }

        @Override
        public HashMap<String, String> getColumnSet(String columnLabel, int idColPosition) {
            HashMap<String, String> check = new HashMap<>();
            try {
                open();
                Sheet sheet = wb.getSheetAt(0);
                int totalRows = sheet.getLastRowNum();

                for (int rowIndex = 0; rowIndex <= totalRows; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    Cell cell = row.getCell(idColPosition, Row.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell);

                    if (value.isEmpty() && isRowEmpty(row)) {
                        continue; // Skip the row if the specific cell is empty and the whole row is empty
                    }

                    if (!checkUnique(check, value, columnLabel, rowIndex)) {
                        return null;
                    }
                }
            } catch (Exception e) {
                setLastError("Failed to process Excel file: " + e.getMessage());
                return null;
            } finally {
                close();
            }
            return check;
        }

        private boolean isRowEmpty(Row row) {
            if (row == null) return true;
            for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
                Cell cell = row.getCell(cellNum, Row.RETURN_BLANK_AS_NULL);
                if (cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK) {
                    return false;
                }
            }
            return true;
        }

        public void open() {
            current_row = 0;
        }

        public String[] readNext() {
            Sheet sheet = wb.getSheetAt(0);
            if (current_row > sheet.getLastRowNum()) {
                return null;
            }

            Row row = sheet.getRow(current_row);
            if (row == null) {
                current_row++;
                return new String[0];
            }

            int numCells = row.getLastCellNum();
            String[] data = new String[numCells];

            for (int cellIndex = 0; cellIndex < numCells; cellIndex++) {
                Cell cell = row.getCell(cellIndex, Row.RETURN_BLANK_AS_NULL);
                data[cellIndex] = (cell == null) ? "" : formatter.formatCellValue(cell);
            }

            current_row++;
            return data;
        }

        public void close() {
            try {
                if (wb != null) {
                    wb.close();
                }
                if (super.getInputStream() != null) {
                    super.getInputStream().close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class FieldFileXlsx extends FieldFileBase {
        private XSSFWorkbook wb;
        private int currentRow;

        DataFormatter formatter = new DataFormatter();

        FieldFileXlsx(final Context ctx, final Uri path) {
            super(ctx, path);
        }

        @Override
        public boolean isCSV() {
            return false;
        }

        @Override
        public boolean isExcel() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public String[] getColumns() {

            try {

                if (super.getInputStream() != null) {

                    wb = new XSSFWorkbook(super.getInputStream());

                    XSSFSheet sheet = wb.getSheetAt(0);

                    XSSFRow headerRow = sheet.getRow(0);

                    ArrayList<String> columns = new ArrayList<>();

                    for (Iterator<Cell> it = headerRow.cellIterator(); it.hasNext();) {
                        XSSFCell cell = (XSSFCell) it.next();

                        columns.add(cell.getStringCellValue());

                    }

                    return columns.toArray(new String[] {});
                }

            } catch (IOException format) {

                format.printStackTrace();

            }

            return new String[0];
        }

        @Override
        public HashMap<String, String> getColumnSet(String columnLabel, int idColPosition) {
            HashMap<String, String> check = new HashMap<>();
            try {
                open();
                XSSFSheet sheet = wb.getSheetAt(0);

                for (Iterator<Row> it = sheet.rowIterator(); it.hasNext(); ) {
                    XSSFRow row = (XSSFRow) it.next();
                    String value = getCellStringValue(row.getCell(idColPosition));

                    if (value.isEmpty() && isRowEmpty(row)) {
                        continue; // Skip the row if the specific cell is empty and the whole row is empty
                    }

                    if (!checkUnique(check, value, columnLabel, row.getRowNum())) {
                        return null;
                    }
                }
            } catch (Exception e) {
                setLastError("Failed to process XLSX file: " + e.getMessage());
                return null;
            } finally {
                close();
            }
            return check;
        }

        private boolean isRowEmpty(XSSFRow row) {
            if (row == null || row.getLastCellNum() <= 0) {
                return true;
            }
            for (Cell cell : row) {
                if (cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK) {
                    return false;
                }
            }
            return true;
        }

        public void open() {
            currentRow = 0;
        }

        public String[] readNext() {
            XSSFSheet sheet = wb.getSheetAt(0);
            if (currentRow > sheet.getLastRowNum()) {
                return null;
            }

            XSSFRow row = sheet.getRow(currentRow);
            if (row == null) {
                currentRow++;
                return new String[0];
            }


            int numCells = row.getLastCellNum();
            String[] data = new String[numCells];

            for (int cellIndex = 0; cellIndex < numCells; cellIndex++) {
                XSSFCell cell = row.getCell(cellIndex, Row.RETURN_BLANK_AS_NULL);
                data[cellIndex] = (cell == null) ? "" : formatter.formatCellValue(cell);
            }

            currentRow++;
            return data;
        }

        public void close() {
            try {
                if (wb != null) {
                    wb.close();
                }
                if (super.getInputStream() != null) {
                    super.getInputStream().close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper function that reads the cell value and formats it as a string, handling different cell types including formulas.
     * @param cell the XSSFCell object from an Apache POI XSSFWorkbook.
     * @return the formatted string value of the cell.
     */
    private static String getCellStringValue(XSSFCell cell) {
        if (cell == null) return "";

        DataFormatter formatter = new DataFormatter();
        try {
            // Use the DataFormatter to handle different data types uniformly
            return formatter.formatCellValue(cell, cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator());
        } catch (Exception e) {
            Log.e("FieldFileBase", "Error parsing cell value: " + e.getMessage());
            return "";
        }
    }

    public static class FieldFileOther extends FieldFileBase {
        FieldFileOther(final Context ctx, final Uri path) {
            super(ctx, path);
        }

        public boolean isCSV() {
            return false;
        }

        public boolean isExcel() {
            return false;
        }

        public boolean isOther() {
            return true;
        }

        public String[] getColumns() {
            return new String[0];
        }

        public HashMap<String, String> getColumnSet(String columnLabel, int idColPosition) {
            return new HashMap<>();
        }

        public void open() {
        }

        public String[] readNext() {
            return new String[0];
        }

        public void close() {
        }
    }
}