package org.phenoapps.brapi

object BrapiAccountConstants {
    /**
     * Namespace every app's BrAPI account type sits under.
     *
     * Android registers exactly one authenticator package per account type, and admits writes to
     * it only from callers whose signature matches that package's. Sibling PhenoApps ship under
     * separate signing keys, so a single shared type would let exactly one of them manage BrAPI
     * accounts and lock the rest out entirely. Each app therefore owns [accountTypeFor] its own
     * package, and cross-app sharing works by discovering the others under this prefix.
     */
    const val ACCOUNT_TYPE_PREFIX = "org.phenoapps.brapi"

    /**
     * The single account type used before per-app types.
     *
     * Only apps co-signed with whichever one claimed it could ever write to it, which is why it
     * appeared to work while every app was built with the shared debug key. Accounts left under
     * it are read and migrated out, never written to.
     */
    const val LEGACY_ACCOUNT_TYPE = ACCOUNT_TYPE_PREFIX

    const val AUTH_TOKEN_TYPE = "access_token"

    /** Suffix appended to a package name to form its config provider authority. */
    const val CONFIG_AUTHORITY_SUFFIX = ".brapi.config"

    /** The BrAPI account type owned by [packageName]. */
    fun accountTypeFor(packageName: String): String = "$ACCOUNT_TYPE_PREFIX.$packageName"

    /** The config provider authority exposed by [packageName]. */
    fun configAuthorityFor(packageName: String): String = "$packageName$CONFIG_AUTHORITY_SUFFIX"

    /** Whether [accountType] is a BrAPI account type owned by some app rather than the legacy one. */
    fun isPerAppAccountType(accountType: String?): Boolean =
        accountType != null && accountType.startsWith("$ACCOUNT_TYPE_PREFIX.")

    // AccountManager user-data keys
    const val KEY_ID_TOKEN = "id_token"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_OIDC_URL = "oidc_url"
    const val KEY_OIDC_FLOW = "oidc_flow"
    const val KEY_OIDC_CLIENT_ID = "oidc_client_id"
    const val KEY_OIDC_SCOPE = "oidc_scope"
    const val KEY_BRAPI_VERSION = "brapi_version"
    const val KEY_OWNER_PACKAGE = "owner_package"
    const val OPTION_SHARED_ACCOUNT_CHOOSER = "org.phenoapps.brapi.option.SHARED_ACCOUNT_CHOOSER"
    const val EXTRA_SHOW_IN_APP_ADDER_TOAST = "org.phenoapps.brapi.extra.SHOW_IN_APP_ADDER_TOAST"

    // Grant key prefix — stored per calling package: "grant_<package>" = "true"
    const val GRANT_KEY_PREFIX = "grant_"

    // Stable identifiers for KEY_OIDC_FLOW. These are persisted and compared, so they must never
    // be localized — earlier versions stored the picker's display label, which meant the value
    // changed with the app language and stopped matching.
    const val OIDC_FLOW_OAUTH_CODE = "oauth_code"
    const val OIDC_FLOW_OAUTH_IMPLICIT = "oauth_implicit"

    /**
     * Resolves a stored OIDC flow to one of the stable identifiers above.
     *
     * Accepts the stable ids, and the legacy English display labels written before those ids
     * existed. Labels stored in a language other than English are not recognised and fall back
     * to the authorization-code flow, which is both the safer option and the current default;
     * re-selecting the flow on the account rewrites it in the stable form.
     */
    fun normalizeOidcFlow(rawFlow: String?): String = when (rawFlow?.trim()) {
        OIDC_FLOW_OAUTH_IMPLICIT, LEGACY_LABEL_OAUTH_IMPLICIT -> OIDC_FLOW_OAUTH_IMPLICIT
        else -> OIDC_FLOW_OAUTH_CODE
    }

    // English labels persisted by versions before the stable ids above. Deliberately hardcoded
    // rather than read from resources: the resource text is translated, and these must keep
    // matching what old installs actually wrote to disk.
    private const val LEGACY_LABEL_OAUTH_IMPLICIT = "OAuth2 Implicit Grant"

    /**
     * The OIDC flow value to publish in a shared config (the QR transfer payload).
     *
     * Deliberately the spec-style spelling rather than the internal identifier: versions that
     * predate those identifiers already understand "code" and "implicit" and map them correctly,
     * whereas an unrecognised value silently falls back to the authorization-code flow. Emitting
     * these keeps a config generated here readable by older Field Book installs and by sibling
     * apps that haven't picked up this module yet.
     */
    fun toSharedConfigOidcFlow(rawFlow: String?): String =
        if (normalizeOidcFlow(rawFlow) == OIDC_FLOW_OAUTH_IMPLICIT) "implicit" else "code"

    // PhenoApps packages that may discover and use BrAPI accounts
    val ALLOWED_PACKAGES = setOf(
        "com.fieldbook.tracker",
        "com.fieldbook.tracker.debug",
        "org.wheatgenetics.coordinate",
        "org.wheatgenetics.coordinate.debug",
        "org.phenoapps.intercross",
        "org.phenoapps.intercross.debug",
    )

    private val PACKAGE_DISPLAY_NAMES = mapOf(
        "com.fieldbook.tracker" to "Field Book",
        "com.fieldbook.tracker.debug" to "Field Book",
        "org.wheatgenetics.coordinate" to "Coordinate",
        "org.wheatgenetics.coordinate.debug" to "Coordinate",
        "org.phenoapps.intercross" to "Intercross",
        "org.phenoapps.intercross.debug" to "Intercross",
    )

    fun isPackageAllowed(packageName: String?): Boolean =
        !packageName.isNullOrEmpty() && packageName in ALLOWED_PACKAGES

    fun canPackageAccessAccount(ownerPackage: String?, packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (ownerPackage.isNullOrEmpty()) return false
        if (ownerPackage == packageName) return true
        return isPackageAllowed(ownerPackage) && isPackageAllowed(packageName)
    }

    fun displayNameForPackage(packageName: String?): String =
        packageName?.let { PACKAGE_DISPLAY_NAMES[it] ?: it }.orEmpty()

    fun grantedPackageKey(packageName: String): String = "$GRANT_KEY_PREFIX$packageName"
}
