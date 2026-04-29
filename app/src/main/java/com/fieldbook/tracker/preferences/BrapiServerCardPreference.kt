package com.fieldbook.tracker.preferences

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.fieldbook.tracker.preferences.composables.BrapiServerCard
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * Custom Preference that displays a BrAPI server card.
 *
 * Shows the server's display name, URL, and a radio button indicating the active server.
 * Status is conveyed via a lock (authenticated) or unlock (unauthenticated) icon in the header.
 * The BrAPI logo in the header acts as the server selector: tapping it on an inactive server
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

    private var _isExpanded by mutableStateOf(false)

    /** Whether the card's chips and status icon are currently shown. */
    var isExpanded: Boolean
        get() = _isExpanded
        set(value) { _isExpanded = value }

    var onLogOut: ((Account) -> Unit)? = null
    var onAuthorize: ((Account) -> Unit)? = null
    var onEnable: ((Account) -> Unit)? = null
    var onCheckCompatibility: ((Account) -> Unit)? = null
    var onShareSettings: ((Account) -> Unit)? = null
    var onEdit: ((Account) -> Unit)? = null
    var onRemove: ((Account) -> Unit)? = null
    var onSwitchServer: ((Account) -> Unit)? = null

    init {
        layoutResource = com.fieldbook.tracker.R.layout.preference_brapi_server_card
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val acct = account ?: return
        val am = AccountManager.get(context)

        val displayName = am.getUserData(acct, BrapiAuthenticator.KEY_DISPLAY_NAME)
            ?.takeIf { it.isNotEmpty() } ?: acct.name
        val serverUrl = am.getUserData(acct, BrapiAuthenticator.KEY_SERVER_URL) ?: ""
        val hasToken = !am.peekAuthToken(acct, BrapiAuthenticator.AUTH_TOKEN_TYPE).isNullOrEmpty()

        val composeView = holder.itemView as? ComposeView ?: return
        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                AppTheme {
                    BrapiServerCard(
                        displayName = displayName,
                        serverUrl = serverUrl,
                        isActive = isActive,
                        hasToken = hasToken,
                        isExpanded = _isExpanded,
                        onToggleExpanded = { _isExpanded = !_isExpanded },
                        onEnable = { onEnable?.invoke(acct) },
                        onAuthorize = { onAuthorize?.invoke(acct) },
                        onLogOut = { onLogOut?.invoke(acct) },
                        onCheckCompatibility = { onCheckCompatibility?.invoke(acct) },
                        onShareSettings = { onShareSettings?.invoke(acct) },
                        onEdit = { onEdit?.invoke(acct) },
                        onRemove = { onRemove?.invoke(acct) },
                        onRequestSwitchServer = { onSwitchServer?.invoke(acct) },
                    )
                }
            }
        }
    }
}
