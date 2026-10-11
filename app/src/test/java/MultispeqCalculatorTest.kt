import com.fieldbook.tracker.devices.multispeq.MultispeqCalculator
import com.fieldbook.tracker.devices.multispeq.MultispeqParameter
import com.fieldbook.tracker.devices.multispeq.MultispeqParameter.*
import com.fieldbook.tracker.devices.multispeq.MultispeqProtocolType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks MultispeqCalculator against the openJII RIDES 2.1 macro and the JII-MultispeQ-Protocols analyses.
 *
 * Fixtures in resources/multispeq are the example responses from JII-MultispeQ-Protocols (MIT).
 * rides_mpf.json is rides.json with the PAM Fm' steps set to a falling ramp, so the multi-phase
 * flash extrapolation is used instead of the apparent Fm'.
 * Expected RIDES values are the output of photosynthesis_rides_2_1_macro.js run on each fixture
 * with the openJII macro helper library.
 */
class MultispeqCalculatorTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/multispeq/$name")!!.bufferedReader().use { it.readText() }

    private fun calculate(
        name: String,
        protocol: MultispeqProtocolType,
        params: Set<MultispeqParameter> = protocol.provides
    ) = MultispeqCalculator.calculate(fixture(name), params, protocol)

    @Test
    fun ridesMatchesMacro() {
        val results = calculate("rides.json", MultispeqProtocolType.RIDES)

        val expected = mapOf(
            PAR to "14.403",
            TEMPERATURE to "23.7",
            HUMIDITY to "35.835",
            PRESSURE to "979.568",
            LEAF_TEMP to "25.53",
            ANGLE to "2.511",
            PHI2 to "0.668",
            PHI_NPQ to "0.195",
            PHI_NO to "0.136",
            NPQT to "1.43",
            QL to "1.004",
            LEF to "4.33",
            ECST to "0.00019",
            VHP to "0.01",
            PS1_ACTIVE to "2.884",
            PS1_OPEN to "0.922",
            PS1_OXIDIZED to "-0.106",
            PS1_OVER_REDUCED to "0.184",
            SPAD_VALUE to "42.726",
            LEAF_THICKNESS to "0.3"
        )
        expected.forEach { (param, value) -> assertEquals(param.name, value, results[param]) }

        // The macro's regression solver differs from ours; the macro reports 52.485
        assertEquals(52.485, results.getValue(GHP).toDouble(), 0.01)
    }

    @Test
    fun ridesUsesMultiPhaseFlashWhenFmPrimeFallsWithIntensity() {
        val results = calculate("rides_mpf.json", MultispeqProtocolType.RIDES)

        assertEquals("0.706", results[PHI2])
        assertEquals("0.15", results[PHI_NPQ])
        assertEquals("0.144", results[PHI_NO])
        assertEquals("1.044", results[NPQT])
        assertEquals("1.004", results[QL])
        assertEquals("4.58", results[LEF])
    }

    @Test
    fun ridesOnlyReturnsRequestedParameters() {
        val results = calculate("rides.json", MultispeqProtocolType.RIDES, setOf(PHI2, SPAD_VALUE))

        assertEquals(mapOf(PHI2 to "0.668", SPAD_VALUE to "42.726"), results)
    }

    @Test
    fun phi2Protocol() {
        val results = calculate("phi2.json", MultispeqProtocolType.PHI2)

        assertEquals("0.269", results[PHI2])
        assertEquals("2.17", results[LEF])
        assertEquals("17.95", results[PAR])
    }

    @Test
    fun parProtocol() {
        assertEquals("346.791", calculate("par.json", MultispeqProtocolType.PAR)[PAR])
    }

    @Test
    fun spadProtocol() {
        val response = """{"sample":[{"set":[{"label":"spad","spad":[38.25,1,2]}]}]}"""

        val results = MultispeqCalculator.calculate(response, setOf(SPAD_VALUE), MultispeqProtocolType.SPAD)

        assertEquals("38.25", results[SPAD_VALUE])
    }

    @Test
    fun unparseableResponseIsNotAvailable() {
        val results = MultispeqCalculator.calculate("not json", setOf(PHI2, PAR), MultispeqProtocolType.RIDES)

        assertEquals(mapOf(PHI2 to "N/A", PAR to "N/A"), results)
    }

    @Test
    fun selectsFastestProtocolCoveringParameters() {
        assertEquals(MultispeqProtocolType.PAR, MultispeqProtocolType.select(setOf(PAR)))
        assertEquals(MultispeqProtocolType.SPAD, MultispeqProtocolType.select(setOf(SPAD_VALUE)))
        assertEquals(MultispeqProtocolType.PHI2, MultispeqProtocolType.select(setOf(PHI2)))
        assertEquals(MultispeqProtocolType.PHI2, MultispeqProtocolType.select(setOf(PHI2, LEF, PAR)))
        assertEquals(MultispeqProtocolType.RIDES, MultispeqProtocolType.select(setOf(PHI2, NPQT)))
        assertEquals(MultispeqProtocolType.RIDES, MultispeqProtocolType.select(setOf(SPAD_VALUE, PAR)))
        assertEquals(MultispeqProtocolType.RIDES, MultispeqProtocolType.select(setOf(TEMPERATURE)))
        assertEquals(MultispeqProtocolType.RIDES, MultispeqProtocolType.select(emptySet()))
    }
}
