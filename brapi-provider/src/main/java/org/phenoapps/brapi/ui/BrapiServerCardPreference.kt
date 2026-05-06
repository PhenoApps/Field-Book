package org.phenoapps.brapi.ui

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
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.R

open class BrapiServerCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    var account: Account? = null
        set(value) { field = value; notifyChanged() }

    var isActive: Boolean = false
        set(value) { field = value; notifyChanged() }

    private var expandedState by mutableStateOf(false)

    var isExpanded: Boolean
        get() = expandedState
        set(value) { expandedState = value }

    var ownerLabel: String? = null
        set(value) { field = value; notifyChanged() }

    var hasToken: Boolean? = null
        set(value) { field = value; notifyChanged() }

    var onLogOut: ((Account) -> Unit)? = null
    var onAuthorize: ((Account) -> Unit)? = null
    var onEnable: ((Account) -> Unit)? = null
    var onCheckCompatibility: ((Account) -> Unit)? = null
    var onShareSettings: ((Account) -> Unit)? = null
    var onEdit: ((Account) -> Unit)? = null
    var onRemove: ((Account) -> Unit)? = null
    var onSwitchServer: ((Account) -> Unit)? = null

    init {
        layoutResource = R.layout.pheno_brapi_preference_server_card
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val acct = account ?: return
        val am = AccountManager.get(context)

        val displayName = am.getUserData(acct, BrapiAccountConstants.KEY_DISPLAY_NAME)
            ?.takeIf { it.isNotEmpty() } ?: acct.name
        val serverUrl = am.getUserData(acct, BrapiAccountConstants.KEY_SERVER_URL) ?: ""
        val tokenVisible = hasToken ?: false

        (holder.itemView as? ComposeView)?.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
            )
            setContent {
                PhenoBrapiTheme {
                    BrapiServerCard(
                        displayName = displayName,
                        serverUrl = serverUrl,
                        isActive = isActive,
                        hasToken = tokenVisible,
                        isExpanded = expandedState,
                        onToggleExpanded = { expandedState = !expandedState },
                        onEnable = { onEnable?.invoke(acct) },
                        onAuthorize = onAuthorize?.let { action -> { action(acct) } },
                        onLogOut = onLogOut?.let { action -> { action(acct) } },
                        onCheckCompatibility = onCheckCompatibility?.let { action -> { action(acct) } },
                        onShareSettings = onShareSettings?.let { action -> { action(acct) } },
                        onEdit = onEdit?.let { action -> { action(acct) } },
                        onRemove = onRemove?.let { action -> { action(acct) } },
                        onRequestSwitchServer = { onSwitchServer?.invoke(acct) },
                        ownerLabel = ownerLabel,
                    )
                }
            }
        }
    }
}
