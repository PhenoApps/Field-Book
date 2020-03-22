package com.fieldbook.tracker.objects;

import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.utilities.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;

import jxl.Workbook;
import jxl.WorkbookSettings;

public class FieldFileObject {
    public static FieldFileBase create(final String path) {
        switch (getExtension(path)) {
            case "csv":
                return new FieldFileCSV(path);
            case "xls":
                return new FieldFileExcel(path);
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
                        if (check.containsKey(columns[idColPosition])) {
                            cr.close();
                            return new HashMap<>();
                        } else {
                            check.put(columns[idColPosition], columns[idColPosition]);
                        }

                        if (columns[idColPosition].contains("/") || columns[idColPosition].contains("\\")) {
                            specialCharactersFail = true;
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