package com.fieldbook.tracker.objects;

/**
 * Enum for defining possible import formats.
 * This enum specifies the formats supported for importing field data
 */
public enum ImportFormat {
    CSV("csv"), // CSV: Comma-Separated Values
    XLS("xls"), // Excel Spreadsheet format (97-2003) for Microsoft Excel
    XLSX("xlsx"), // Excel Spreadsheet format (Office Open XML) for newer versions of Microsoft Excel
    BRAPI("brapi"), // Imports made using the Breeding API (BrAPI)
    INTERNAL("internal"); // Created internally (within Field Book)

    private final String format;

    ImportFormat(String format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return this.format;
    }

    /**
     * Method to get an enum value from a string representation.
     * @param text The string representation of the import format.
     * @return The corresponding ImportFormat enum value, or null if no match is found.
     */
    public static ImportFormat fromString(String text) {
        for (ImportFormat b : ImportFormat.values()) {
            if (b.format.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }
}
