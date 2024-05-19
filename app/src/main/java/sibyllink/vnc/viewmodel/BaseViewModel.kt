/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sibyllink.vnc.util.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Base view model.
 */
open class BaseViewModel(val app: Application) : AndroidViewModel(app) {

    val pref by lazy { AppPreferences() }

    /**
     * Launches a new coroutine using [viewModelScope], and executes [block] in that coroutine.
     */
    protected fun launch(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(context) { this.block() }
    }

    protected fun launchMain(block: suspend CoroutineScope.() -> Unit) = launch(Dispatchers.Main, block)
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit) = launch(Dispatchers.IO, block)
}