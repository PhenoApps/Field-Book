import com.fieldbook.tracker.database.dao.ObservationDao;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * CollectActivity confirms a save by reading the row back and comparing it to the value that was
 * submitted. insertObservation strips null control characters on the way in, so the comparison has
 * to strip them the same way -- otherwise every paste from an affected device would look like a
 * failed write and raise the save-failure dialog on a value that actually saved fine.
 */
public class ObservationValueSanitizeTest {

    private static final String NUL = String.valueOf((char) 0);

    private static String sanitize(String value) {
        return ObservationDao.Companion.sanitizeValue(value);
    }

    @Test
    public void ordinaryValueIsUnchanged() {
        assertEquals("38", sanitize("38"));
        assertEquals("a note with spaces", sanitize("a note with spaces"));
    }

    @Test
    public void nullBecomesEmptyString() {
        assertEquals("", sanitize(null));
    }

    @Test
    public void emptyValueStaysEmpty() {
        assertEquals("", sanitize(""));
    }

    @Test
    public void nullCharactersAreStripped() {
        assertEquals("38", sanitize("3" + NUL + "8"));
        assertEquals("38", sanitize(NUL + "38" + NUL));
    }

    /**
     * The submitted value carries null characters, the stored value does not. They must still
     * compare equal or the write would be reported as failed.
     */
    @Test
    public void submittedAndStoredFormsCompareEqual() {
        String submitted = "12" + NUL + ".5";
        String stored = "12.5";
        assertEquals(sanitize(stored), sanitize(submitted));
    }

    @Test
    public void sanitizeIsIdempotent() {
        String submitted = "4" + NUL + "2";
        assertEquals(sanitize(submitted), sanitize(sanitize(submitted)));
    }

    /**
     * Verification must still catch a genuinely different value, e.g. a row that was overwritten
     * by another write between the save and the read-back.
     */
    @Test
    public void differentValuesDoNotCompareEqual() {
        assertNotEquals(sanitize("38"), sanitize("39"));
    }
}
