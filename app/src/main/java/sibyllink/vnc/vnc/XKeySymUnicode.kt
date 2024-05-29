/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.vnc

/**
 * Implements mapping between Unicode code-points and X KeySyms.
 */
object XKeySymUnicode {

    /**
     * Returns X KeySym for [uChar].
     */
    fun getKeySymForUnicodeChar(uChar: Int): Int {
        return if (uChar < 0x100) uChar else uChar + 0x01000000
    }

}