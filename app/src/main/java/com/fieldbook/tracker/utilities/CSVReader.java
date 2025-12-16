package com.fieldbook.tracker.utilities;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom CSV Reading Class
 * No changes in V2
 */
public class CSVReader implements Closeable {

    private static final char DEFAULT_SEPARATOR = ',';
    private static final char DEFAULT_QUOTE_CHARACTER = '"';
    private static final int DEFAULT_SKIP_LINES = 0;
    private BufferedReader br;
    private boolean hasNext = true;
    private char separator;
    private char quotechar;
    private int skipLines;
    private boolean linesSkiped;

    private boolean firstLineRead = false;
    private static final String UTF8_BOM = "\uFEFF";

    public CSVReader(Reader reader) {
        this(reader, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER,
                DEFAULT_SKIP_LINES);
    }

    private CSVReader(Reader reader, char separator, char quotechar, int line) {
        this.br = new BufferedReader(reader);
        this.separator = separator;
        this.quotechar = quotechar;
        this.skipLines = line;
    }

    public String[] readNext() throws IOException {

        String nextLine = getNextLine();

        if (!firstLineRead) {

            firstLineRead = true;

            if (nextLine != null && nextLine.startsWith(UTF8_BOM)) {

                nextLine = nextLine.substring(1);

            }
        }
        return parseLine(nextLine);
    }

    /**
     * Reads the next line from the file.
     */
    private String getNextLine() throws IOException {
        if (!this.linesSkiped) {
            for (int i = 0; i < skipLines; i++) {
                br.readLine();
            }
            this.linesSkiped = true;
        }
        String nextLine = br.readLine();
        if (nextLine == null) {
            hasNext = false;
        }
        return hasNext ? nextLine : null;
    }

    /**
     * Parses an incoming String and returns an array of elements.
     */
    private String[] parseLine(String nextLine) throws IOException {

        if (nextLine == null) {
            return null;
        }

        List<String> tokensOnThisLine = new ArrayList<>();
        StringBuffer sb = new StringBuffer();
        boolean inQuotes = false;

        do {
            if (inQuotes) {
                // continuing a quoted section, reappend newline
                sb.append("\n");
                nextLine = getNextLine();
                if (nextLine == null)
                    break;
            }

            for (int i = 0; i < nextLine.length(); i++) {

                char c = nextLine.charAt(i);
                if (c == quotechar) {
                    // this gets complex... the quote may end a quoted block, or
                    // escape another quote.
                    // do a 1-char lookahead:

                    if (inQuotes // we are in quotes, therefore there can be
                            // escaped quotes in here.
                            && nextLine.length() > (i + 1) // there is indeed
                            // another character
                            // to check.
                            && nextLine.charAt(i + 1) == quotechar) { // ..and
                        // that
                        // char.
                        // is a
                        // quote
                        // also.
                        // we have two quote chars in a row == one quote char,
                        // so consume them both and
                        // put one on the token. we do *not* exit the quoted
                        // text.
                        sb.append(nextLine.charAt(i + 1));
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                        // the tricky case of an embedded quote in the middle:
                        // a,bc"d"ef,g
                        if (i > 2 // not on the begining of the line
                                && nextLine.charAt(i - 1) != this.separator // not
                                // at
                                // the
                                // begining
                                // of
                                // an
                                // escape
                                // sequence
                                && nextLine.length() > (i + 1)
                                && nextLine.charAt(i + 1) != this.separator // not
                            // at
                            // the
                            // end
                            // of
                            // an
                            // escape
                            // sequence
                        ) {
                            sb.append(c);
                        }
                    }
                } else if (c == separator && !inQuotes) {
                    tokensOnThisLine.add(sb.toString());
                    sb = new StringBuffer(); // start work on next token
                } else {
                    sb.append(c);
                }
            }

        } while (inQuotes);

        tokensOnThisLine.add(sb.toString());
        return tokensOnThisLine.toArray(new String[tokensOnThisLine.size()]);
    }

    public void close() throws IOException {
        br.close();
    }

}