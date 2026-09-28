package io.github.nfsandroid.ui.edit

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R

/** A question before something that cannot be undone. */
@Composable
fun Confirm(title: Int, text: Int, yes: Int, onYes: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = { Text(stringResource(text)) },
    confirmButton = { TextButton(onClick = { onDismiss(); onYes() }) { Text(stringResource(yes)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
)
