package com.fieldbook.tracker.objects;

import com.fieldbook.tracker.utilities.CSVReader;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import jxl.Workbook;
import jxl.WorkbookSettings;

//TODO when merged with xlsx edit getColumnSet
public class FieldFileObject {
    public static FieldFileBase create(final String path) {
        switch (getExtension(path)) {
            case "csv":
                return new FieldFileCSV(path);
            case "xls":
                return new FieldFileExcel(path);
            case "xlsx":
                return new FieldFileXlsx(path);
            default:
                return new FieldFileOther(path);
        }
    }

    private static String getExtension(final String path) {
        final int first = path.lastIndexOf(".") + 1;
        return path.substring(first).toLowerCase();
    }

    public abstract static class FieldFileBase {
        boolean openFail;
        boolean specialCharactersFail;
        private String path_;

        FieldFileBase(final String path) {
            path_ = path;
            openFail = false;
            specialCharactersFail = false;
        }

        public final String getPath() {
            return path_;
        }

        public final String getStem() {
            final int first = path_.lastIndexOf("/") + 1;
            final int last = path_.lastIndexOf(".");
            return path_.substring(first, last);
        }

        public final boolean hasSpecialCharasters() {
            return specialCharactersFail;
        }

        public FieldObject createFieldObject() {
            FieldObject f = new FieldObject();
            f.setExp_name(this.getStem());
            f.setExp_alias(this.getStem());
            return f;
        }

        public boolean getOpenFailed() {
            return openFail;
        }

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

        FieldFileCSV(final String path) {
            super(path);
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
                FileReader fr = new FileReader(super.getPath());
                CSVReader cr = new CSVReader(fr);
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
                FileReader fr = new FileReader(super.getPath());
                CSVReader cr = new CSVReader(fr);
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
                FileReader fr = new FileReader(super.getPath());
                cr = new CSVReader(fr);
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

        FieldFileExcel(final String path) {
            super(path);
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

                wb = Workbook.getWorkbook(new File(super.getPath()), wbSettings);
                String[] importColumns = new String[wb.getSheet(0).getColumns()];

                for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                    importColumns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                }
                return importColumns;
            } catch (Exception ignore) {
                openFail = true;
                return new String[0];
            }
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
        }
    }

    public static class FieldFileXlsx extends FieldFileBase {
        private XSSFWorkbook wb;
        private int currentRow;

        FieldFileXlsx(String path) {
            super(path);
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
                
                wb = new XSSFWorkbook(new FileInputStream(getPath()));

                XSSFSheet sheet = wb.getSheetAt(0);

                XSSFRow headerRow = sheet.getRow(0);

                ArrayList<String> columns = new ArrayList<>();

                for (Iterator<Cell> it = headerRow.cellIterator(); it.hasNext();) {
                    XSSFCell cell = (XSSFCell) it.next();

                    columns.add(cell.getStringCellValue());

                }

                return columns.toArray(new String[] {});

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
            ArrayList<XSSFRow> rows = new ArrayList<>();
            XSSFSheet sheet = wb.getSheetAt(0);

            for (Iterator<Row> it = sheet.rowIterator(); it.hasNext();) {
                rows.add((XSSFRow) it.next());
            }

            if (currentRow >= rows.size()) {
                return null;
            }

            ArrayList<String> data = new ArrayList<>();
            for (Iterator<Cell> it = rows.get(currentRow).cellIterator(); it.hasNext();) {
                XSSFCell cell = (XSSFCell) it.next();
                data.add(fmt.formatCellValue(cell));
            }

            currentRow += 1;
            return data.toArray(new String[] {});
        }

        public void close() {
        }
    }

    /**
     * Helper function that reads the cell value and parses to string from xlsx sheets.
     * @param cell the xssf cell object
     * @return attempt to parse the string value of the cell
     */
    private static String getCellStringValue(XSSFCell cell) {
        switch (cell.getCellType()) {
            case 0: { //numeric
                return String.valueOf(cell.getNumericCellValue());
            }
            case 1: { //text
                return cell.getStringCellValue();
            }
            case 3: { //boolean
                return String.valueOf(cell.getBooleanCellValue());
            }
            default:
                return "";
        }
    }

    public static class FieldFileOther extends FieldFileBase {
        FieldFileOther(final String path) {
            super(path);
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