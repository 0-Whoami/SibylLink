/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.model

/**
 * Generic wrapper for login information.
 * This can be used to hold different [Type]s of credentials.
 */
data class LoginInfo(
        var name: String = "", // Profile name
        var host: String = "",
        var username: String = "",
        var password: String = "",
) {
    enum class Type {
        VNC_PASSWORD,
        VNC_CREDENTIAL  // Username & Password
    }
}