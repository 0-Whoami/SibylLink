/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.util

/**
 * Utility class for accessing app preferences
 */
class AppPreferences {

    inner class UI {
        val sortServerList = false
    }

    inner class Viewer {
        val keepScreenOn = true
        val zoomMax; get() = 5f
        val zoomMin; get() = 0.5f
        val toolbarShowGestureStyleToggle = true
    }

    inner class Gesture {
        val swipeSensitivity = 1f
    }

    inner class Input {
        val gesture = Gesture()

        val vkOpenWithKeyboard=false
        val vkShowAll=true

        val hideLocalCursor=false
        val hideRemoteCursor=false

        val kmLanguageSwitchToSuper=false
        val kmRightAltToSuper=false
        val kmBackToEscape=false
    }

    inner class Server {
        val lockSavedServer=false
        val autoReconnect=true
    }

    val ui = UI()
    val viewer = Viewer()
    val input = Input()
    val server = Server()
}