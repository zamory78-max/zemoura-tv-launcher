package com.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(Palette.Surface).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            content()
        }
    }
}

/** لوحة أرقام تعمل بالأسهم (D-pad) لإدخال رمز من 4 أرقام. */
@Composable
fun PinPad(error: String?, onComplete: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { i ->
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(if (i < pin.length) Palette.Amber else Palette.Track)
            )
        }
    }
    Text(error ?: " ", color = Color(0xFFFFB4A8), fontSize = 18.sp)
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "←", "0", "✕")
    keys.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { k ->
                Focusable(
                    onClick = {
                        when (k) {
                            "←" -> pin = pin.dropLast(1)
                            "✕" -> pin = ""
                            else -> if (pin.length < 4) {
                                pin += k
                                if (pin.length == 4) { val p = pin; pin = ""; onComplete(p) }
                            }
                        }
                    },
                    modifier = Modifier.size(width = 96.dp, height = 70.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(k, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


/** طلب الرمز السري للتحقق. */
@Composable
fun PinPrompt(title: String, error: String?, onPin: (String) -> Unit, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) { PinPad(error, onPin) }
}

/** تعيين رمز جديد على خطوتين (إدخال ثم تأكيد). */
@Composable
fun PinSetup(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    DialogFrame(if (first == null) "أدخل رمزاً جديداً (4 أرقام)" else "أعد إدخال الرمز للتأكيد", onDismiss) {
        PinPad(err) { p ->
            if (first == null) { first = p; err = null }
            else if (first == p) onDone(p)
            else { first = null; err = "الرمزان غير متطابقين، ابدأ من جديد" }
        }
    }
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) {
        LazyColumn(
            Modifier.heightIn(max = 480.dp).widthIn(min = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(options) { (value, label) ->
                Focusable(onClick = { onPick(value) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label, color = Palette.Text, fontSize = 22.sp, modifier = Modifier.padding(18.dp))
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String, initial: String = "", numeric: Boolean = false, hint: String = "",
    onOk: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    DialogFrame(title, onDismiss) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            placeholder = { Text(hint) },
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.widthIn(min = 420.dp).focusRequester(fr)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton("حفظ", primary = true) { onOk(text.trim()) }
            PillButton("إلغاء") { onDismiss() }
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) {
        Text(message, color = Palette.Muted, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
        PillButton("حسناً", primary = true) { onDismiss() }
    }
}

/** قائمة بعناصر يمكن تفعيلها/إلغاؤها (قفل التطبيقات والمجلدات، تطبيقات المشاهدة). تبقى مفتوحة أثناء التبديل. */
@Composable
fun MultiToggleDialog(
    title: String, entries: List<Triple<String, String, Boolean>>,
    onToggle: (String) -> Unit, onDismiss: () -> Unit
) {
    DialogFrame(title, onDismiss) {
        if (entries.isEmpty()) Text("لا توجد عناصر.", color = Palette.Muted, fontSize = 20.sp)
        LazyColumn(Modifier.heightIn(max = 460.dp).widthIn(min = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.first }) { (id, label, on) ->
                Focusable(onClick = { onToggle(id) }, modifier = Modifier.fillMaxWidth(), bg = if (on) Palette.Teal else Palette.Card) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Palette.Text, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        Text(if (on) "✓" else "", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        PillButton("تم", primary = true) { onDismiss() }
    }
}
