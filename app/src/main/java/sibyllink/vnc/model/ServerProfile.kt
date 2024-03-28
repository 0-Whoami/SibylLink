/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.model

import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * This class holds connection configuration of a remote VNC server.
 *
 * Some fields remain unused until that feature is implemented.
 */
data class ServerProfile(
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
         * Transport channel to be used for communicating with the server.
         * e.g. TCP, SSH Tunnel
         */
        var channelType: Int = CHANNEL_TCP,

        /**
         * Specifies the color level of received frames.
         * This value determines the pixel-format used for framebuffer.
         */
        var colorLevel: Int = 0,

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
        var useLocalCursor: Boolean = true,

        /**
         * Server type hint received from user, e.g. tigervnc, tightvnc, vino
         * Can be used in future to handle known server quirks.
         */
        var serverTypeHint: String = "",

        /**
         * Composite field for various flags.
         * This is accessed via individual members like [fLegacyKeySym].
         */
        var flags: Long = FLAG_LEGACY_KEYSYM,

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

) : Serializable {

    companion object {
        // Channel types (from RFC 7869)
        const val CHANNEL_TCP = 1



        // Flag masks
        private const val FLAG_LEGACY_KEYSYM = 0x01L
        private const val FLAG_BUTTON_UP_DELAY = 0x02L
        private const val FLAG_ZOOM_LOCKED = 0x04L
    }

    /**
     * Delegated property builder for [flags] field.
     */
    private class Flag(val flag: Long):Serializable {
        operator fun getValue(p: ServerProfile, kp: KProperty<*>) = (p.flags and flag) != 0L
        operator fun setValue(p: ServerProfile, kp: KProperty<*>, value: Boolean) {
            p.flags = if (value) p.flags or flag else p.flags and flag.inv()
        }
    }

    /**
     * Flag to emit legacy X KeySym events in certain cases.
     */
    var fLegacyKeySym by Flag(FLAG_LEGACY_KEYSYM)

    /**
     * Flag to insert artificial delay before UP event of left-click.
     */
    var fButtonUpDelay by Flag(FLAG_BUTTON_UP_DELAY)

    /**
     * If zoom is locked, user requests to change [zoom1] & [zoom2]
     * should be ignored.
     */
    var fZoomLocked by Flag(FLAG_ZOOM_LOCKED)

}
