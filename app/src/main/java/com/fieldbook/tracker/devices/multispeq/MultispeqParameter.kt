package com.fieldbook.tracker.devices.multispeq

import org.json.JSONArray

/**
 * Enumeration of all calculatable output parameters.
 *
 * Every parameter comes from the RIDES 2.1 protocol; [MultispeqProtocolType.select] picks a
 * faster protocol when the selection allows it. [Group] is only used to group parameters for display.
 */
enum class MultispeqParameter(
    val displayName: String,
    val group: Group,
    val unit: String = ""
) {
    // Environment
    PAR("PAR", Group.ENVIRONMENT, "µmol m⁻² s⁻¹"),
    TEMPERATURE("Temperature", Group.ENVIRONMENT, "°C"),
    HUMIDITY("Humidity", Group.ENVIRONMENT, "%"),
    PRESSURE("Pressure", Group.ENVIRONMENT, "hPa"),
    LEAF_TEMP("Leaf Temp", Group.ENVIRONMENT, "°C"),
    ANGLE("Angle", Group.ENVIRONMENT, "°"),

    // Chlorophyll Fluorescence — from the PAM sub-protocol
    PHI2("Phi2", Group.FLUORESCENCE),
    PHI_NPQ("PhiNPQ", Group.FLUORESCENCE),
    PHI_NO("PhiNO", Group.FLUORESCENCE),
    NPQT("NPQt", Group.FLUORESCENCE),
    QL("qL", Group.FLUORESCENCE),
    LEF("LEF", Group.FLUORESCENCE, "µmol e⁻ m⁻² s⁻¹"),

    // ECS / PMF — from the DIRK_ECS sub-protocol
    ECST("ECSt", Group.ECS, "mAU"),
    VHP("vH+", Group.ECS, "s⁻¹"),
    GHP("gH+", Group.ECS, "s⁻¹"),

    // P700 / PS1 — from the P700 channel of the dual-detector PAM trace
    PS1_ACTIVE("PS1 Active Centers", Group.PS1),
    PS1_OPEN("PS1 Open Centers", Group.PS1),
    PS1_OXIDIZED("PS1 Oxidized Centers", Group.PS1),
    PS1_OVER_REDUCED("PS1 Over-reduced Centers", Group.PS1),

    // Relative Chlorophyll
    SPAD_VALUE("SPAD", Group.SPAD),
    LEAF_THICKNESS("Leaf Thickness", Group.SPAD, "mm");

    enum class Group(val label: String) {
        ENVIRONMENT("Environment"),
        FLUORESCENCE("Chlorophyll Fluorescence"),
        ECS("ECS / PMF"),
        PS1("P700 / PS1"),
        SPAD("Relative Chlorophyll")
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
