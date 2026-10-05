package com.noorconnect.feature.channelinfo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChannelInfoRoute(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: ChannelInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoState by viewModel.photoState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state == ChannelInfoState.SignInRequired) {
                viewModel.retry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    ChannelInfoScreen(
        state = state,
        photoState = photoState,
        isSaving = isSaving,
        saveSuccessEvents = viewModel.saveSuccessEvents,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSignIn = onSignIn,
        onSave = viewModel::save,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChannelInfoScreen(
    state: ChannelInfoState,
    photoState: ChannelPhotoState,
    isSaving: Boolean,
    saveSuccessEvents: SharedFlow<Unit>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var originalTitle by remember { mutableStateOf("") }
    var originalDescription by remember { mutableStateOf("") }
    var originalUsername by remember { mutableStateOf("") }
    var originalPhoto by remember { mutableStateOf<String?>(null) }
    val info = (state as? ChannelInfoState.Loaded)?.info
    val hasChanges = editing && (
        title != originalTitle || description != originalDescription || username != originalUsername || photoPath != originalPhoto
    )

    fun leaveEditor() {
        if (hasChanges) showDiscardDialog = true else editing = false
    }

    BackHandler(enabled = isSaving || (editing && hasChanges)) {
        if (!isSaving) showDiscardDialog = true
    }
    LaunchedEffect(saveSuccessEvents) {
        saveSuccessEvents.collect {
            photoPath?.let { File(it).delete() }
            photoPath = null
            editing = false
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                            ?: error("تعذر فتح الصورة المختارة")
                        val sampleSize = generateSequence(1) { it * 2 }
                            .takeWhile { bounds.outWidth / it > MAX_PHOTO_DIMENSION || bounds.outHeight / it > MAX_PHOTO_DIMENSION }
                            .lastOrNull() ?: 1
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(
                                input,
                                null,
                                BitmapFactory.Options().apply { inSampleSize = sampleSize },
                            )
                        } ?: error("تعذر قراءة الصورة المختارة")
                        val file = File.createTempFile("channel-photo-", ".jpg", context.cacheDir)
                        try {
                            val saved = runCatching {
                                file.outputStream().use { output ->
                                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)) {
                                        "تعذر تجهيز الصورة المختارة"
                                    }
                                }
                                check(file.length() > 0L) { "الصورة المختارة فارغة" }
                                file.absolutePath
                            }.onFailure { file.delete() }
                            saved.getOrThrow()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                result.onSuccess {
                    photoPath?.let { previous -> File(previous).delete() }
                    photoPath = it
                }
                    .onFailure { error -> snackbarHostState.showSnackbar(error.message ?: "تعذر تجهيز الصورة") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "تعديل القناة" else "معلومات القناة") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (editing) leaveEditor() else onBack() },
                        enabled = !isSaving,
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (info?.canManage == true && !editing) {
                        IconButton(onClick = {
                            title = info.title
                            description = info.description.orEmpty()
                            username = (info.editableUsername ?: info.username).orEmpty()
                            photoPath = null
                            originalTitle = title
                            originalDescription = description
                            originalUsername = username
                            originalPhoto = null
                            editing = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "تعديل القناة")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            ChannelInfoState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            ChannelInfoState.SignInRequired -> EmptyState(
                message = "سجّل الدخول لعرض معلومات القناة",
                action = "تسجيل الدخول",
                onAction = onSignIn,
                modifier = Modifier.padding(padding),
            )
            is ChannelInfoState.Error -> EmptyState(
                message = state.message,
                action = "إعادة المحاولة",
                onAction = onRetry,
                modifier = Modifier.padding(padding),
            )
            is ChannelInfoState.Loaded -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelPhoto(
                    photoState = photoState,
                    localPhotoPath = photoPath,
                    title = if (editing) title else state.info.title,
                    editable = editing,
                    enabled = !isSaving,
                    onChoosePhoto = { photoPicker.launch("image/*") },
                )
                Spacer(Modifier.size(18.dp))

                if (editing) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("اسم القناة") },
                        enabled = !isSaving,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(12.dp))
                } else {
                    Text(state.info.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.size(8.dp))
                if (editing) {
                    if (state.info.canEditUsername) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            enabled = !isSaving,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(12.dp))
                    } else if (!state.info.username.isNullOrBlank()) {
                        Text("@${state.info.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("الوصف") },
                        enabled = !isSaving,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { leaveEditor() }, enabled = !isSaving) { Text("إلغاء") }
                        Button(
                            onClick = {
                                onSave(
                                    title,
                                    description,
                                    username.takeIf { state.info.canEditUsername },
                                    photoPath,
                                )
                            },
                            enabled = !isSaving && title.isNotBlank() && title.length <= 128 && description.length <= 255,
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("حفظ")
                        }
                    }
                } else {
                    state.info.username?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.size(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(
                            onClick = {
                                val link = state.info.link
                                if (link == null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("لا يتوفر رابط مشاركة لهذه القناة")
                                    }
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, link)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "مشاركة القناة"))
                                    } catch (_: ActivityNotFoundException) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("تعذرت مشاركة رابط القناة")
                                        }
                                    }
                                }
                            },
                            enabled = state.info.link != null,
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("مشاركة القناة")
                        }
                    }
                    Spacer(Modifier.size(20.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column {
                            InfoRow(
                                label = "الرابط",
                                value = state.info.link ?: "لا يتوفر رابط عام أو رابط دعوة",
                                onClick = state.info.link?.let { link -> ({
                                    val copied = runCatching {
                                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                            ?: error("الحافظة غير متاحة")
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("channel-link", link))
                                    }.isSuccess
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(if (copied) "تم نسخ رابط القناة" else "تعذر نسخ الرابط")
                                    }
                                }) },
                                isLink = state.info.link != null,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            InfoRow(
                                label = "المشتركون",
                                value = state.info.subscriberCount?.let {
                                    java.text.NumberFormat.getIntegerInstance(Locale("ar")).format(it)
                                } ?: "العدد غير متاح",
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            InfoRow(label = "الوصف", value = state.info.description ?: "لا يوجد وصف")
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("إلغاء التغييرات؟") },
            text = { Text("ستفقد التعديلات التي لم تحفظها.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    photoPath?.let { File(it).delete() }
                    editing = false
                    photoPath = null
                }) { Text("إلغاء التعديل") }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("متابعة التعديل") } },
        )
    }
}

@Composable
private fun ChannelPhoto(
    photoState: ChannelPhotoState,
    localPhotoPath: String?,
    title: String,
    editable: Boolean,
    enabled: Boolean,
    onChoosePhoto: () -> Unit,
) {
    val path = localPhotoPath ?: (photoState as? ChannelPhotoState.Ready)?.path
    val image = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            when {
                image != null -> Image(image, contentDescription = "صورة القناة", modifier = Modifier.fillMaxSize())
                photoState == ChannelPhotoState.Loading -> CircularProgressIndicator(Modifier.size(32.dp))
                else -> Text(title.firstOrNull()?.uppercase() ?: "ق", style = MaterialTheme.typography.displaySmall)
            }
        }
        if (editable) {
            IconButton(onClick = onChoosePhoto, enabled = enabled) {
                Icon(Icons.Filled.Image, contentDescription = "تغيير صورة القناة")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null, isLink: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = if (isLink) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ الرابط")
    }
}

@Composable
private fun EmptyState(message: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.size(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private const val MAX_PHOTO_DIMENSION = 1280