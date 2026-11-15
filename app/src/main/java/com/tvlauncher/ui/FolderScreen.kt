package com.tvlauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvlauncher.LauncherViewModel
import com.tvlauncher.data.AppRepository
import com.tvlauncher.data.FolderItem

@Composable
fun FolderScreen(vm: LauncherViewModel, folderId: String, onBack: () -> Unit, guard: (String, () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val folder = s.folders.firstOrNull { it.id == folderId }
    var pickApp by remember { mutableStateOf(false) }
    var removeItem by remember { mutableStateOf<FolderItem?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                }
            }.getOrNull() ?: "ملف"
            vm.addToFolder(folderId, FolderItem(false, uri.toString(), name))
        }
    }

    if (folder == null) { LaunchedEffect(Unit) { onBack() }; return }

    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("رجوع", onClick = onBack)
            Text(folder.name, color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            PillButton("إضافة تطبيق") { pickApp = true }
            PillButton("إضافة ملف") { pickFile.launch(arrayOf("*/*")) }
        }
        if (folder.items.isEmpty()) {
            Text("المجلد فارغ. أضف تطبيقات أو ملفات. اضغط مطولاً على عنصر لحذفه من المجلد.", color = Palette.Muted, fontSize = 20.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(folder.items, key = { it.ref }) { item ->
                Focusable(
                    onClick = {
                        val ok = if (item.isApp) AppRepository.launch(ctx, item.ref) else AppRepository.openFile(ctx, item.ref)
                        if (!ok) info = "تعذر فتح ${item.label}"
                    },
                    onLongClick = { removeItem = item },
                    modifier = Modifier.height(112.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.label, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (item.isApp) "تطبيق" else "ملف", color = Palette.Muted, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    if (pickApp) ChoiceDialog("اختر تطبيقاً",
        apps.map { it.pkg to (it.label + if (it.supported) "" else " (غير مدعوم)") },
        onDismiss = { pickApp = false },
        onPick = { pkg ->
            pickApp = false
            apps.firstOrNull { it.pkg == pkg }?.let { vm.addToFolder(folderId, FolderItem(true, it.pkg, it.label)) }
        })
    removeItem?.let { it0 ->
        ChoiceDialog(it0.label, listOf(true to "حذف من المجلد"), onDismiss = { removeItem = null }, onPick = {
            vm.mutateFolder(folderId) { f -> f.copy(items = f.items.filter { x -> x.ref != it0.ref }) }
            removeItem = null
        })
    }
    info?.let { MessageDialog("تنبيه", it) { info = null } }
}
