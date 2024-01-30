/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.SimpleAdapter
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentProfileEditorAdvancedBinding
import com.gaurav.avnc.databinding.FragmentProfileEditorBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.util.OpenableDocument
import com.gaurav.avnc.viewmodel.EditorViewModel
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.gaurav.avnc.viewmodel.isPrivateKeyEncrypted
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/********************************************************************************
 * ServerProfile editor. There are two modes:
 *
 * Simple:
 * A simple Dialog with most common options.
 *
 * Advanced:
 * A fullscreen fragment attached to content root.
 * All available options are shown in this mode.
 *
 *******************************************************************************/

private const val PROFILE_KEY = "profile"

fun startProfileEditor(host: FragmentActivity, profile: ServerProfile, preferAdvancedEditor: Boolean) {
    if (preferAdvancedEditor)
        startAdvancedProfileEditor(host, profile)
    else
        startSimpleProfileEditor(host, profile)
}

private fun startSimpleProfileEditor(host: FragmentActivity, profile: ServerProfile) {
    SimpleProfileEditor().apply {
        arguments = Bundle().apply { putParcelable(PROFILE_KEY, profile) }
        show(host.supportFragmentManager, null)
    }
}

private fun startAdvancedProfileEditor(host: FragmentActivity, profile: ServerProfile) {
    val fragment = AdvancedProfileEditor().apply {
        arguments = Bundle().apply { putParcelable(PROFILE_KEY, profile) }
    }

    host.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
}

/**
 * Extracts [ServerProfile] passed to given fragment via argument.
 */
private fun getProfileArg(f: Fragment): ServerProfile {
    return f.arguments?.let { BundleCompat.getParcelable(it, PROFILE_KEY, ServerProfile::class.java) }
           ?: ServerProfile()
}

/**
 * Returns title string based on whether we are editing an existing profile, or creating a new profile.
 */
private fun getTitle(f: Fragment): Int {
    return if (getProfileArg(f).ID == 0L) R.string.title_add_server_profile
    else R.string.title_edit_server_profile
}


/**
 * If [preCondition] is `true`, validates that [target] is not empty.
 */
private fun validateNotEmpty(target: EditText, preCondition: Boolean = true, msg: String = "Required"): Boolean {
    if (preCondition && target.length() == 0) {
        target.error = msg
        return false
    }
    return true
}


/********************************************************************************
 * Simple mode
 *******************************************************************************/

class SimpleProfileEditor : DialogFragment() {
    private val homeViewModel by activityViewModels<HomeViewModel>()
    private val profile by lazy { getProfileArg(this) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentProfileEditorBinding.inflate(layoutInflater, null, false)
        binding.profile = profile
        binding.lifecycleOwner = this
        isCancelable = false

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog_Dimmed)
                .setTitle(getTitle(this))
                .setView(binding.root)
                .setPositiveButton(R.string.title_save) { _, _ -> /* See below */ }
                .setNegativeButton(R.string.title_cancel) { _, _ -> dismiss() }
                .setNeutralButton(R.string.title_advanced) { _, _ -> startAdvancedProfileEditor(requireActivity(), profile) }
                .setBackgroundInsetTop(0)
                .setBackgroundInsetBottom(0)
                .create()

        // Customize Save button directly to avoid Dialog dismissal if validation fails
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateNotEmpty(binding.host) and validateNotEmpty(binding.port)) {
                    homeViewModel.saveProfile(profile)
                    dismiss()
                }
            }
        }

        return dialog
    }
}


/********************************************************************************
 * Advanced mode
 *******************************************************************************/

class AdvancedProfileEditor : Fragment() {
    private val homeViewModel by activityViewModels<HomeViewModel>()
    private val viewModel by viewModels<EditorViewModel> { EditorViewModelFactory(this) }
    private lateinit var binding: FragmentProfileEditorAdvancedBinding
    private val keyFilePicker = registerForActivityResult(OpenableDocument()) { importPrivateKey(it) }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentProfileEditorAdvancedBinding.inflate(inflater, container, false)
        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = this@AdvancedProfileEditor.viewModel
            toolbar.title = getString(getTitle(this@AdvancedProfileEditor))

            saveBtn.setOnClickListener { save() }
            toolbar.setNavigationOnClickListener { dismiss() }
            keyImportBtn.setOnClickListener { keyFilePicker.launch(arrayOf("*/*")) }


            // TODO Move it to proper place
            val securityTypes = mapOf(
                    getString(R.string.title_automatic) to 0,
                    getString(R.string.title_none) to 1,
                    "VncAuth" to 2,
                    "AnonTLS" to 18,
                    "VeNCrypt" to 19
            )

            val p = this@AdvancedProfileEditor.viewModel.profile
            security.setEntries(securityTypes, p.securityType) { p.securityType = it }

