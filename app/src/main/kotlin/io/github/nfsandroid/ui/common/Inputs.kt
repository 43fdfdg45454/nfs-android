package io.github.nfsandroid.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Text, with its help under it (or what is wrong with it). */
@Composable
fun TextInput(
    label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), help: String? = null, error: String? = null,
    placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit,
) = OutlinedTextField(
    value, onChange, modifier, label = { Text(label) }, singleLine = true, isError = error != null,
    placeholder = placeholder?.let { { Text(it) } },
    supportingText = (error ?: help)?.let { { Text(it) } },
    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    shape = MaterialTheme.shapes.medium,
)

/** One of a few values, side by side. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) =
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected = value == selected, onClick = { onSelect(value) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(label)
            }
        }
    }

/** On or off: the whole row switches it. */
@Composable
fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Help(help)
        }
        Switch(checked, onChange)
    }

/** An amount: typed exactly in its box, or picked with the slider among round steps. */
@Composable
fun AmountInput(title: String, value: Int, steps: List<Int>, unit: String, help: String, onChange: (Int) -> Unit) {
    val range = steps.first()..steps.last()
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value.toString()) }
    val typed = text.toIntOrNull()?.takeIf { it in range }
    // Changed from outside (the slider): show the new value.
    if (typed != null && typed != value) text = value.toString()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                text, { t -> text = t.filter(Char::isDigit).take(5); text.toIntOrNull()?.takeIf { it in range }?.let(onChange) },
                Modifier.width(128.dp), singleLine = true, isError = typed == null,
                suffix = unit.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium,
            )
        }
        val index = steps.indices.minBy { abs(steps[it] - value) }
        Slider(index.toFloat(), { i -> steps[i.roundToInt()].let { text = it.toString(); onChange(it) } },
            valueRange = 0f..steps.lastIndex.toFloat(), steps = steps.size - 2)
        Help(help)
    }
}

/** A whole number typed in; empty while being typed is 0 to the caller. */
@Composable
fun NumberInput(label: String, value: Int, modifier: Modifier = Modifier.fillMaxWidth(), help: String? = null, error: String? = null, onChange: (Int) -> Unit) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value.toString()) }
    // Changed from outside (another transport's port): show the new value.
    if ((text.toIntOrNull() ?: 0) != value) text = value.toString()
    TextInput(label, text, modifier, help, error, keyboard = KeyboardType.Number) { v ->
        text = v.filter(Char::isDigit).take(9)
        onChange(text.toIntOrNull() ?: 0)
    }
}
