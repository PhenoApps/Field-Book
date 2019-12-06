import com.fieldbook.tracker.brapi.BrapiObservation;
import com.fieldbook.tracker.brapi.Observation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObservationTest {

    @Test
    public void nullFieldsEqual() {
        Observation o1 = new Observation();
        Observation o2 = new Observation();
        assertTrue("Null fields are equal", o1.equals(o2));
    }

    @Test
    public void sameReferenceEqual() {
        Observation o1 = new Observation();
        Observation o2 = o1;
        assertTrue("Same references are equal", o1.equals(o2));
    }

    @Test
    public void otherObjectNull() {
        Observation o1 = new Observation();
        Observation o2 = null;
        assertFalse("Other observation null", o1.equals(o2));
    }

    @Test
    public void fieldsSetSame() {
        Observation o1 = new Observation();
        Observation o2 = new Observation();
        o1.setUnitDbId("1");
        o1.setVariableDbId("VBTI:0000006");
        o2.setUnitDbId("1");
        o2.setVariableDbId("VBTI:0000006");
        assertTrue("Same fields equal", o1.equals(o2));
    }

    @Test
    public void nullHashCodesEqual() {
        Observation o1 = new Observation();
        Observation o2 = new Observation();
        assertTrue("Null fields are equal", o1.hashCode() == o2.hashCode());
    }

    @Test
    public void fieldsSameHashCodesEqual() {
        Observation o1 = new Observation();
        Observation o2 = new Observation();
        o1.setUnitDbId("1");
        o1.setVariableDbId("VBTI:0000006");
        o2.setUnitDbId("1");
        o2.setVariableDbId("VBTI:0000006");
        assertTrue("Hashcode fields equal", o1.hashCode() == o2.hashCode());
    }

    @Test
    public void fieldsDifferentHashCodesNotEqual() {
        Observation o1 = new Observation();
        Observation o2 = new Observation();
        o1.setUnitDbId("1");
        o1.setVariableDbId("VBTI:0000007");
        o2.setUnitDbId("1");
        o2.setVariableDbId("VBTI:0000006");
        assertFalse("Hashcode fields equal", o1.hashCode() == o2.hashCode());
    }

    @Test
    public void defaultStatus() {
        Observation o = new Observation();
        assertTrue("Default status NEW", o.getStatus() == Observation.Status.NEW);
    }

    @Test
    public void invalidStatus() {
        Observation o = new Observation();
        o.setDbId("1");
        o.setLastSyncedTime("2019-10-15 12:14:59-0400");
        assertTrue("Invalid status dbId but no lastSynced time", o.getStatus() == BrapiObservation.Status.INVALID);
    }

    @Test
    public void incompleteStatus() {
        Observation o = new Observation();
        o.setDbId("1");
        assertTrue("Incomplete status", o.getStatus() == BrapiObservation.Status.INCOMPLETE);
    }

    @Test
    public void syncedStatus() {
        Observation o = new Observation();
        o.setDbId("1");
        o.setTimestamp("2019-10-15 11:14:59-0400");
        o.setLastSyncedTime("2019-10-15 12:14:59-0400");
        assertTrue("Synced status", o.getStatus() == Observation.Status.SYNCED);
    }

    @Test
    public void editedStatus() {
        Observation o = new Observation();
        o.setDbId("1");
        o.setTimestamp("2019-10-15 12:14:59-0400");
        o.setLastSyncedTime("2019-10-15 11:14:59-0400");
        assertTrue("Edited status", o.getStatus() == Observation.Status.EDITED);
    }

}