            //Setup Gesture Style
            val gestureStyleItems = listOf(
                    mapOf("name" to getString(R.string.pref_gesture_style_auto),
                          "description" to getString(R.string.pref_gesture_style_auto_summary),
                          "value" to "auto"),
                    mapOf("name" to getString(R.string.pref_gesture_style_touchscreen),
                          "description" to getString(R.string.pref_gesture_style_touchscreen_summary),
                          "value" to "touchscreen"),
                    mapOf("name" to getString(R.string.pref_gesture_style_touchpad),
                          "description" to getString(R.string.pref_gesture_style_touchpad_summary),
                          "value" to "touchpad"),
            )

            val adapter = SimpleAdapter(requireContext(), gestureStyleItems, android.R.layout.simple_list_item_1,
                                        arrayOf("name", "description"), intArrayOf(android.R.id.text1, android.R.id.text2))

            adapter.setDropDownViewResource(android.R.layout.simple_list_item_2)
            gestureStyle.adapter = adapter
            gestureStyle.setSelection(gestureStyleItems.indexOfFirst { it["value"] == p.gestureStyle })
            gestureStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    p.gestureStyle = gestureStyleItems[position]["value"]!!
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Screen orientation spinner
            val orientations = mapOf(
                    getString(R.string.pref_orientation_option_auto) to "auto",
                    getString(R.string.pref_orientation_option_portrait) to "portrait",
                    getString(R.string.pref_orientation_option_landscape) to "landscape"
            )
            screenOrientation.setEntries(orientations.keys.toTypedArray(), orientations.values.toTypedArray(), p.screenOrientation) { p.screenOrientation = it }

            setupHelpButton(keyCompatModeHelpBtn, R.string.title_key_compat_mode, R.string.msg_key_compat_mode_help)
            setupHelpButton(buttonUpDelayHelpBtn, R.string.title_button_up_delay, R.string.msg_button_up_delay_help)
        }

        return binding.root
    }

    private fun setupHelpButton(button: View, title: Int, msg: Int) {
        button.setOnClickListener {
            MsgDialog.show(parentFragmentManager, getString(title), getString(msg))
        }
    }

    private fun dismiss() = parentFragmentManager.popBackStack()

    private fun save() {
        if (!validate()) return
        homeViewModel.saveProfile(viewModel.prepareProfileForSave())
        dismiss()
    }

    private fun validate(): Boolean {
        var result = validateNotEmpty(binding.host) and
                validateNotEmpty(binding.port) and
                validateNotEmpty(binding.idOnRepeater, binding.useRepeater.isChecked)

        if (binding.useSshTunnel.isChecked) {
            result = result and
                    validateNotEmpty(binding.sshHost) and
                    validateNotEmpty(binding.sshUsername) and
                    validatePrivateKey()
        }

        return result
    }


    private fun validatePrivateKey(): Boolean {
        if (binding.sshAuthTypeKey.isChecked && viewModel.hasSshPrivateKey.value != true) {
            binding.keyImportBtn.error = "Required"
            return false
        }
        return true
    }


    private fun importPrivateKey(uri: Uri?) {
        if (uri == null)
            return

        lifecycleScope.launch(Dispatchers.IO) {
            var key = ""
            var encrypted = false
            val result = runCatching {
                requireContext().contentResolver.openAssetFileDescriptor(uri, "r")!!.use {
                    // Valid key files are only few KBs. So if selected file is too big,
                    // user has accidentally selected something else.
                    check(it.length < 2 * 1024 * 1024) { "File is too big [${it.length}]" }
                    key = it.createInputStream().use { s -> s.reader().use { r -> r.readText() } }
                }
                encrypted = isPrivateKeyEncrypted(key)
            }

            withContext(Dispatchers.Main) {
                result.onSuccess {
                    viewModel.profile.sshPrivateKey = key
                    viewModel.isPrivateKeyEncrypted.value = encrypted
                    viewModel.hasSshPrivateKey.value = true
                    binding.keyImportBtn.error = null
                    Snackbar.make(binding.root, R.string.msg_imported, Snackbar.LENGTH_SHORT).show()
                }.onFailure {
                    MsgDialog.show(parentFragmentManager, getString(R.string.msg_invalid_key_file), it.message ?: "")
                    Log.e("ProfileEditor", "Error importing Private Key", it)
                }
            }
        }
    }

    private class EditorViewModelFactory(private val fragment: Fragment) : AbstractSavedStateViewModelFactory(fragment, null) {
        override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
            val app = fragment.requireActivity().application
            val profile = getProfileArg(fragment)
            return modelClass.cast(EditorViewModel(app, handle, profile))!!
        }
    }
}