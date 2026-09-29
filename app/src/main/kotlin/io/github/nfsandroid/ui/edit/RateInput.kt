package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.data.Rates

/** A cap on a transfer: an amount (0: none) and its unit, picked from a menu. */
@Composable
fun RateInput(title: String, amount: Int, unit: String, onChange: (Int, String) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        var text by remember { mutableStateOf(amount.toString()) }
        OutlinedTextField(
            text, { typed -> text = typed.filter(Char::isDigit).take(7); onChange(text.toIntOrNull() ?: 0, unit) },
            Modifier.width(112.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium,
        )
        Box {
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = true }) {
                Text(Rates.UNITS.firstOrNull { it.first == unit }?.second ?: unit)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }) {
                Rates.UNITS.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onChange(amount, key) })
                }
            }
        }
    }
