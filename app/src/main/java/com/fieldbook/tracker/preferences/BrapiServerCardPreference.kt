package com.fieldbook.tracker.preferences

import android.accounts.Account
import android.accounts.AccountManager
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Custom Preference that displays a BrAPI server card.
 *
 * Shows the server's display name, URL, and a radio button indicating the active server.
 * Status is conveyed via a lock (authenticated) or unlock (unauthenticated) icon in the header.
 * The radio button in the header acts as the server selector: tapping it on an inactive server
 * activates it (or triggers auth if unauthenticated).
 * Action chips: Compatibility, Share, Edit, and Logout/Authorize.
 *
 * Tapping anywhere in the header row (except the logo) collapses/expands the chips and
 * auth status icon. Active cards start expanded; inactive cards start collapsed.
 *
 * Bind [account], [isActive], and the various action listeners before attaching to a
 * PreferenceCategory.
 */
class BrapiServerCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    var account: Account? = null
        set(value) { field = value; notifyChanged() }

    /** True if this card is the currently active (Connected) server. */
    var isActive: Boolean = false
        set(value) { field = value; notifyChanged() }

    /** Whether the card's chips and status icon are currently shown. */
    var isExpanded: Boolean = false

    var onLogOut: ((Account) -> Unit)? = null
    var onAuthorize: ((Account) -> Unit)? = null
    var onEnable: ((Account) -> Unit)? = null
    var onCheckCompatibility: ((Account) -> Unit)? = null
    var onShareSettings: ((Account) -> Unit)? = null
    var onEdit: ((Account) -> Unit)? = null
    var onRemove: ((Account) -> Unit)? = null

    init {
        layoutResource = R.layout.preference_brapi_server_card
        isSelectable = false
        runCatching {
            Preference::class.java.getDeclaredField("mAllowDividerBelow")
                .also { it.isAccessible = true }
                .setBoolean(this, false)
        }.onFailure {
            Log.w("BrapiServerCard", "Could not suppress divider via reflection; dividers may appear between cards", it)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val acct = account ?: return
        val am = AccountManager.get(context)

        val headerRow = holder.findViewById(R.id.brapi_card_header_row) as? LinearLayout
        val displayNameTv = holder.findViewById(R.id.brapi_card_display_name) as? TextView
        val urlTv = holder.findViewById(R.id.brapi_card_url) as? TextView
        val statusIcon = holder.findViewById(R.id.brapi_card_status_icon) as? ImageView
        val chevron = holder.findViewById(R.id.brapi_card_chevron) as? ImageView
        val logoView = holder.findViewById(R.id.brapi_card_logo) as? ImageView
        val chipGroup = holder.findViewById(R.id.brapi_card_chip_group) as? ChipGroup
        val chipCompat = holder.findViewById(R.id.brapi_card_chip_compat) as? Chip
        val chipShare = holder.findViewById(R.id.brapi_card_chip_share) as? Chip
        val chipEdit = holder.findViewById(R.id.brapi_card_chip_edit) as? Chip
        val chipAuth = holder.findViewById(R.id.brapi_card_chip_auth) as? Chip
        val chipRemove = holder.findViewById(R.id.brapi_card_chip_remove) as? Chip

        val displayName = am.getUserData(acct, BrapiAuthenticator.KEY_DISPLAY_NAME)
            ?.takeIf { it.isNotEmpty() } ?: acct.name
        val serverUrl = am.getUserData(acct, BrapiAuthenticator.KEY_SERVER_URL) ?: ""

        displayNameTv?.text = displayName
        urlTv?.text = serverUrl

        val hasToken = !am.peekAuthToken(acct, BrapiAuthenticator.AUTH_TOKEN_TYPE).isNullOrEmpty()

        // ── BrAPI logo (server selector) ──────────────────────────────────
        logoView?.setImageResource(if (isActive) R.drawable.brapi_logo else R.drawable.brapi_logo_grayscale)
        if (isActive) {
            logoView?.isClickable = false
            logoView?.isFocusable = false
        } else {
            logoView?.isClickable = true
            logoView?.isFocusable = true
            logoView?.setOnClickListener {
                if (hasToken) {
                    onEnable?.invoke(acct)
                } else {
                    AlertDialog.Builder(context, R.style.AppAlertDialog)
                        .setTitle(R.string.brapi_switch_server_title)
                        .setItems(
                            arrayOf(
                                context.getString(R.string.brapi_authenticate),
                                context.getString(R.string.brapi_use_as_is)
                            )
                        ) { _, which ->
                            if (which == 0) onAuthorize?.invoke(acct) else onEnable?.invoke(acct)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }

        // ── Status icon (lock / unlock) ────────────────────────────────────
        when {
            isActive && hasToken -> {
                statusIcon?.setImageResource(R.drawable.ic_tb_lock)
                statusIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#4CAF50"), PorterDuff.Mode.SRC_IN)
            }
            isActive && !hasToken -> {
                statusIcon?.setImageResource(R.drawable.ic_tb_unlock)
                statusIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#F44336"), PorterDuff.Mode.SRC_IN)
            }
            hasToken -> {
                statusIcon?.setImageResource(R.drawable.ic_tb_lock)
                statusIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#454545"), PorterDuff.Mode.SRC_IN)
            }
            else -> {
                statusIcon?.setImageResource(R.drawable.ic_tb_unlock)
                statusIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#454545"), PorterDuff.Mode.SRC_IN)
            }
        }

        // ── Chip stroke color for available servers ──────────────────────
        if (!isActive) {
            val availableStroke = ColorStateList.valueOf(Color.parseColor("#454545"))
            listOf(chipCompat, chipShare, chipEdit, chipAuth, chipRemove).forEach { chip ->
                chip?.chipStrokeColor = availableStroke
            }
        }

        // ── Action chips ───────────────────────────────────────────────────
        chipCompat?.setOnClickListener { onCheckCompatibility?.invoke(acct) }
        chipShare?.setOnClickListener { onShareSettings?.invoke(acct) }
        chipEdit?.setOnClickListener { onEdit?.invoke(acct) }

        if (hasToken) {
            chipAuth?.text = context.getString(R.string.brapi_chip_logout)
            chipAuth?.chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_pref_brapi_logout)
            chipAuth?.setOnClickListener { onLogOut?.invoke(acct) }
            chipRemove?.visibility = View.GONE
        } else {
            chipAuth?.text = context.getString(R.string.brapi_chip_authorize)
            chipAuth?.chipIcon = ContextCompat.getDrawable(context, R.drawable.key)
            chipAuth?.setOnClickListener { onAuthorize?.invoke(acct) }
            chipRemove?.visibility = View.VISIBLE
            chipRemove?.setOnClickListener { onRemove?.invoke(acct) }
        }

        // ── Expand / collapse ──────────────────────────────────────────────
        applyExpansionState(chipGroup, statusIcon, chevron, animated = false)

        headerRow?.setOnClickListener {
            isExpanded = !isExpanded
            applyExpansionState(chipGroup, statusIcon, chevron, animated = true)
        }
    }

    private fun applyExpansionState(
        chipGroup: ChipGroup?,
        statusIcon: ImageView?,
        chevron: ImageView?,
        animated: Boolean
    ) {
        chipGroup?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        // Status icon is always visible
        statusIcon?.visibility = View.VISIBLE

        // ic_chevron_down at 0° = pointing down (collapsed); 180° = pointing up (expanded)
        val targetRotation = if (isExpanded) 180f else 0f
        if (animated && chevron != null) {
            ViewCompat.animate(chevron).rotation(targetRotation).setDuration(200).start()
        } else {
            chevron?.rotation = targetRotation
        }
    }
}
