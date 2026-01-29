package com.fieldbook.tracker.utilities;

import android.database.Cursor;

import com.fieldbook.tracker.objects.TraitObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;

/**
 * Custom CSV Writing Class
 * V2 - WriteFile and WriteFile2 have been amended
 * Columns are now selectable
 */
public class CSVWriter {

    /**
     * The character used for escaping quotes.
     */
    private static final char DEFAULT_ESCAPE_CHARACTER = '"';
    /**
     * The default separator to use if none is supplied to the constructor.
     */
    private static final char DEFAULT_SEPARATOR = ',';
    /**
     * The default quote character to use if none is supplied to the
     * constructor.
     */
    private static final char DEFAULT_QUOTE_CHARACTER = '"';
    /**
     * The quote constant to use when you wish to suppress all quoting.
     */
    private static final char NO_QUOTE_CHARACTER = '\u0000';
    /**
     * The escape constant to use when you wish to suppress all escaping.
     */
    private static final char NO_ESCAPE_CHARACTER = '\u0000';
    /**
     * Default line terminator uses platform encoding.
     */
    private static final String DEFAULT_LINE_END = "\n";
    private final PrintWriter pw;
    private final char separator;
    private final char quotechar;
    private final char escapechar;
    private final String lineEnd;
    private final Cursor curCSV;

    /**
     * Constructs CSVWriter using a comma for the separator.
     */
    public CSVWriter(Writer writer, Cursor c) {
        this(writer, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER,
                DEFAULT_ESCAPE_CHARACTER, DEFAULT_LINE_END, c);
    }

    /**
     * Constructs CSVWriter with supplied separator, quote char, escape char and
     * line ending.
     */
    private CSVWriter(Writer writer, char separator, char quotechar,
                      char escapechar, String lineEnd, Cursor c) {
        this.pw = new PrintWriter(writer);
        this.separator = separator;
        this.quotechar = quotechar;
        this.escapechar = escapechar;
        this.lineEnd = lineEnd;
        curCSV = c;
    }

    /**
     * Outputs the field book; First Name and Last name does not exist in
     * database, and so is passed in as a parameter
     * V2 - Range added, columns selectable
     */
    public void writeDatabaseFormat(ArrayList<String> range, String deviceName) throws Exception {
        // Simply loop through all items
        if (curCSV.getCount() > 0) {

            range.add("trait");
            range.add("value");
            range.add("timestamp");
            range.add("person");
            range.add("location");
            range.add("number");
            range.add("photo_uri");
            range.add("video_uri");
            range.add("audio_uri");

            //device name must be last added
            range.add("device_name");

            String[] labels = range.toArray(new String[range.size()]);

            writeNext(labels);

            curCSV.moveToPosition(-1);

            while (curCSV.moveToNext()) {
                String[] arrStr = new String[labels.length];

                for (int i = 0; i < labels.length - 1; i++) {
                    String value = curCSV.getString(i);
                    // prefer a media URI if present for this row
                    //value = preferMediaUri(curCSV, value);
                    arrStr[i] = value;
                }

                // add device name
                arrStr[labels.length - 1] = deviceName;

                writeNext(arrStr);
            }
        }

        curCSV.close();
        close();
    }

    /**
     * Exports traits table
     */
    public void writeTraitFile(String[] labels) throws Exception {
        if (curCSV.getCount() > 0) {

            writeNext(labels);

            curCSV.moveToPosition(-1);

            while (curCSV.moveToNext()) {

                String[] arrStr = new String[labels.length];

                for (int i = 0; i < arrStr.length; i++) {
                    arrStr[i] = curCSV.getString(i);
                }

                writeNext(arrStr);
            }
        }

        curCSV.close();
        close();
    }

    private String searchForCategorical(ArrayList<TraitObject> traits, String name, String value) {
        for (TraitObject t : traits) {
            if (t.getName().equals(name)) {
                if (t.getFormat().equals("categorical") || t.getFormat().equals("qualitative")) {
                    try {
                        return CategoryJsonUtil.Companion.flattenMultiCategoryValue(
                                CategoryJsonUtil.Companion.decode(value), false
                        );
                    } catch (Exception e) {
                        return value;
                    }

                }
            }
        }
        return value;
    }

    // If any media URI columns are present and non-empty for the current cursor row,
    // prefer returning a media URI over the textual value.
    private String preferMediaUri(android.database.Cursor c, String currentValue) {
        if (c == null) return currentValue;

        // Prefer photo, then video, then audio (typical image-first preference)
        String photo = getColumnIfExists(c, "photo_uri");
        if (photo != null && !photo.isEmpty()) return photo;
        String video = getColumnIfExists(c, "video_uri");
        if (video != null && !video.isEmpty()) return video;
        String audio = getColumnIfExists(c, "audio_uri");
        if (audio != null && !audio.isEmpty()) return audio;

        return currentValue;
    }

    private String getColumnIfExists(android.database.Cursor c, String name) {
        try {
            int idx = c.getColumnIndex(name);
            if (idx >= 0) return c.getString(idx);
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * Generates data in an table style format
     */
    public void writeTableFormat(String[] labels, int rangeTotal, ArrayList<TraitObject> traits) throws Exception {
        if (curCSV.getCount() > 0) {

            writeNext(labels);

            curCSV.moveToPosition(-1);

            while (curCSV.moveToNext()) {

                String[] arrStr = new String[labels.length];

                for (int k = 0; k < rangeTotal; k++) {
                    String traitName = curCSV.getColumnName(k);
                    String rawValue = curCSV.getString(k);
                    String value = searchForCategorical(traits, traitName, rawValue);
                    arrStr[k] = value;
                }

                // Get matching values for every row in the Range table
                for (int k = rangeTotal; k < labels.length; k++) {
                    String traitName = curCSV.getColumnName(k);
                    String rawValue = curCSV.getString(k);
                    String value = searchForCategorical(traits, traitName, rawValue);
                    arrStr[k] = value;
                }

                writeNext(arrStr);
            }
        }

        curCSV.close();
        close();
    }

    /**
     * Writes the next line to the file.
     */
    private void writeNext(String[] nextLine) {

        if (nextLine == null)
            return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nextLine.length; i++) {

            if (i != 0) {
                sb.append(separator);
            }

            String nextElement = nextLine[i];

            if (nextElement == null)
                continue;

            if (quotechar != NO_QUOTE_CHARACTER)
                sb.append(quotechar);

            for (int j = 0; j < nextElement.length(); j++) {
                char nextChar = nextElement.charAt(j);

                if (escapechar != NO_ESCAPE_CHARACTER && nextChar == quotechar) {
                    sb.append(escapechar).append(nextChar);
                } else if (escapechar != NO_ESCAPE_CHARACTER
                        && nextChar == escapechar) {
                    sb.append(escapechar).append(nextChar);
                } else {
                    sb.append(nextChar);
                }
            }

            if (quotechar != NO_QUOTE_CHARACTER)
                sb.append(quotechar);
        }

        sb.append(lineEnd);
        pw.write(sb.toString());

    }

    /**
     * Close the underlying stream writer flushing any buffered content.
     */
    public void close() throws IOException {
        pw.flush();
        pw.close();
    }
}