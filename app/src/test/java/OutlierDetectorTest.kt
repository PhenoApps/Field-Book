import com.fieldbook.tracker.utilities.OutlierDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the adaptive prediction interval used to flag numeric outliers during collection.
 *
 * Interval bounds noted below were worked out independently of the implementation.
 */
class OutlierDetectorTest {

    companion object {

        /** Median 12, MAD 1, so the scale is 1.4826. */
        private val FIVE_VALUES = listOf(10.0, 11.0, 12.0, 13.0, 14.0)

        /** Same spread as above with ten times the data. */
        private val FIFTY_VALUES = List(10) { FIVE_VALUES }.flatten()
    }

    @Test
    fun tooFewReferenceValuesNeverFlags() {

        assertFalse(OutlierDetector.isOutlier(1000.0, FIVE_VALUES.take(4)))
    }

    @Test
    fun earlyIntervalOnlyFlagsGrossErrors() {

        // interval is about -1.98 to 25.98
        assertFalse(OutlierDetector.isOutlier(25.0, FIVE_VALUES))
        assertTrue(OutlierDetector.isOutlier(100.0, FIVE_VALUES))
        assertTrue(OutlierDetector.isOutlier(-3.0, FIVE_VALUES))
    }

    @Test
    fun intervalTightensAsDataIsCollected() {

        // interval is about 6.76 to 17.24
        assertFalse(OutlierDetector.isOutlier(15.0, FIFTY_VALUES))
        assertFalse(OutlierDetector.isOutlier(17.0, FIFTY_VALUES))
        assertTrue(OutlierDetector.isOutlier(19.0, FIFTY_VALUES))
        assertTrue(OutlierDetector.isOutlier(6.0, FIFTY_VALUES))

        // the same value passes with less data
        assertFalse(OutlierDetector.isOutlier(19.0, FIVE_VALUES))
    }

    @Test
    fun zeroMadFallsBackToIqr() {

        // MAD is 0 but IQR is 1.75, interval is about -1.51 to 11.51
        val reference = listOf(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 6.0, 7.0, 8.0, 9.0)

        assertFalse(OutlierDetector.isOutlier(11.0, reference))
        assertTrue(OutlierDetector.isOutlier(12.0, reference))
    }

    @Test
    fun zeroMadAndIqrFallsBackToMeanAbsoluteDeviation() {

        // interval is about -6.21 to 12.21
        val reference = List(8) { 3.0 } + 10.0

        assertFalse(OutlierDetector.isOutlier(12.0, reference))
        assertTrue(OutlierDetector.isOutlier(13.0, reference))
    }

    @Test
    fun identicalReferenceValuesNeverFlag() {

        val reference = List(20) { 7.0 }

        assertFalse(OutlierDetector.isOutlier(7.0, reference))
        assertFalse(OutlierDetector.isOutlier(1000.0, reference))
    }

    @Test
    fun tQuantileLookup() {

        assertEquals(4.587, OutlierDetector.tQuantile(10), 1e-9)

        // exact t 0.9995 at 35 degrees of freedom is 3.5911
        assertEquals(3.5911, OutlierDetector.tQuantile(35), 0.001)

        // approaches the normal quantile
        assertEquals(3.291, OutlierDetector.tQuantile(100_000), 0.001)
    }
}
