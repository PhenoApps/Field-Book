package com.fieldbook.tracker.devices.multispeq

import org.json.JSONArray

/**
 * Enumeration of all calculatable output parameters from the RIDES 2.1 protocol.
 *
 * Parameters are grouped by [Group], which maps to the sub-protocol required to produce them.
 * The [Group.subProtocol] value is the label used in the RIDES 2.1 `_protocol_set_` array.
 * [Group.ENVIRONMENT] has no sub-protocol because `no_leaf_baseline` is always included.
 */
enum class MultispeqParameter(
    val displayName: String,
    val group: Group,
    val unit: String = ""
) {
    // Environment — from no_leaf_baseline (always collected)
    PAR("PAR", Group.ENVIRONMENT, "µmol m⁻² s⁻¹"),
    TEMPERATURE("Temperature", Group.ENVIRONMENT, "°C"),
    HUMIDITY("Humidity", Group.ENVIRONMENT, "%"),
    PRESSURE("Pressure", Group.ENVIRONMENT, "hPa"),
    LEAF_TEMP("Leaf Temp", Group.ENVIRONMENT, "°C"),
    ANGLE("Angle", Group.ENVIRONMENT, "°"),

    // Chlorophyll Fluorescence — from PAM sub-protocol
    PHI2("Phi2", Group.FLUORESCENCE),
    PHI_NPQ("PhiNPQ", Group.FLUORESCENCE),
    PHI_NO("PhiNO", Group.FLUORESCENCE),
    NPQT("NPQt", Group.FLUORESCENCE),
    QL("qL", Group.FLUORESCENCE),
    LEF("LEF", Group.FLUORESCENCE, "µmol e⁻ m⁻² s⁻¹"),

    // ECS / PMF — from DIRK_ECS sub-protocol
    ECST("ECSt", Group.ECS, "mAU"),
    VHP("vH+", Group.ECS, "s⁻¹"),
    GHP("gH+", Group.ECS, "s⁻¹"),

    // P700 / PS1 — from PAM sub-protocol (P700 channel from dual-detector trace)
    PS1_ACTIVE("PS1 Active Centers", Group.PS1),
    PS1_OPEN("PS1 Open Centers", Group.PS1),
    PS1_OXIDIZED("PS1 Oxidized Centers", Group.PS1),
    PS1_OVER_REDUCED("PS1 Over-reduced Centers", Group.PS1),

    // Relative Chlorophyll — from SPAD sub-protocol
    SPAD_VALUE("SPAD", Group.SPAD),
    LEAF_THICKNESS("Leaf Thickness", Group.SPAD, "mm");

    enum class Group(val label: String, val subProtocol: String?) {
        ENVIRONMENT("Environment", null),
        FLUORESCENCE("Chlorophyll Fluorescence", "PAM"),
        ECS("ECS / PMF", "DIRK_ECS"),
        PS1("P700 / PS1", "PAM"),          // PS1 data comes from dual-detector PAM trace
        SPAD("Relative Chlorophyll", "SPAD")
    }

    companion object {
        fun fromNames(names: Collection<String>): Set<MultispeqParameter> =
            names.mapNotNull { name -> values().find { it.name == name } }.toSet()

        fun defaultSelection(): Set<MultispeqParameter> =
            values().filter { it.group == Group.ENVIRONMENT }.toSet()

        fun toJsonString(params: Set<MultispeqParameter>): String =
            JSONArray(params.map { it.name }).toString()

        fun fromJsonString(json: String?): Set<MultispeqParameter> {
            if (json.isNullOrBlank()) return emptySet()
            return try {
                val arr = JSONArray(json)
                fromNames((0 until arr.length()).map { arr.getString(it) })
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
}
