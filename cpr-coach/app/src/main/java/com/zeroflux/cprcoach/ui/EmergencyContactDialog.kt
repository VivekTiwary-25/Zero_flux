package com.zeroflux.cprcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Small dialog to type or pick the emergency contact. Saving never calls anyone: the contact
 * button on the Emergency screen only opens the dialer.
 */
@Composable
fun EmergencyContactDialog(
    current: EmergencyContact?,
    onSave: (EmergencyContact?) -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var name by rememberSaveable(current) { mutableStateOf(current?.name.orEmpty()) }
    var number by rememberSaveable(current) { mutableStateOf(current?.phoneNumber.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.get("home_emergency_contact")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.get("contact_name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.get("contact_number")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.get("contact_pick"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(EmergencyContact(name.trim(), number.trim())) }) {
                Text(strings.get("contact_save"))
            }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = onClear) { Text(strings.get("contact_clear")) }
            }
        },
    )
}
