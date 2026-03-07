package com.fieldbook.tracker.preferences

import android.accounts.Account
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.BrapiAuthenticator

/**
 * Custom Preference that displays a BrAPI server card.
 *
 * Shows the server's display name, URL, connection status, and action buttons (Log Out, Enable).
 * The three-dot overflow menu contains: Check Compatibility, Share Settings, Edit, Remove.
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
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val acct = account ?: return
        val am = android.accounts.AccountManager.get(context)

        val displayNameTv = holder.findViewById(R.id.brapi_card_display_name) as? TextView
        val urlTv = holder.findViewById(R.id.brapi_card_url) as? TextView
        val statusDot = holder.findViewById(R.id.brapi_card_status_dot)
        val statusTv = holder.findViewById(R.id.brapi_card_status_text) as? TextView
        val logoutBtn = holder.findViewById(R.id.brapi_card_logout_btn) as? Button
        val enableBtn = holder.findViewById(R.id.brapi_card_enable_btn) as? Button
        val overflowBtn = holder.findViewById(R.id.brapi_card_overflow_btn)

        val displayName = am.getUserData(acct, BrapiAuthenticator.KEY_DISPLAY_NAME)
            ?.takeIf { it.isNotEmpty() } ?: acct.name
        val serverUrl = am.getUserData(acct, BrapiAuthenticator.KEY_SERVER_URL) ?: ""

        displayNameTv?.text = displayName
        urlTv?.text = serverUrl

        val hasToken = !am.peekAuthToken(acct, BrapiAuthenticator.AUTH_TOKEN_TYPE).isNullOrEmpty()

        when {
            isActive -> {
                statusDot?.setBackgroundResource(R.drawable.circle_green)
                statusTv?.text = context.getString(R.string.brapi_card_status_connected)
                logoutBtn?.visibility = View.VISIBLE
                logoutBtn?.text = context.getString(R.string.brapi_revoke_auth)
                logoutBtn?.setOnClickListener { onLogOut?.invoke(acct) }
                enableBtn?.visibility = View.GONE
            }
            hasToken -> {
                statusDot?.setBackgroundResource(R.drawable.circle_blue)
                statusTv?.text = context.getString(R.string.brapi_card_status_authorized)
                logoutBtn?.visibility = View.VISIBLE
                logoutBtn?.text = context.getString(R.string.brapi_revoke_auth)
                logoutBtn?.setOnClickListener { onLogOut?.invoke(acct) }
                enableBtn?.visibility = View.VISIBLE
                enableBtn?.setOnClickListener { onEnable?.invoke(acct) }
            }
            else -> {
                // No token — unauthenticated
                statusDot?.setBackgroundResource(R.drawable.circle_red)
                statusTv?.text = context.getString(R.string.brapi_card_status_unauthenticated)
                logoutBtn?.visibility = View.VISIBLE
                logoutBtn?.text = context.getString(R.string.brapi_card_authorize)
                logoutBtn?.setOnClickListener { onAuthorize?.invoke(acct) }
                enableBtn?.visibility = View.GONE
            }
        }

        overflowBtn?.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            popup.menuInflater.inflate(R.menu.menu_brapi_server_card, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.brapi_card_menu_check_compatibility -> {
                        onCheckCompatibility?.invoke(acct); true
                    }
                    R.id.brapi_card_menu_share_settings -> {
                        onShareSettings?.invoke(acct); true
                    }
                    R.id.brapi_card_menu_edit -> {
                        onEdit?.invoke(acct); true
                    }
                    R.id.brapi_card_menu_remove -> {
                        onRemove?.invoke(acct); true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
