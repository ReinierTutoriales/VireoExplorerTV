package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*

@Composable
internal fun TvTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    inputFocusRequester: FocusRequester? = null,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    downFocusRequester: FocusRequester? = null
) {
    val colors = MaterialTheme.colorScheme
    var isFocused by remember { mutableStateOf(false) }
    val fieldModifier = Modifier
        .fillMaxWidth()
        .then(if (inputFocusRequester != null) Modifier.focusRequester(inputFocusRequester) else Modifier)
        .then(
            if (downFocusRequester != null) Modifier.onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                    key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0 &&
                    downFocusRequester.requestFocus()
            } else Modifier
        )
        .onFocusChanged { isFocused = it.isFocused }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) colors.primary else colors.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, VireoInputShape)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) colors.primary else colors.border,
                    shape = VireoInputShape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (state.text.isEmpty()) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
            }
            val actionHandler = if (onDone != null) KeyboardActionHandler { onDone() } else null
            if (password) {
                BasicSecureTextField(
                    state = state,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = imeAction
                    ),
                    onKeyboardAction = actionHandler,
                    modifier = fieldModifier
                )
            } else {
                BasicTextField(
                    state = state,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = imeAction
                    ),
                    onKeyboardAction = actionHandler,
                    modifier = fieldModifier
                )
            }
        }
    }
}

@Composable
internal fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VireoCardShape,
            colors = SurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.onSurface
            ),
            border = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)
        ) {
            Column(
                modifier = Modifier.widthIn(min = 380.dp, max = 520.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        modifier = Modifier.focusRequester(cancelFocus),
                        onClick = onDismiss
                    ) { Text(cancelLabel) }
                    Button(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) }
                }
            }
        }
    }
}

@Composable
internal fun TvInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    cancelLabel: String = "Cancel",
    validate: ((String) -> String?)? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textState = rememberTextFieldState(initial)
    var error by remember { mutableStateOf<String?>(null) }
    val inputFocus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect { error = null }
    }
    fun submit() {
        val candidate = textState.text.toString()
        val problem = validate?.invoke(candidate)
        if (problem != null) error = problem
        else { onDismiss(); onConfirm(candidate) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VireoCardShape,
            colors = SurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.onSurface
            ),
            border = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)
        ) {
            Column(
                modifier = Modifier.widthIn(min = 380.dp, max = 520.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                TvTextField(
                    state = textState,
                    label = label,
                    inputFocusRequester = inputFocus,
                    imeAction = ImeAction.Done,
                    onDone = ::submit
                )
                error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) { Text(cancelLabel) }
                    Button(onClick = ::submit) { Text(confirmLabel) }
                }
            }
        }
    }
}
