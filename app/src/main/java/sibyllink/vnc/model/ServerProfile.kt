
package sibyllink.vnc.model

import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * This class holds connection configuration of a remote VNC server.
 *
 * Some fields remain unused until that feature is implemented.
 */
class ServerProfile(
    val id: Int,
    /**
     * Internet address of the server (without port number).
     * This can be hostname or IP address.
     */
    var host: String = "",

    /**
     * Port number of the server.
     */
    var port: Int = 5900,

    /**
     * Username used for authentication.
     */
    var username: String = "",

    /**
     * Password used for authentication.
     * Note: Username & password may not be used for all security types.
     */
    var password: String = "",

    /**
     * Security type to use when connecting to this server (e.g. VncAuth).
     * 0 enables all supported types.
     */
    var securityType: Int = 0,
    /**
     * Specifies the image quality of the frames.
     * This mainly affects the compression level used by some encodings.
     */
    var imageQuality: Int = 5,

    /**
     * Use raw encoding for framebuffer.
     * This can improve performance when server is running on localhost.
     */
    var useRawEncoding: Boolean = false,

    /**
     * Initial zoom for the viewer.
     * This will be used in portrait orientation, or when per-orientation zooming is disabled.
     */
    var zoom1: Float = 1f,

    /**
     * Specifies whether 'View Only' mode should be used.
     * In this mode client does not send any input messages to remote server.
     */
    var viewOnly: Boolean = false,


    /**
     * Whether the cursor should be drawn by client instead of server.
     * It's value is currently ignored, and hardcoded to true.
     * See [sibyllink.vnc.viewmodel.VncViewModel.preConnect]
     */
    var useLocalCursor: Boolean = false,

    /**
     * Whether UltraVNC Repeater is used for connections.
     * When repeater is used, [host] & [port] identifies the repeater.
     */
    var useRepeater: Boolean = false,

    /**
     * When using a repeater, this value identifies the VNC server.
     * Valid IDs: [0, 999999999].
     */
    var idOnRepeater: Int = 0,

    /**
     * Resize remote desktop to match with local window size.
     */
    var resizeRemoteDesktop: Boolean = false

) : Serializable
