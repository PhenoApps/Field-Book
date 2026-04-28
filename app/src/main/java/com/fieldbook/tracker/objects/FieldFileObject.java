package com.fieldbook.tracker.objects;

import static org.apache.poi.ss.usermodel.Cell.CELL_TYPE_BOOLEAN;
import static org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC;
import static org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import com.fieldbook.tracker.utilities.CSVReader;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import jxl.Workbook;
import jxl.WorkbookSettings;

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
        private final Uri path_;
        private final Context ctx;

        private String name;

        FieldFileBase(final Context ctx, final Uri path) {
            this.ctx = ctx;
            path_ = path;
            openFail = false;
            specialCharactersFail = false;
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

        // return {column name: column name}
        // if columns are duplicated, return an empty HshMap
        abstract public HashMap<String, String> getColumnSet(int idColPosition);

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

        public HashMap<String, String> getColumnSet(int idColPosition) {
            try {
                openFail = false;
                HashMap<String, String> check = new HashMap<>();
                InputStreamReader isr = new InputStreamReader(super.getInputStream());
                CSVReader cr = new CSVReader(isr);
                String[] columns = cr.readNext();

                while (columns != null) {
                    columns = cr.readNext();

                    if (columns != null) {
                        String unique = columns[idColPosition];
                        if (!unique.isEmpty()) {
                            if (check.containsKey(unique)) {
                                cr.close();
                                return new HashMap<>();
                            } else {
                                check.put(unique, unique);
                            }

                            if (unique.contains("/") || unique.contains("\\")) {
                                specialCharactersFail = true;
                            }
                        }
                    }
                }
                return check;
            } catch (Exception n) {
                openFail = true;
                n.printStackTrace();
                return new HashMap<>();
            }
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
                WorkbookSettings wbSettings = new WorkbookSettings();
                wbSettings.setUseTemporaryFileDuringWrite(true);

                InputStream is = super.getInputStream();
                if (is != null) {
                    wb = Workbook.getWorkbook(super.getInputStream(), wbSettings);
                    String[] importColumns = new String[wb.getSheet(0).getColumns()];

                    for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                        importColumns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                    }
                    return importColumns;
                }

            } catch (Exception ignore) {
                openFail = true;
                return new String[0];
            }

            return new String[0];
        }

        public HashMap<String, String> getColumnSet(int idColPosition) {
            HashMap<String, String> check = new HashMap<>();

            for (int s = 0; s < wb.getSheet(0).getRows(); s++) {
                String value = wb.getSheet(0).getCell(idColPosition, s).getContents();

                if (!value.isEmpty()) {
                    if (check.containsKey(value)) {
                        return new HashMap<>();
                    } else {
                        check.put(value, value);
                    }

                    if (value.contains("/") || value.contains("\\")) {
                        specialCharactersFail = true;
                    }
                }
            }
            return check;
        }

        public void open() {
            current_row = 0;
        }

        public String[] readNext() {
            if (current_row >= wb.getSheet(0).getRows()) {
                return null;
            }

            String[] data = new String[wb.getSheet(0).getColumns()];
            for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                data[s] = wb.getSheet(0).getCell(s, current_row).getContents();
            }
            current_row += 1;
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

        public HashMap<String, String> getColumnSet(int idColPosition) {
            HashMap<String, String> check = new HashMap<>();

            XSSFSheet sheet = wb.getSheetAt(0);

            for (Iterator<Row> it = sheet.rowIterator(); it.hasNext(); ) {
                XSSFRow row = (XSSFRow) it.next();

                String value = getCellStringValue(row.getCell(idColPosition));

                if (check.containsKey(value)) {
                    return new HashMap<>();
                } else {
                    check.put(value, value);
                }

                if (value.contains("/") || value.contains("\\")) {
                    specialCharactersFail = true;
                }

            }

            return check;
        }

        public void open() {
            currentRow = 0;
        }

        public String[] readNext() {

            DataFormatter fmt = new DataFormatter();
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFFormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            if (currentRow >= sheet.getPhysicalNumberOfRows()) {
                return null;
            }

            XSSFRow row = sheet.getRow(currentRow);
            ArrayList<String> data = new ArrayList<>();

            int maxColumns = sheet.getRow(0).getLastCellNum(); // Get total number of columns from header

            for (int colIdx = 0; colIdx < maxColumns; colIdx++) {
                XSSFCell cell = (row == null) ? null : row.getCell(colIdx);

                if (cell != null) {
                    if (cell.getCellType() == Cell.CELL_TYPE_FORMULA) {//formula
                        int type = evaluator.evaluateFormulaCell(cell);
                        switch (type) {
                            case CELL_TYPE_BOOLEAN:
                                data.add(String.valueOf(cell.getBooleanCellValue()));
                                break;
                            case CELL_TYPE_NUMERIC:
                                data.add(String.valueOf(cell.getNumericCellValue()));
                                break;
                            default:
                                data.add(cell.getStringCellValue());
                                break;
                        }
                    } else {
                        data.add(fmt.formatCellValue(cell));
                    }
                } else {
                    data.add(""); // Add empty string for missing/empty cells
                }
            }

            currentRow += 1;
            return data.toArray(new String[] {});
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
     * Helper function that reads the cell value and parses to string from xlsx sheets.
     * @param cell the xssf cell object
     * @return attempt to parse the string value of the cell
     */
    private static String getCellStringValue(XSSFCell cell) {

        if (cell == null) return "";

        FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();

        switch (cell.getCellType()) {
            case 0: { //numeric
                return String.valueOf(cell.getNumericCellValue());
            }
            case 1: { //text
                return cell.getStringCellValue();
            }
            case Cell.CELL_TYPE_FORMULA: { //formula
                switch (evaluator.evaluateFormulaCell(cell)) {
                    case CELL_TYPE_BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());
                    case CELL_TYPE_NUMERIC:
                        return String.valueOf(cell.getNumericCellValue());
                    case CELL_TYPE_STRING:
                        return cell.getStringCellValue();
                }
            }
            case 3: { //boolean
                return String.valueOf(cell.getBooleanCellValue());
            }
            default:
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

        public HashMap<String, String> getColumnSet(int idColPosition) {
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