package uk.elizabeth.aac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uk.elizabeth.aac.core.data.Backup
import kotlin.math.roundToInt

/** Header with a Back button, used on the carer screens. */
@Composable
fun CarerHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onBack) { Text("◀ Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        content()
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    help: String? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${format(value)}", style = MaterialTheme.typography.titleMedium)
        if (help != null) Text(help, style = MaterialTheme.typography.bodyMedium)
        val steps = ((range.endInclusive - range.start) / step).roundToInt() - 1
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onChange((it / step).roundToInt() * step) },
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, help: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (help != null) Text(help, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN (at least 4 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(enabled = pin.length >= 4, onClick = { onSubmit(pin) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the export passphrase. When [confirm] is true it must be typed twice. */
@Composable
fun PassphraseDialog(title: String, confirm: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit, message: String? = null) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val longEnough = first.length >= Backup.MIN_PASSPHRASE_LENGTH
    val matches = !confirm || first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message != null) Text(message)
                if (confirm) {
                    Text("The export is encrypted with this passphrase. Without it, nobody (including you) can open the file. Write it down and keep it somewhere safe.")
                }
                OutlinedTextField(
                    value = first, onValueChange = { first = it }, singleLine = true,
                    label = { Text("Passphrase (at least ${Backup.MIN_PASSPHRASE_LENGTH} characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = second, onValueChange = { second = it }, singleLine = true,
                        label = { Text("Type it again") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (second.isNotEmpty() && !matches) Text("The passphrases do not match", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(enabled = longEnough && matches, onClick = { onSubmit(first.toCharArray()) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
