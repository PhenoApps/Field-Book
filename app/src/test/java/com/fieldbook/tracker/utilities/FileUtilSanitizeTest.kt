package com.fieldbook.tracker.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Owner regression for FileUtil Necessary-global `/` → `_` sanitize (09 §3 / §7 A).
 */
class FileUtilSanitizeTest {

    @Test
    fun sanitizeFileName_mapsSlashToUnderscore() {
        assertEquals("_", FileUtil.sanitizeFileName("/"))
        assertEquals("a_b", FileUtil.sanitizeFileName("a/b"))
    }

    @Test
    fun sanitizeFileName_slashyPlotAndTraitIds_doNotRetainPathSeparators() {
        val plotId = "field/plot-01"
        val traitId = "root/branch/leaf"

        val sanitizedPlot = FileUtil.sanitizeFileName(plotId)
        val sanitizedTrait = FileUtil.sanitizeFileName(traitId)

        assertEquals("field_plot-01", sanitizedPlot)
        assertEquals("root_branch_leaf", sanitizedTrait)
        assertFalse(sanitizedPlot.contains("/"))
        assertFalse(sanitizedTrait.contains("/"))
    }

    @Test
    fun sanitizeFileName_replacesExistingIllegalCharacters() {
        assertEquals("a_b_c_d_e_f_g_h_i_j_k", FileUtil.sanitizeFileName("a|b?c*d<e\"f\\g:h>i'j;k"))
        assertEquals("safe_name", FileUtil.sanitizeFileName("safe_name"))
    }
}
