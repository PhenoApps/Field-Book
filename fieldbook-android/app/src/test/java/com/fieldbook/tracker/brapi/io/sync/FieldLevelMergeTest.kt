/**
 * Feature: Field-Level Conflict Resolution
 *   As a field researcher collaborating with others
 *   I want only truly conflicting fields to require manual resolution
 *   So that non-overlapping edits are auto-merged without my intervention
 *
 *   Scenario: Only one side edited a field — auto-merge
 *     Given user A offline changed observation value from "150" to "160"
 *     And user B offline changed notes from "" to "re-check"
 *     When both sync to the server
 *     Then value "160" and notes "re-check" are both preserved
 *     And no conflict is raised
 *
 *   Scenario: Both sides edited the same field — conflict
 *     Given user A offline changed value from "150" to "160"
 *     And user B offline changed value from "150" to "155"
 *     When both sync to the server
 *     Then a conflict is flagged specifically on the "value" field
 *     And the user is prompted to choose Local or Server for value
 *
 *   Scenario: Multi-field edit with partial overlap
 *     Given user A changed value and collector
 *     And user B changed value and notes
 *     When sync occurs
 *     Then "value" is flagged as conflict (both changed it)
 *     And "collector" change from A is auto-merged
 *     And "notes" change from B is auto-merged
 *
 *   Scenario: Conflict UI shows field-level differences
 *     Given a conflict exists on value field
 *     When the user views the conflict list
 *     Then the UI shows which specific fields differ
 *     And per-field resolution buttons are available
 */

package com.fieldbook.tracker.brapi.io.sync

import org.junit.Test

class FieldLevelMergeTest {

    @Test
    fun `GIVEN fields changed on different sides WHEN merged THEN auto-merge both`() {
        // TODO: RED
    }

    @Test
    fun `GIVEN same field changed on both sides WHEN merged THEN flag conflict`() {
        // TODO: RED
    }

    @Test
    fun `GIVEN multi-field changes with partial overlap WHEN merged THEN auto-merge non-overlapping`() {
        // TODO: RED
    }

    @Test
    fun `GIVEN field-level conflict WHEN viewing UI THEN show per-field differences`() {
        // TODO: RED
    }
}
