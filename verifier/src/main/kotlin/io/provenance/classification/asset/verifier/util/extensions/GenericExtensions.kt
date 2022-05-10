package io.provenance.classification.asset.verifier.util.extensions

/**
 * The functional equivalent of an elvis operator, allowing default values to be derived when an elvis operator
 * would cause weird-looking syntax, like parenthetical enclosures that unnecessarily obfuscate meaning.
 */
internal fun <T> T?.elvis(default: () -> T): T = this ?: default()

internal fun <T> T.alsoIf(condition: Boolean, action: () -> Unit): T = this.also {
    if (condition) {
        action.invoke()
    }
}
