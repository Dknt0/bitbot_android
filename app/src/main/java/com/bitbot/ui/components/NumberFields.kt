package com.bitbot.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import java.util.Locale

/**
 * Text fields that keep the raw text the user types instead of reformatting
 * the stored value on every keystroke. Intermediate input ("", "1", "1.") is
 * allowed; the parsed value is pushed upstream on every parseable change and
 * the display is reformatted only when the field loses focus or the external
 * value changes while unfocused. On focus the whole text is selected so the
 * first keystroke replaces the previous value.
 */

@Composable
fun DecimalField(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current
) {
    var text by remember { mutableStateOf(TextFieldValue(formatDecimal(value))) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(focused) {
        if (focused) {
            text = TextFieldValue(text.text, selection = TextRange(0, text.text.length))
        }
    }
    LaunchedEffect(value) {
        if (!focused) text = TextFieldValue(formatDecimal(value))
    }

    OutlinedTextField(
        value = text,
        onValueChange = { tv ->
            text = tv
            tv.text.toDoubleOrNull()?.let(onValueChange)
        },
        modifier = modifier,
        label = label,
        leadingIcon = leadingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        interactionSource = interactionSource,
        textStyle = textStyle
    )
}

@Composable
fun IntField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    validRange: IntRange? = null
) {
    var text by remember { mutableStateOf(TextFieldValue(value.toString())) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(focused) {
        if (focused) {
            text = TextFieldValue(text.text, selection = TextRange(0, text.text.length))
        }
    }
    LaunchedEffect(value) {
        if (!focused) text = TextFieldValue(value.toString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = { tv ->
            text = tv
            val parsed = tv.text.toIntOrNull()
            if (parsed != null && (validRange == null || parsed in validRange)) {
                onValueChange(parsed)
            }
        },
        modifier = modifier,
        label = label,
        leadingIcon = leadingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = interactionSource
    )
}

private fun formatDecimal(value: Double): String = "%.2f".format(Locale.US, value)
