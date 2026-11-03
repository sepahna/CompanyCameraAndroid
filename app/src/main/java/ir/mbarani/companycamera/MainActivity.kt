package ir.mbarani.companycamera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Navy = Color(0xFF142B4A)
private val Blue = Color(0xFF2563EB)
private val SurfaceSoft = Color(0xFFF4F7FB)

private enum class AppTab(val label: String, val icon: ImageVector) {
    Dashboard("داشبورد", Icons.Default.Dashboard),
    Camera("دوربین", Icons.Default.PhotoCamera),
    History("تاریخچه", Icons.Default.History),
    Settings("تنظیمات", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private val vm: CompanyCameraViewModel by viewModels()
    private var capture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var previewView: PreviewView? = null

    private var pendingStartCamera: (() -> Unit)? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingStartCamera?.invoke()
        } else {
            toast("برای عکاسی مجوز دوربین لازم است.")
        }
        pendingStartCamera = null
    }

    private val exportJson = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) lifecycleScope.launch { writeJson(uri) } }

    private val exportZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) lifecycleScope.launch { writeZip(uri) } }

    private val importJson = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) lifecycleScope.launch { restoreJson(uri) } }

    private val importZip = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) lifecycleScope.launch { restoreZip(uri) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootDirs()
        setContent {
            CompanyCameraApp(
                vm = vm,
                onEnsureCameraPermission = { onGranted ->
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        onGranted()
                    } else {
                        pendingStartCamera = onGranted
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                onBindCamera = { view ->
                    previewView = view
                    bindCamera()
                },
                onStopCamera = { stopCamera() },
                onCapture = { takePhoto(it) },
                onFlip = {
                    lens = if (lens == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    bindCamera()
                },
                onBackupJson = { exportJson.launch("company-camera-backup.json") },
                onBackupZip = { exportZip.launch("company-camera-backup.zip") },
                onRestoreJson = {
                    importJson.launch(arrayOf("application/json", "text/*"))
                },
                onRestoreZip = {
                    importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                onHelp = { showHelp() },
                onInstallUpdate = { file -> UpdateChecker.installApk(this, file) },
                onOpenInstallPermission = { UpdateChecker.openUnknownSourcesSettings(this) }
            )
        }
    }

    override fun onStop() {
        stopCamera()
        super.onStop()
    }

    private fun rootDir() =
        File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "CompanyCamera")
            .apply { mkdirs() }

    private fun photosRoot() = File(rootDir(), "PHOTOS").apply { mkdirs() }
    private fun backupsRoot() = File(rootDir(), "BACKUPS").apply { mkdirs() }
    private fun categoryDir(c: String) = File(photosRoot(), c).apply { mkdirs() }
    private fun rootDirs() {
        categoryDir("MACHINERY")
        categoryDir("PERSONNEL")
        backupsRoot()
    }

    private fun bindCamera() {
        val view = previewView ?: return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
                capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    this,
                    CameraSelector.Builder().requireLensFacing(lens).build(),
                    preview,
                    capture
                )
            } catch (e: Exception) {
                toast("خطا در دوربین: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        capture = null
        previewView = null
    }

    private fun takePhoto(description: String) {
        val cap = capture ?: run {
            toast("ابتدا دوربین را روشن کنید.")
            return
        }
        val temp = File(cacheDir, "capture-${System.currentTimeMillis()}.jpg")
        cap.takePicture(
            ImageCapture.OutputFileOptions.Builder(temp).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    askName(temp, description)
                }

                override fun onError(e: ImageCaptureException) {
                    toast("ثبت عکس ناموفق بود: ${e.message}")
                }
            }
        )
    }

    private fun askName(temp: File, description: String) {
        val edit = android.widget.EditText(this).apply { hint = "نام فایل، مثلاً 2254" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("نام عکس")
            .setView(edit)
            .setPositiveButton("ذخیره") { _, _ ->
                val n = edit.text.toString().trim()
                    .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
                    .removeSuffix(".jpg")
                    .removeSuffix(".JPG")
                if (n.isBlank()) {
                    temp.delete()
                    toast("نام فایل الزامی است.")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val c = vm.category.value
                    val f = File(categoryDir(c), "$n.jpg")
                    if (f.exists()) {
                        toast("این نام قبلاً ثبت شده است.")
                        temp.delete()
                        return@launch
                    }
                    withContext(Dispatchers.IO) { temp.copyTo(f) }
                    vm.add(
                        Photo(
                            fileName = f.name,
                            project = c,
                            description = description,
                            relativePath = f.absolutePath,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    temp.delete()
                    toast("عکس ذخیره شد.")
                }
            }
            .setNegativeButton("لغو") { _, _ -> temp.delete() }
            .show()
    }

    private fun showHelp() {
        val msg = buildString {
            append("عکس‌ها در مسیر زیر ذخیره می‌شوند:\n")
            append(photosRoot().absolutePath)
            append("\n\nماشین‌آلات: ")
            append(categoryDir("MACHINERY").absolutePath)
            append("\nنفرات: ")
            append(categoryDir("PERSONNEL").absolutePath)
            append("\n\nپشتیبان داخلی: ")
            append(backupsRoot().absolutePath)
            append("\n\nبرای انتقال به گوشی دیگر، خروجی ZIP بگیرید.")
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("راهنمای Company Camera")
            .setMessage(msg)
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private suspend fun backupObject(): JSONObject = withContext(Dispatchers.IO) {
        val a = JSONArray()
        vm.photos.value.forEach { p ->
            val f = File(p.relativePath)
            val b = if (f.exists()) {
                android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
            } else {
                ""
            }
            a.put(
                JSONObject()
                    .put("fileName", p.fileName)
                    .put("project", p.project)
                    .put("description", p.description)
                    .put("createdAt", p.createdAt)
                    .put("imageBase64", b)
            )
        }
        JSONObject()
            .put("format", "company-camera-backup")
            .put("version", 2)
            .put("exportedAt", System.currentTimeMillis())
            .put("photos", a)
    }

    private suspend fun writeJson(uri: Uri) {
        try {
            val bytes = backupObject().toString(2).toByteArray()
            withContext(Dispatchers.IO) {
                File(backupsRoot(), "backup-${System.currentTimeMillis()}.json").writeBytes(bytes)
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            toast("پشتیبان JSON ذخیره شد.")
        } catch (e: Exception) {
            toast("خطا: ${e.localizedMessage}")
        }
    }

    private suspend fun writeZip(uri: Uri) {
        try {
            val obj = backupObject()
            withContext(Dispatchers.IO) {
                val f = File(backupsRoot(), "backup-${System.currentTimeMillis()}.zip")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(f))).use { z ->
                    z.putNextEntry(ZipEntry("backup.json"))
                    z.write(obj.toString(2).toByteArray())
                    z.closeEntry()
                    vm.photos.value.forEach { p ->
                        val image = File(p.relativePath)
                        if (image.exists()) {
                            z.putNextEntry(ZipEntry("photos/${p.project}/${p.fileName}"))
                            image.inputStream().use { it.copyTo(z) }
                            z.closeEntry()
                        }
                    }
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
            }
            toast("پشتیبان ZIP ذخیره شد.")
        } catch (e: Exception) {
            toast("خطا: ${e.localizedMessage}")
        }
    }

    private suspend fun restoreJson(uri: Uri) {
        try {
            val s = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            }
            restoreObject(JSONObject(s))
            toast("بازیابی انجام شد.")
        } catch (e: Exception) {
            toast("فایل JSON معتبر نیست: ${e.localizedMessage}")
        }
    }

    private suspend fun restoreZip(uri: Uri) {
        try {
            val entries = withContext(Dispatchers.IO) {
                val m = HashMap<String, ByteArray>()
                ZipInputStream(contentResolver.openInputStream(uri)!!).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory) m[e.name] = z.readBytes()
                        z.closeEntry()
                    }
                }
                m
            }
            val o = JSONObject(
                entries["backup.json"]?.toString(Charsets.UTF_8) ?: error("backup.json پیدا نشد")
            )
            val a = o.getJSONArray("photos")
            for (i in 0 until a.length()) {
                val item = a.getJSONObject(i)
                val project = item.optString("project", "MACHINERY")
                val name = item.getString("fileName")
                val bytes = entries["photos/$project/$name"]
                    ?: item.optString("imageBase64").takeIf { it.isNotBlank() }
                        ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                if (bytes != null) saveRestored(item, bytes)
            }
            toast("بازیابی ZIP انجام شد.")
        } catch (e: Exception) {
            toast("بازیابی ناموفق: ${e.localizedMessage}")
        }
    }

    private suspend fun restoreObject(o: JSONObject) {
        require(o.optString("format") == "company-camera-backup")
        val a = o.getJSONArray("photos")
        for (i in 0 until a.length()) {
            val p = a.getJSONObject(i)
            val b = p.optString("imageBase64")
            if (b.isNotBlank()) {
                saveRestored(p, android.util.Base64.decode(b, android.util.Base64.DEFAULT))
            }
        }
    }

    private suspend fun saveRestored(p: JSONObject, b: ByteArray) = withContext(Dispatchers.IO) {
        val c = if (p.optString("project") == "PERSONNEL") "PERSONNEL" else "MACHINERY"
        val n = p.optString("fileName", "restored-${System.currentTimeMillis()}.jpg")
            .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
        var f = File(categoryDir(c), n)
        if (f.exists()) f = File(categoryDir(c), "${System.currentTimeMillis()}-$n")
        f.writeBytes(b)
        withContext(Dispatchers.Main) {
            vm.add(
                Photo(
                    fileName = f.name,
                    project = c,
                    description = p.optString("description"),
                    relativePath = f.absolutePath,
                    createdAt = p.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyCameraApp(
    vm: CompanyCameraViewModel,
    onEnsureCameraPermission: (() -> Unit) -> Unit,
    onBindCamera: (PreviewView) -> Unit,
    onStopCamera: () -> Unit,
    onCapture: (String) -> Unit,
    onFlip: () -> Unit,
    onBackupJson: () -> Unit,
    onBackupZip: () -> Unit,
    onRestoreJson: () -> Unit,
    onRestoreZip: () -> Unit,
    onHelp: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onOpenInstallPermission: () -> Unit
) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(AppTab.Dashboard) }
    // History tabs: 0 = نفرات, 1 = ماشین‌آلات
    var historyCategoryTab by remember { mutableIntStateOf(0) }
    var cameraActive by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }

    fun openHistory(category: String? = null) {
        if (category == "MACHINERY") historyCategoryTab = 1
        else if (category == "PERSONNEL") historyCategoryTab = 0
        selectedTab = AppTab.History
    }

    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    fun runUpdateCheck(notifyIfLatest: Boolean) {
        scope.launch {
            checkingUpdate = true
            updateError = null
            try {
                val latest = UpdateChecker.fetchLatest()
                val current = UpdateChecker.currentVersionCode(context)
                if (latest != null && latest.versionCode > current) {
                    updateInfo = latest
                    showUpdateDialog = true
                } else if (notifyIfLatest) {
                    Toast.makeText(context, "نسخه فعلی به‌روز است.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (notifyIfLatest) {
                    Toast.makeText(
                        context,
                        "بررسی به‌روزرسانی ناموفق بود: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                checkingUpdate = false
            }
        }
    }

    fun startDownloadAndInstall() {
        val info = updateInfo ?: return
        scope.launch {
            downloading = true
            updateError = null
            downloadProgress = 0
            try {
                if (!UpdateChecker.canRequestInstallPermission(context)) {
                    onOpenInstallPermission()
                    updateError = "اجازه نصب از منابع ناشناس را فعال کنید و دوباره دانلود را بزنید."
                    return@launch
                }
                val file = UpdateChecker.downloadApk(context, info.apkUrl) { p ->
                    downloadProgress = p
                }
                showUpdateDialog = false
                onInstallUpdate(file)
            } catch (e: Exception) {
                updateError = e.localizedMessage ?: "خطا در دانلود"
            } finally {
                downloading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runUpdateCheck(notifyIfLatest = false)
    }

    DisposableEffect(selectedTab, cameraActive) {
        if (selectedTab != AppTab.Camera || !cameraActive) {
            onStopCamera()
            if (selectedTab != AppTab.Camera) {
                cameraActive = false
            }
        }
        onDispose { }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            secondary = Navy,
            background = SurfaceSoft,
            surface = Color.White
        )
    ) {
        Scaffold(
            containerColor = SurfaceSoft,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "COMPANY CAMERA",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = when (selectedTab) {
                                    AppTab.Dashboard -> "داشبورد"
                                    AppTab.Camera -> "ثبت تصویر"
                                    AppTab.History -> "آرشیو تصاویر"
                                    AppTab.Settings -> "تنظیمات و پشتیبان"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    AppTab.Dashboard -> DashboardScreen(
                        photos = photos,
                        onOpenCamera = { selectedTab = AppTab.Camera },
                        onOpenHistory = { openHistory() },
                        onOpenPersonnel = { openHistory("PERSONNEL") },
                        onOpenMachinery = { openHistory("MACHINERY") }
                    )

                    AppTab.Camera -> CameraScreen(
                        category = category,
                        description = description,
                        cameraActive = cameraActive,
                        onCategoryChange = vm::setCategory,
                        onDescriptionChange = { description = it },
                        onStartCamera = {
                            onEnsureCameraPermission {
                                cameraActive = true
                            }
                        },
                        onStopCamera = {
                            cameraActive = false
                            onStopCamera()
                        },
                        onBindCamera = onBindCamera,
                        onFlip = onFlip,
                        onCapture = {
                            onCapture(description)
                            description = ""
                        }
                    )

                    AppTab.History -> HistoryScreen(
                        photos = photos,
                        query = query,
                        categoryTab = historyCategoryTab,
                        onCategoryTabChange = { historyCategoryTab = it },
                        onQueryChange = vm::setQuery,
                        onOpenPhoto = { selectedPhoto = it }
                    )

                    AppTab.Settings -> SettingsScreen(
                        currentVersion = UpdateChecker.currentVersionName(context),
                        currentCode = UpdateChecker.currentVersionCode(context),
                        checkingUpdate = checkingUpdate,
                        onBackupJson = onBackupJson,
                        onBackupZip = onBackupZip,
                        onRestoreJson = onRestoreJson,
                        onRestoreZip = onRestoreZip,
                        onHelp = onHelp,
                        onCheckUpdate = { runUpdateCheck(notifyIfLatest = true) }
                    )
                }
            }
        }

        val selectedPhotoId = selectedPhoto?.id ?: -1L
        val comments by vm.comments(selectedPhotoId)
            .collectAsStateWithLifecycle(emptyList())

        selectedPhoto?.let { selected ->
            val livePhoto = photos.firstOrNull { it.id == selected.id } ?: selected
            PhotoDetailSheet(
                photo = livePhoto,
                comments = comments,
                onDismiss = { selectedPhoto = null },
                onAddComment = { text -> vm.addComment(livePhoto.id, text) },
                onDeleteComment = { id -> vm.deleteComment(id) },
                onSaveEdit = { updated ->
                    vm.update(updated)
                    selectedPhoto = updated
                    Toast.makeText(context, "تغییرات ذخیره شد.", Toast.LENGTH_SHORT).show()
                },
                onDelete = {
                    vm.delete(livePhoto)
                    selectedPhoto = null
                    Toast.makeText(context, "عکس حذف شد.", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    try {
                        MediaActions.shareImage(context, livePhoto)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            e.localizedMessage ?: "اشتراک‌گذاری ناموفق",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onSaveGallery = {
                    val ok = MediaActions.saveToGallery(context, livePhoto)
                    Toast.makeText(
                        context,
                        if (ok) "در گالری ذخیره شد." else "ذخیره در گالری ناموفق بود.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        if (showUpdateDialog && updateInfo != null) {
            val info = updateInfo!!
            AlertDialog(
                onDismissRequest = { if (!downloading) showUpdateDialog = false },
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Blue) },
                title = { Text("نسخه جدید موجود است") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("نسخه ${info.versionName} (کد ${info.versionCode}) آماده دانلود است.")
                        Text(
                            "نسخه فعلی: ${UpdateChecker.currentVersionName(context)}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "$downloadProgress٪",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        updateError?.let { Text(it, color = Color.Red) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !downloading,
                        onClick = { startDownloadAndInstall() }
                    ) { Text(if (downloading) "در حال دانلود..." else "دانلود و نصب") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !downloading,
                        onClick = { showUpdateDialog = false }
                    ) { Text("بعداً") }
                }
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    photos: List<Photo>,
    onOpenCamera: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPersonnel: () -> Unit,
    onOpenMachinery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = "کل تصاویر",
                value = photos.size.toString(),
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier.weight(1f),
                onClick = onOpenHistory
            )
            StatCard(
                label = "ماشین‌آلات",
                value = photos.count { it.project == "MACHINERY" }.toString(),
                icon = Icons.Default.Build,
                modifier = Modifier.weight(1f),
                onClick = onOpenMachinery
            )
            StatCard(
                label = "نفرات",
                value = photos.count { it.project == "PERSONNEL" }.toString(),
                icon = Icons.Default.Groups,
                modifier = Modifier.weight(1f),
                onClick = onOpenPersonnel
            )
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("شروع سریع", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "دوربین فقط وقتی که خودتان بخواهید روشن می‌شود.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("رفتن به دوربین")
                }
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("مشاهده تاریخچه")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("آخرین تصاویر", fontWeight = FontWeight.Bold)
                if (photos.isEmpty()) {
                    Text("هنوز تصویری ثبت نشده است.", color = Color.Gray)
                } else {
                    photos.take(5).forEach { photo ->
                        Text(
                            "• ${photo.fileName} — ${categoryLabel(photo.project)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraScreen(
    category: String,
    description: String,
    cameraActive: Boolean,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStartCamera: () -> Unit,
    onStopCamera: () -> Unit,
    onBindCamera: (PreviewView) -> Unit,
    onFlip: () -> Unit,
    onCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf("MACHINERY" to "ماشین‌آلات", "PERSONNEL" to "نفرات")
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = category == option.first,
                    onClick = { onCategoryChange(option.first) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(option.second) }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!cameraActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEAF1FF),
                        modifier = Modifier
                            .size(96.dp)
                            .clickable(onClick = onStartCamera)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "شروع دوربین",
                                tint = Blue,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("دوربین خاموش است", fontWeight = FontWeight.SemiBold)
                    Text(
                        "برای شروع، روی آیکون دوربین بزنید.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onStartCamera) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("روشن کردن دوربین")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            PreviewView(context).also { view ->
                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                onBindCamera(view)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(Color(0xFFE8EEF6), RoundedCornerShape(16.dp))
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("توضیحات عکس (اختیاری)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onFlip, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("تعویض")
                        }
                        Button(onClick = onCapture, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("ثبت عکس")
                        }
                    }
                    TextButton(onClick = onStopCamera, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("خاموش کردن دوربین")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    photos: List<Photo>,
    query: String,
    categoryTab: Int,
    onCategoryTabChange: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenPhoto: (Photo) -> Unit
) {
    val searching = query.isNotBlank()
    val matched = photos.filter {
        !searching || it.fileName.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    // هنگام جست‌وجو همه دسته‌ها؛ در حالت عادی فقط تب انتخاب‌شده
    val filtered = if (searching) {
        matched
    } else {
        val selectedCategory = if (categoryTab == 0) "PERSONNEL" else "MACHINERY"
        matched.filter { it.project == selectedCategory }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = categoryTab,
            containerColor = Color.White
        ) {
            Tab(
                selected = categoryTab == 0,
                onClick = { onCategoryTabChange(0) },
                text = {
                    Text("نفرات (${photos.count { it.project == "PERSONNEL" }})")
                }
            )
            Tab(
                selected = categoryTab == 1,
                onClick = { onCategoryTabChange(1) },
                text = {
                    Text("ماشین‌آلات (${photos.count { it.project == "MACHINERY" }})")
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جست‌وجو در همه دسته‌ها...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
            item {
                Text(
                    if (searching) {
                        "نتایج جست‌وجو در همه دسته‌ها: ${filtered.size} مورد"
                    } else {
                        "${filtered.size} مورد در ${if (categoryTab == 0) "نفرات" else "ماشین‌آلات"}"
                    },
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (filtered.isEmpty()) {
                item { EmptyArchiveCard() }
            } else {
                items(filtered, key = { it.id }) { photo ->
                    PhotoArchiveRow(photo = photo, onClick = { onOpenPhoto(photo) })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    currentVersion: String,
    currentCode: Int,
    checkingUpdate: Boolean,
    onBackupJson: () -> Unit,
    onBackupZip: () -> Unit,
    onRestoreJson: () -> Unit,
    onRestoreZip: () -> Unit,
    onHelp: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("به‌روزرسانی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("نسخه فعلی: $currentVersion (کد $currentCode)", color = Color.Gray)
                Button(
                    onClick = onCheckUpdate,
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (checkingUpdate) "در حال بررسی..." else "بررسی نسخه جدید")
                }
            }
        }

        BackupCard(
            onBackupJson = onBackupJson,
            onBackupZip = onBackupZip,
            onRestoreJson = onRestoreJson,
            onRestoreZip = onRestoreZip,
            onHelp = onHelp
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("درباره برنامه", fontWeight = FontWeight.Bold)
                Text(
                    "Company Camera · نسخه سازمانی\nطراحی Material 3 · ذخیره‌سازی محلی و آفلاین",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun EmptyArchiveCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(38.dp))
            Text("تصویری پیدا نشد", fontWeight = FontWeight.Medium)
            Text(
                "با ثبت عکس یا تغییر عبارت جست‌وجو دوباره امتحان کنید.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PhotoArchiveRow(photo: Photo, onClick: () -> Unit) {
    val date = remember(photo.createdAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR")).format(Date(photo.createdAt))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEAF1FF),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "مشاهده عکس",
                        tint = Blue,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(photo.fileName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${categoryLabel(photo.project)} · $date",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
                if (photo.description.isNotBlank()) {
                    Text(
                        photo.description,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupCard(
    onBackupJson: () -> Unit,
    onBackupZip: () -> Unit,
    onRestoreJson: () -> Unit,
    onRestoreZip: () -> Unit,
    onHelp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackupJson, modifier = Modifier.weight(1f)) { Text("خروجی JSON") }
                Button(onClick = onBackupZip, modifier = Modifier.weight(1f)) { Text("خروجی ZIP") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestoreJson, modifier = Modifier.weight(1f)) { Text("بازیابی JSON") }
                OutlinedButton(onClick = onRestoreZip, modifier = Modifier.weight(1f)) { Text("بازیابی ZIP") }
            }
            TextButton(onClick = onHelp) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("راهنما و مسیر ذخیره‌سازی")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDetailSheet(
    photo: Photo,
    comments: List<PhotoComment>,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onSaveEdit: (Photo) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSaveGallery: () -> Unit
) {
    var editing by remember(photo.id) { mutableStateOf(false) }
    var editName by remember(photo.id) { mutableStateOf(photo.fileName.removeSuffix(".jpg").removeSuffix(".JPG")) }
    var editDescription by remember(photo.id) { mutableStateOf(photo.description) }
    var editCategory by remember(photo.id) { mutableStateOf(photo.project) }
    var commentText by remember(photo.id) { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = remember(photo.createdAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR")).format(Date(photo.createdAt))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(photo.fileName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val imageFile = remember(photo.relativePath) { File(photo.relativePath) }
                if (imageFile.exists()) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = photo.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    )
                } else {
                    Text("فایل تصویر در مسیر ذخیره‌شده پیدا نشد.")
                }

                Text("${categoryLabel(photo.project)} · $date", color = Color.Gray, style = MaterialTheme.typography.labelMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری", tint = Blue)
                    }
                    IconButton(onClick = onSaveGallery) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "ذخیره در گالری", tint = Blue)
                    }
                    IconButton(onClick = { editing = !editing }) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = Blue)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFDC2626))
                    }
                }

                if (editing) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام فایل") },
                        singleLine = true
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val options = listOf("MACHINERY" to "ماشین‌آلات", "PERSONNEL" to "نفرات")
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = editCategory == option.first,
                                onClick = { editCategory = option.first },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size)
                            ) { Text(option.second) }
                        }
                    }
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("توضیحات") }
                    )
                    Button(
                        onClick = {
                            val clean = editName.trim()
                                .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
                                .removeSuffix(".jpg")
                                .removeSuffix(".JPG")
                            if (clean.isBlank()) return@Button
                            onSaveEdit(
                                photo.copy(
                                    fileName = "$clean.jpg",
                                    description = editDescription.trim(),
                                    project = editCategory
                                )
                            )
                            editing = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ذخیره ویرایش") }
                } else if (photo.description.isNotBlank()) {
                    Text(photo.description)
                }

                Text("کامنت‌ها", fontWeight = FontWeight.Bold)
                if (comments.isEmpty()) {
                    Text("هنوز کامنتی ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                } else {
                    comments.forEach { comment ->
                        val commentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR"))
                            .format(Date(comment.createdAt))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(comment.text)
                                    Text(commentDate, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { onDeleteComment(comment.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف کامنت",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("نوشتن کامنت...") },
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            val text = commentText.trim()
                            if (text.isNotEmpty()) {
                                onAddComment(text)
                                commentText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "ارسال کامنت", tint = Blue)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف عکس") },
            text = { Text("این عکس و کامنت‌هایش برای همیشه حذف می‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("لغو") }
            }
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Blue)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

private fun categoryLabel(project: String): String =
    if (project == "PERSONNEL") "نفرات" else "ماشین‌آلات"
