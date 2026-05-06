/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wireguard.android.R
import com.wireguard.android.util.TunnelImporter
import kotlinx.coroutines.launch

/**
 * Dialog that lets the user paste or manually type a WireGuard config INI block.
 * Fields ([Interface] / [Peer] sections) are auto-detected from the input text.
 * On "Import", the config is validated and handed to [TunnelImporter].
 */
class PasteConfigDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_paste_config, null)

        val textInputLayout = view.findViewById<TextInputLayout>(R.id.config_text_input_layout)
        val editText = view.findViewById<TextInputEditText>(R.id.config_edit_text)

        // Pre-fill from clipboard if it looks like a WireGuard config
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString()
        if (!clipText.isNullOrBlank() && looksLikeWireGuardConfig(clipText)) {
            editText.setText(clipText)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.paste_config_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.paste_config_import, null) // set below to prevent auto-dismiss on error
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val importButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // Validate on every keystroke — highlight error inline
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    textInputLayout.error = null
                    importButton.isEnabled = !s.isNullOrBlank()
                }
            })
            importButton.isEnabled = !editText.text.isNullOrBlank()

            importButton.setOnClickListener {
                val configText = editText.text?.toString().orEmpty().trim()
                if (configText.isEmpty()) {
                    textInputLayout.error = getString(R.string.paste_config_empty_error)
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    TunnelImporter.importTunnel(parentFragmentManager, configText) { message ->
                        // Show result via the parent fragment's snackbar mechanism
                        (parentFragment as? TunnelListFragment)?.let {
                            it.showSnackbar(message)
                        }
                    }
                }
                dismiss()
            }
        }

        return dialog
    }

    companion object {
        /**
         * Heuristic: a string contains at least one [Interface] or [Peer] section header
         * and at least one key = value pair typical of WireGuard configs.
         */
        fun looksLikeWireGuardConfig(text: String): Boolean {
            val hasSection = text.contains("[Interface]", ignoreCase = true) ||
                text.contains("[Peer]", ignoreCase = true)
            val hasKeyValue = text.contains("PrivateKey", ignoreCase = true) ||
                text.contains("PublicKey", ignoreCase = true) ||
                text.contains("Address", ignoreCase = true) ||
                text.contains("Endpoint", ignoreCase = true)
            return hasSection && hasKeyValue
        }
    }
}
