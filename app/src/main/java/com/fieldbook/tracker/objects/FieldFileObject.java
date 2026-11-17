package com.fieldbook.tracker.objects;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import com.fieldbook.tracker.utilities.CSVReader;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import jxl.Workbook;
import jxl.WorkbookSettings;

/**
 * Readers for the file types a field can be imported from.
 * Each reader returns the header from {@link FieldFileBase#getColumns()}, then the header and data rows in order from
 * {@link FieldFileBase#readNext()} after {@link FieldFileBase#open()}.
 * Checking the rows is left to {@link FieldImportValidator}.
 */
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
        private final Uri path_;
        private final Context ctx;

        private String name;

        FieldFileBase(final Context ctx, final Uri path) {
            this.ctx = ctx;
            path_ = path;
            openFail = false;
        }

        /**
         * Opens a new stream over the file; callers close it.
         * Not final so tests can supply file contents without a content resolver.
         */
        public InputStream getInputStream() {
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

        // read file
        abstract public void open();

        /**
         * @return the next row, starting with the header, or null once the rows run out or a read fails
         * (check {@link #getOpenFailed()} to tell the two apart)
         */
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
            openFail = false;
            try (CSVReader reader = new CSVReader(new InputStreamReader(getInputStream()))) {
                return reader.readNext();
            } catch (Exception ignore) {
                openFail = true;
                return new String[0];
            }
        }

        public void open() {
            try {
                openFail = false;
                InputStreamReader isr = new InputStreamReader(getInputStream());
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
                return null;
            }
        }

        public void close() {
            try {
                if (cr != null) {
                    cr.close();
                }
            } catch (Exception e) {
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
            openFail = false;
            WorkbookSettings wbSettings = new WorkbookSettings();
            wbSettings.setUseTemporaryFileDuringWrite(true);

            try (InputStream is = getInputStream()) {
                if (is != null) {
                    wb = Workbook.getWorkbook(is, wbSettings);
                    String[] importColumns = new String[wb.getSheet(0).getColumns()];

                    for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                        importColumns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                    }
                    return importColumns;
                }
            } catch (Exception ignore) {
                openFail = true;
            }

            return new String[0];
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
            if (wb != null) {
                wb.close();
            }
        }
    }

    public static class FieldFileXlsx extends FieldFileBase {
        private XSSFWorkbook wb;
        private FormulaEvaluator evaluator;
        private final DataFormatter formatter = new DataFormatter();
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
            openFail = false;
            try (InputStream is = getInputStream()) {
                if (is != null) {
                    wb = new XSSFWorkbook(is);
                    evaluator = wb.getCreationHelper().createFormulaEvaluator();
                    String[] header = readRow(wb.getSheetAt(0).getRow(0));
                    return header == null ? new String[0] : header;
                }
            } catch (Exception e) {
                openFail = true;
                e.printStackTrace();
            }

            return new String[0];
        }

        public void open() {
            currentRow = 0;
        }

        public String[] readNext() {
            XSSFSheet sheet = wb.getSheetAt(0);
            if (currentRow > sheet.getLastRowNum()) {
                return null;
            }

            String[] data = readRow(sheet.getRow(currentRow));
            currentRow++;
            return data == null ? new String[0] : data;
        }

        /**
         * Reads every cell up to the row's last one by position, so a blank cell keeps the
         * cells after it in the right column.
         */
        private String[] readRow(@Nullable XSSFRow row) {
            if (row == null || row.getLastCellNum() < 0) return null;

            String[] data = new String[row.getLastCellNum()];
            for (int i = 0; i < data.length; i++) {
                data[i] = formatCell(row.getCell(i, Row.RETURN_BLANK_AS_NULL));
            }
            return data;
        }

        /**
         * Formats a cell as it is displayed in a spreadsheet.
         * Formulas are evaluated, falling back to the result the spreadsheet saved when POI cannot evaluate them
         * (POI 3.12 does not know newer functions such as CONCAT).
         */
        private String formatCell(@Nullable Cell cell) {
            if (cell == null) return "";
            if (cell.getCellType() != Cell.CELL_TYPE_FORMULA) return formatter.formatCellValue(cell);

            try {
                return formatter.formatCellValue(cell, evaluator);
            } catch (RuntimeException unsupported) {
                switch (cell.getCachedFormulaResultType()) {
                    case Cell.CELL_TYPE_NUMERIC:
                        CellStyle style = cell.getCellStyle();
                        return formatter.formatRawCellContents(cell.getNumericCellValue(),
                                style.getDataFormat(), style.getDataFormatString());
                    case Cell.CELL_TYPE_STRING:
                        return cell.getRichStringCellValue().getString();
                    case Cell.CELL_TYPE_BOOLEAN:
                        return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
                    default:
                        return "";
                }
            }
        }

        public void close() {
            try {
                if (wb != null) {
                    wb.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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

        public void open() {
        }

        public String[] readNext() {
            return null;
        }

        public void close() {
        }
    }
}
