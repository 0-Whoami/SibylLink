
package sibyllink.vnc.vnc

/**
 * This class is used for returning user credentials from callbacks.
 */
data class UserCredential(
        @JvmField val username: String = "",
        @JvmField val password: String = ""
)