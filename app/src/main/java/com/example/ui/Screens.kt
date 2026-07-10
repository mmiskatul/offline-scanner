package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.data.FolderEntity
import com.example.data.ScannedPage
import com.example.services.FilterService
import com.example.services.ImageService
import com.example.services.OcrService
import com.example.services.PdfService
import com.example.services.ScannerService
import com.example.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class Screen {
    SPLASH,
    ONBOARDING,
    HOME,
    SCANNER,
    CROP_EDITOR,
    FILTER_EDITOR,
    PAGE_MANAGER,
    PDF_PREVIEW,
    SAVED_DOCUMENTS,
    DOCUMENT_DETAILS,
    OCR_TEXT,
    SETTINGS
}

// --- Local File Sharing Helper ---
fun shareFileOffline(context: Context, filePath: String, mimeType: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist locally", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.offlinesmartscanner.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

// --- 1. SPLASH SCREEN ---
@Composable
fun SplashScreen(onNavigateToOnboarding: () -> Unit, onNavigateToHome: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(1600)
        val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        val seenOnboarding = prefs.getBoolean("seen_onboarding", false)
        if (seenOnboarding) {
            onNavigateToHome()
        } else {
            onNavigateToOnboarding()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(110.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Offline Smart Scanner",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Secure • On-Device • Swift",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            )
        }
    }
}

// --- 2. ONBOARDING SCREEN ---
@Composable
fun OnboardingScreen(onGetStartedClick: () -> Unit) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(0) }
    
    val onboardingPages = listOf(
        Triple(
            Icons.Default.CameraAlt,
            "Scan & Auto-Crop",
            "Point your camera or pick photos from your gallery. Our offline engine helps frame your document page borders."
        ),
        Triple(
            Icons.Default.AutoAwesome,
            "20 Professional Filters",
            "Instantly enhance receipts, invoices, IDs, notes, and photos with highly optimized, hardware-accelerated filters."
        ),
        Triple(
            Icons.Default.Lock,
            "100% On-Device Privacy",
            "Generate offline PDFs, execute local OCR text recognition, and search inside documents securely. No internet needed!"
        )
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Top skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("seen_onboarding", true).apply()
                        onGetStartedClick()
                    },
                    modifier = Modifier.testTag("onboarding_skip_btn")
                ) {
                    Text("Skip", fontWeight = FontWeight.Bold)
                }
            }

            // Central Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val page = onboardingPages[currentPage]
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(130.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = page.first,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = page.second,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = page.third,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Bottom Indicators and Buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Indicators Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onboardingPages.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentPage) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                AppButton(
                    text = if (currentPage == onboardingPages.size - 1) "Get Started" else "Next",
                    onClick = {
                        if (currentPage < onboardingPages.size - 1) {
                            currentPage++
                        } else {
                            val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("seen_onboarding", true).apply()
                            onGetStartedClick()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "onboarding_next_btn",
                    icon = {
                        Icon(
                            imageVector = if (currentPage == onboardingPages.size - 1) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

// --- 3. HOME SCREEN ---
@Composable
fun HomeScreen(
    viewModel: ScannerViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val context = LocalContext.current
    val recentDocs by viewModel.filteredDocuments.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilterFolderId by viewModel.selectedFolderFilter.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    // Launcher for importing from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val localPaths = uris.mapNotNull { uri ->
                ScannerService.copyUriToLocal(context, uri, "Temp")
            }
            if (localPaths.isNotEmpty()) {
                viewModel.setDraftPages(localPaths)
                onNavigateToScanner() // Takes them to Scanner/Crop workflow
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScanner,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("home_fab_scan")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Scan Document")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Header Home Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Smart Scanner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
                Row {
                    IconButton(onClick = onNavigateToSaved) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Saved Documents")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }

            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                placeholderText = "Search documents or OCR text..."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Large Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clickable { onNavigateToScanner() }
                        .testTag("scan_doc_card_btn")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Scan Document",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clickable { galleryLauncher.launch("image/*") }
                        .testTag("import_gallery_card_btn")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Import Gallery",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Folder Shortcuts Horizontal List
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = activeFilterFolderId == null,
                        onClick = { viewModel.setFolderFilter(null) },
                        label = { Text("All Docs") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                items(folders) { folder ->
                    FilterChip(
                        selected = activeFilterFolderId == folder.id,
                        onClick = { viewModel.setFolderFilter(folder.id) },
                        label = { Text(folder.name) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent Documents Title
            Text(
                text = "Recent Scanned Documents",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Documents List
            if (recentDocs.isEmpty()) {
                EmptyState(
                    title = "No Documents Scanned",
                    description = "Capture page images or import from your photo gallery to create high-quality PDFs offline.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recent_docs_list")
                ) {
                    items(recentDocs) { doc ->
                        DocumentCard(
                            document = doc,
                            onClick = { onNavigateToDetails(doc.id) },
                            onLongClick = {},
                            onShareClick = { shareFileOffline(context, doc.pdfPath ?: "", "application/pdf") },
                            onDeleteClick = { docToDelete = doc },
                            onRenameClick = { docToRename = doc }
                        )
                    }
                }
            }
        }
    }

    // Modal dialogues
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    placeholder = { Text("Folder name") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.trim().isNotEmpty()) {
                            viewModel.createFolder(newFolderName.trim())
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    },
                    enabled = newFolderName.trim().isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (docToRename != null) {
        RenameModal(
            currentName = docToRename?.name ?: "",
            onDismiss = { docToRename = null },
            onConfirm = { newName ->
                docToRename?.let { viewModel.renameDocument(it.id, newName) }
                docToRename = null
            }
        )
    }

    if (docToDelete != null) {
        ConfirmDeleteModal(
            title = "Delete Document",
            message = "Are you sure you want to permanently delete '${docToDelete?.name}' and all associated files?",
            onDismiss = { docToDelete = null },
            onConfirm = {
                docToDelete?.let { viewModel.deleteDocument(it) }
                docToDelete = null
            }
        )
    }
}

// --- 4. SCANNER SCREEN ---
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onGoToCrop: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permissions check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val draftPages by viewModel.draftPages.collectAsState()

    // Camera utilities
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var isFlashEnabled by remember { mutableStateOf(false) }

    // Launcher for selecting additional images
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val localPaths = uris.mapNotNull { uri ->
                ScannerService.copyUriToLocal(context, uri, "Temp")
            }
            if (localPaths.isNotEmpty()) {
                val currentPages = draftPages.map { it.originalPath }.toMutableList()
                currentPages.addAll(localPaths)
                viewModel.setDraftPages(currentPages)
                onGoToCrop()
            }
        }
    }

    if (!hasCameraPermission) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This offline scanning app relies fully on your camera to capture receipts, notes, and pages. Please grant camera permission to scan documents.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                AppButton(
                    text = "Grant Permission",
                    onClick = { launcher.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Live camera view
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = CameraPreview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        camera.cameraControl.enableTorch(isFlashEnabled)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)
                previewView
            },
            update = { view ->
                // Apply flash toggle
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    if (cameraProvider.isBound(imageCapture)) {
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA
                        )
                        camera.cameraControl.enableTorch(isFlashEnabled)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(
                    onClick = { isFlashEnabled = !isFlashEnabled },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = Color.White
                    )
                }
            }
        }

        // Overlay bottom control container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Count indicator
            if (draftPages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "${draftPages.size} Page(s) Scanned",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery import button
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Import",
                        tint = Color.White
                    )
                }

                // Core Snap Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            val tempDir = ScannerService.getSubDir(context, "Temp")
                            val file = File(tempDir, "scanned_${UUID.randomUUID()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        viewModel.addDraftPage(file.absolutePath)
                                        Toast.makeText(context, "Page snapped!", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                        Toast.makeText(context, "Capture failed: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        .testTag("shutter_button")
                )

                // Check button (proceeds to crop edit / manager)
                IconButton(
                    onClick = {
                        if (draftPages.isNotEmpty()) {
                            onGoToCrop()
                        } else {
                            Toast.makeText(context, "Please scan at least 1 page first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (draftPages.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .testTag("scanner_done_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// --- 5. CROP EDITOR SCREEN ---
@Composable
fun CropEditorScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onGoToFilter: () -> Unit
) {
    val draftPages by viewModel.draftPages.collectAsState()
    val currentIndex by viewModel.currentEditingPageIndex.collectAsState()

    if (draftPages.isEmpty() || currentIndex !in draftPages.indices) {
        onBack()
        return
    }

    val page = draftPages[currentIndex]

    // Local crop box coordinates in percentage space (0f..1f)
    var cropLeft by remember(currentIndex) { mutableStateOf(0.1f) }
    var cropTop by remember(currentIndex) { mutableStateOf(0.1f) }
    var cropRight by remember(currentIndex) { mutableStateOf(0.9f) }
    var cropBottom by remember(currentIndex) { mutableStateOf(0.9f) }

    Scaffold(
        topBar = {
            Header(
                title = "Adjust Page Crop (${currentIndex + 1}/${draftPages.size})",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Interactive Crop area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Async scanned page image loaded in full
                AsyncImage(
                    model = page.originalPath,
                    contentDescription = "Draft Image to Crop",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Drag handles overlaid Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentIndex) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Drag calculations relative to size
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()

                                val deltaX = dragAmount.x / width
                                val deltaY = dragAmount.y / height

                                // Update nearest corner handle to drag position
                                val touchX = change.position.x / width
                                val touchY = change.position.y / height

                                val distTopLeft = Offset(touchX - cropLeft, touchY - cropTop).getDistance()
                                val distTopRight = Offset(touchX - cropRight, touchY - cropTop).getDistance()
                                val distBottomLeft = Offset(touchX - cropLeft, touchY - cropBottom).getDistance()
                                val distBottomRight = Offset(touchX - cropRight, touchY - cropBottom).getDistance()

                                val minDist = minOf(distTopLeft, distTopRight, distBottomLeft, distBottomRight)

                                when (minDist) {
                                    distTopLeft -> {
                                        cropLeft = (cropLeft + deltaX).coerceIn(0f, cropRight - 0.1f)
                                        cropTop = (cropTop + deltaY).coerceIn(0f, cropBottom - 0.1f)
                                    }
                                    distTopRight -> {
                                        cropRight = (cropRight + deltaX).coerceIn(cropLeft + 0.1f, 1f)
                                        cropTop = (cropTop + deltaY).coerceIn(0f, cropBottom - 0.1f)
                                    }
                                    distBottomLeft -> {
                                        cropLeft = (cropLeft + deltaX).coerceIn(0f, cropRight - 0.1f)
                                        cropBottom = (cropBottom + deltaY).coerceIn(cropTop + 0.1f, 1f)
                                    }
                                    distBottomRight -> {
                                        cropRight = (cropRight + deltaX).coerceIn(cropLeft + 0.1f, 1f)
                                        cropBottom = (cropBottom + deltaY).coerceIn(cropTop + 0.1f, 1f)
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    val leftPx = cropLeft * w
                    val topPx = cropTop * h
                    val rightPx = cropRight * w
                    val bottomPx = cropBottom * h

                    // Draw darker shade over cropped-out zone
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, topPx)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, topPx),
                        size = Size(leftPx, bottomPx - topPx)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(rightPx, topPx),
                        size = Size(w - rightPx, bottomPx - topPx)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, bottomPx),
                        size = Size(w, h - bottomPx)
                    )

                    // Draw main cropping rectangle borders
                    drawRect(
                        color = Color.Cyan,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(rightPx - leftPx, bottomPx - topPx),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // Draw circular drag handles at the 4 corners
                    val handleRadius = 14.dp.toPx()
                    drawCircle(color = Color.Cyan, radius = handleRadius, center = Offset(leftPx, topPx))
                    drawCircle(color = Color.Cyan, radius = handleRadius, center = Offset(rightPx, topPx))
                    drawCircle(color = Color.Cyan, radius = handleRadius, center = Offset(leftPx, bottomPx))
                    drawCircle(color = Color.Cyan, radius = handleRadius, center = Offset(rightPx, bottomPx))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Edit controls (Rotate Left, Rotate Right, Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.rotateCurrentPage(-90f) }) {
                    Icon(imageVector = Icons.Default.RotateLeft, contentDescription = "Rotate Left")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate -90°")
                }

                TextButton(onClick = { viewModel.rotateCurrentPage(90f) }) {
                    Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate Right")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate +90°")
                }

                TextButton(onClick = {
                    cropLeft = 0f
                    cropTop = 0f
                    cropRight = 1f
                    cropBottom = 1f
                    viewModel.resetCurrentPageEdits()
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Crop")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            viewModel.setCurrentEditingPageIndex(currentIndex - 1)
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Previous")
                }

                Button(
                    onClick = {
                        // Save crops
                        val rect = RectF(cropLeft, cropTop, cropRight, cropBottom)
                        viewModel.cropCurrentPage(rect)
                        
                        if (currentIndex < draftPages.size - 1) {
                            viewModel.setCurrentEditingPageIndex(currentIndex + 1)
                        } else {
                            onGoToFilter() // Take to Filter Editor
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("crop_save_btn")
                ) {
                    Text(if (currentIndex < draftPages.size - 1) "Save & Next Page" else "Save & Continue")
                }
            }
        }
    }
}

// --- 6. FILTER EDITOR SCREEN ---
@Composable
fun FilterEditorScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onGoToManager: () -> Unit
) {
    val draftPages by viewModel.draftPages.collectAsState()
    val currentIndex by viewModel.currentEditingPageIndex.collectAsState()

    if (draftPages.isEmpty() || currentIndex !in draftPages.indices) {
        onBack()
        return
    }

    val page = draftPages[currentIndex]
    var showOriginal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Header(
                title = "Enhancement Filter (${currentIndex + 1}/${draftPages.size})",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Hold image to compare with original",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Large Preview Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(showOriginal) {
                        detectDragGestures(
                            onDragStart = { showOriginal = true },
                            onDragEnd = { showOriginal = false },
                            onDragCancel = { showOriginal = false },
                            onDrag = { _, _ -> }
                        )
                    }
                    .testTag("filter_image_preview")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Loaded Bitmap
                    AsyncImage(
                        model = if (showOriginal) page.originalPath else page.enhancedPath,
                        contentDescription = "Enhancement Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Display 'Original' badge if user is holding image
                    if (showOriginal) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                        ) {
                            Text(
                                "Original View",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active enhancement filter name banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Active Filter: ${page.filterType.displayName}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Horizontal Filters Carousel List
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(FilterService.FilterType.entries.toTypedArray()) { filter ->
                    FilterThumbnail(
                        filterType = filter,
                        isSelected = page.filterType == filter,
                        originalPath = page.originalPath,
                        onClick = { viewModel.applyFilterToCurrentPage(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            viewModel.setCurrentEditingPageIndex(currentIndex - 1)
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Previous")
                }

                Button(
                    onClick = {
                        if (currentIndex < draftPages.size - 1) {
                            viewModel.setCurrentEditingPageIndex(currentIndex + 1)
                        } else {
                            onGoToManager() // Done editing, go to layout review
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("filter_next_btn")
                ) {
                    Text(if (currentIndex < draftPages.size - 1) "Apply & Next" else "Apply & Finish")
                }
            }
        }
    }
}

// --- 7. PAGE MANAGER SCREEN ---
@Composable
fun PageManagerScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onGoToPreview: (String) -> Unit
) {
    val draftPages by viewModel.draftPages.collectAsState()
    val context = LocalContext.current

    var docTitle by remember { mutableStateOf("Doc_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}") }
    var showPdfSetupDialog by remember { mutableStateOf(false) }

    // Picker for adding more pages
    val addMoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val localPath = ScannerService.copyUriToLocal(context, uri, "Temp")
                if (localPath != null) {
                    viewModel.addDraftPage(localPath)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Header(
                title = "Review Document Pages (${draftPages.size})",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Drag grid thumbnails listing
            if (draftPages.isEmpty()) {
                EmptyState(
                    title = "No Pages",
                    description = "You've deleted all pages from this scan draft. Add pages to review layout.",
                    modifier = Modifier.weight(1f),
                    onActionClick = { addMoreLauncher.launch("image/*") },
                    actionText = "Import Photo"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("page_manager_grid")
                ) {
                    itemsIndexed(draftPages) { index, page ->
                        PageThumbnail(
                            index = index,
                            filePath = page.enhancedPath,
                            filterName = page.filterType.displayName,
                            isSelected = false,
                            onClick = {
                                viewModel.setCurrentEditingPageIndex(index)
                                // Returns to editor for adjustments
                                onBack()
                            },
                            onDeleteClick = { viewModel.deleteDraftPage(index) },
                            onDuplicateClick = { viewModel.duplicateDraftPage(index) },
                            onMoveLeftClick = if (index > 0) { { viewModel.reorderDraftPages(index, index - 1) } } else null,
                            onMoveRightClick = if (index < draftPages.size - 1) { { viewModel.reorderDraftPages(index, index + 1) } } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Page Manager utility buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { addMoreLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Page")
                }

                Button(
                    onClick = {
                        if (draftPages.isNotEmpty()) {
                            showPdfSetupDialog = true
                        } else {
                            Toast.makeText(context, "Please scan or import pages first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("manager_save_pdf_btn")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Generate PDF")
                }
            }
        }
    }

    if (showPdfSetupDialog) {
        PdfOptionsModal(
            initialName = docTitle,
            onDismiss = { showPdfSetupDialog = false },
            onGenerate = { name, addPageNumbers, watermarkText, compress ->
                showPdfSetupDialog = false
                viewModel.saveDraftAsDocument(
                    pdfName = name,
                    addPageNumbers = addPageNumbers,
                    watermarkText = watermarkText,
                    compressBeforePdf = compress,
                    onComplete = { docId ->
                        onGoToPreview(docId) // Open PDF render Preview Screen
                    }
                )
            }
        )
    }
}

// --- 8. PDF PREVIEW SCREEN ---
@Composable
fun PdfPreviewScreen(
    docId: String,
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onGoToDetails: (String) -> Unit
) {
    val context = LocalContext.current
    var pdfPagesBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loadedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    val scope = rememberCoroutineScope()

    // Fetch active document metadata
    LaunchedEffect(docId) {
        viewModel.allDocuments.collect { list ->
            val doc = list.firstOrNull { it.id == docId }
            if (doc != null) {
                loadedDoc = doc
                // Local off-screen PDF Renderer conversion to Bitmaps
                scope.launch(Dispatchers.IO) {
                    try {
                        doc.pdfPath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                val renderer = PdfRenderer(pfd)
                                val pageCount = renderer.pageCount
                                val bitmaps = mutableListOf<Bitmap>()
                                
                                for (i in 0 until pageCount) {
                                    val page = renderer.openPage(i)
                                    // Scale to high-res fitting width
                                    val scale = 800f / page.width.coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(
                                        (page.width * scale).toInt(),
                                        (page.height * scale).toInt(),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()
                                    bitmaps.add(bmp)
                                }
                                renderer.close()
                                pfd.close()
                                withContext(Dispatchers.Main) {
                                    pdfPagesBitmaps = bitmaps
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Header(
                title = loadedDoc?.name ?: "PDF Preview",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // PDF page scrolls
            if (pdfPagesBitmaps.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(pdfPagesBitmaps) { index, bmp ->
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "PDF Page ${index + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Page ${index + 1} of ${pdfPagesBitmaps.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(4.dp),
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        loadedDoc?.pdfPath?.let { path ->
                            shareFileOffline(context, path, "application/pdf")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = loadedDoc != null
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share PDF")
                }

                Button(
                    onClick = { onGoToDetails(docId) },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("preview_done_btn"),
                    enabled = loadedDoc != null
                ) {
                    Text("Done & Details")
                }
            }
        }
    }
}

// --- 9. SAVED DOCUMENTS SCREEN ---
@Composable
fun SavedDocumentsScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val context = LocalContext.current
    val docs by viewModel.filteredDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()

    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    Scaffold(
        topBar = {
            Header(
                title = "Saved Documents",
                onBackClick = onBack,
                actions = {
                    var expandedSort by remember { mutableStateOf(false) }
                    IconButton(onClick = { expandedSort = true }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = expandedSort, onDismissRequest = { expandedSort = false }) {
                        DropdownMenuItem(text = { Text("Newest first") }, onClick = { viewModel.setSortBy("newest"); expandedSort = false })
                        DropdownMenuItem(text = { Text("Oldest first") }, onClick = { viewModel.setSortBy("oldest"); expandedSort = false })
                        DropdownMenuItem(text = { Text("Alphabetical") }, onClick = { viewModel.setSortBy("name"); expandedSort = false })
                        DropdownMenuItem(text = { Text("File Size") }, onClick = { viewModel.setSortBy("size"); expandedSort = false })
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                placeholderText = "Search documents title or OCR index..."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sort subtitle label
            Text(
                text = "Sorting by: ${
                    when (sortBy) {
                        "newest" -> "Newest Date"
                        "oldest" -> "Oldest Date"
                        "name" -> "Name (A-Z)"
                        "size" -> "File Size"
                        else -> "Default"
                    }
                }",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Docs grid/list
            if (docs.isEmpty()) {
                EmptyState(
                    title = "No Documents Found",
                    description = "Change folder filters, clear your search text, or start scanning to create new items offline.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("saved_docs_scroller")
                ) {
                    items(docs) { doc ->
                        DocumentCard(
                            document = doc,
                            onClick = { onNavigateToDetails(doc.id) },
                            onLongClick = {},
                            onShareClick = { shareFileOffline(context, doc.pdfPath ?: "", "application/pdf") },
                            onDeleteClick = { docToDelete = doc },
                            onRenameClick = { docToRename = doc }
                        )
                    }
                }
            }
        }
    }

    if (docToRename != null) {
        RenameModal(
            currentName = docToRename?.name ?: "",
            onDismiss = { docToRename = null },
            onConfirm = { newName ->
                docToRename?.let { viewModel.renameDocument(it.id, newName) }
                docToRename = null
            }
        )
    }

    if (docToDelete != null) {
        ConfirmDeleteModal(
            title = "Delete Document",
            message = "Are you sure you want to permanently delete '${docToDelete?.name}'?",
            onDismiss = { docToDelete = null },
            onConfirm = {
                docToDelete?.let { viewModel.deleteDocument(it) }
                docToDelete = null
            }
        )
    }
}

// --- 10. DOCUMENT DETAILS SCREEN ---
@Composable
fun DocumentDetailsScreen(
    docId: String,
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onNavigateToOcr: (String) -> Unit
) {
    val context = LocalContext.current
    var loadedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    val folders by viewModel.allFolders.collectAsState()

    var showRenameModal by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(docId) {
        viewModel.allDocuments.collect { list ->
            val doc = list.firstOrNull { it.id == docId }
            if (doc != null) {
                loadedDoc = doc
            }
        }
    }

    val doc = loadedDoc
    if (doc == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val formattedDate = remember(doc.createdAt) {
        SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(doc.createdAt))
    }
    val formattedSize = remember(doc.fileSize) {
        val kb = doc.fileSize / 1024f
        if (kb < 1024) String.format("%.1f KB", kb)
        else String.format("%.1f MB", kb / 1024f)
    }

    Scaffold(
        topBar = {
            Header(
                title = doc.name,
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Meta container card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Document Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("File Name", fontWeight = FontWeight.Medium, color = Color.Gray)
                        Text(doc.name, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Page Count", fontWeight = FontWeight.Medium, color = Color.Gray)
                        Text("${doc.pageCount} page(s)", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("File Size", fontWeight = FontWeight.Medium, color = Color.Gray)
                        Text(formattedSize, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Created Date", fontWeight = FontWeight.Medium, color = Color.Gray)
                        Text(formattedDate, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Local PDF Path", fontWeight = FontWeight.Medium, color = Color.Gray)
                        Text(
                            doc.pdfPath?.substringAfterLast("/Documents/") ?: "N/A",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                    }
                }
            }

            // Quick Operations Options List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Extract & View OCR Text", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Search, copy, or export extracted text offline") },
                    leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigateToOcr(doc.id) }
                        .testTag("ocr_details_btn")
                )

                ListItem(
                    headlineContent = { Text("Move to Folder", fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        val folderName = folders.firstOrNull { it.id == doc.folderId }?.name ?: "None (Uncategorized)"
                        Text("Currently in: $folderName")
                    },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showFolderPicker = true }
                )

                ListItem(
                    headlineContent = { Text("Rename PDF", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Change document name and file name") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showRenameModal = true }
                )
            }

            // PDF Share & Delete buttons bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        doc.pdfPath?.let { path ->
                            shareFileOffline(context, path, "application/pdf")
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("share_pdf_details_btn")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PDF")
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("delete_doc_details_btn")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }

    if (showRenameModal) {
        RenameModal(
            currentName = doc.name,
            onDismiss = { showRenameModal = false },
            onConfirm = { newName ->
                viewModel.renameDocument(doc.id, newName)
                showRenameModal = false
            }
        )
    }

    if (showFolderPicker) {
        FolderPickerModal(
            folders = folders,
            currentFolderId = doc.folderId,
            onDismiss = { showFolderPicker = false },
            onFolderSelected = { fId ->
                viewModel.moveDocumentToFolder(doc.id, fId)
                showFolderPicker = false
            },
            onCreateFolderClick = { name ->
                viewModel.createFolder(name)
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDeleteModal(
            title = "Delete Scan Document",
            message = "Permanently delete '${doc.name}'? This cannot be undone.",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteDocument(doc)
                showDeleteConfirm = false
                onBack() // pop
            }
        )
    }
}

// --- 11. OCR TEXT SCREEN ---
@Composable
fun OcrTextScreen(
    docId: String,
    viewModel: ScannerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var loadedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var ocrPagesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var ocrSearchQuery by remember { mutableStateOf("") }
    var highlightedMatches by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(docId) {
        viewModel.allDocuments.collect { list ->
            val doc = list.firstOrNull { it.id == docId }
            if (doc != null) {
                loadedDoc = doc
                // Split OCR into pages
                ocrPagesList = if (doc.ocrText.contains("--- PAGE BREAK ---")) {
                    doc.ocrText.split("--- PAGE BREAK ---")
                } else {
                    listOf(doc.ocrText)
                }
            }
        }
    }

    // Interactive query-highlights
    LaunchedEffect(ocrSearchQuery, ocrPagesList) {
        highlightedMatches = OcrService.searchOcrText(ocrPagesList, ocrSearchQuery)
    }

    Scaffold(
        topBar = {
            Header(
                title = "Offline Extracted OCR",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Search inside OCR query field
            OutlinedTextField(
                value = ocrSearchQuery,
                onValueChange = { ocrSearchQuery = it },
                placeholder = { Text("Search words inside OCR...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ocr_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Text block page-by-page
            if (ocrPagesList.isEmpty() || ocrPagesList.all { it.trim().isEmpty() }) {
                EmptyState(
                    title = "No OCR Text Extracted",
                    description = "This document contains no recognized text or OCR was disabled during PDF export.",
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(40.dp)) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("ocr_scroller")
                ) {
                    itemsIndexed(ocrPagesList) { index, pageText ->
                        val isPageMatch = highlightedMatches.contains(index)
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPageMatch && ocrSearchQuery.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "PAGE ${index + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Dynamic Highlight Rendering text
                                val textToDisplay = pageText.trim()
                                if (ocrSearchQuery.isNotEmpty() && textToDisplay.contains(ocrSearchQuery, ignoreCase = true)) {
                                    val annotatedString = buildAnnotatedString {
                                        var startIndex = 0
                                        while (startIndex < textToDisplay.length) {
                                            val matchIndex = textToDisplay.indexOf(ocrSearchQuery, startIndex, ignoreCase = true)
                                            if (matchIndex == -1) {
                                                append(textToDisplay.substring(startIndex))
                                                break
                                            } else {
                                                append(textToDisplay.substring(startIndex, matchIndex))
                                                withStyle(SpanStyle(background = Color.Yellow, color = Color.Black, fontWeight = FontWeight.Bold)) {
                                                    append(textToDisplay.substring(matchIndex, matchIndex + ocrSearchQuery.length))
                                                }
                                                startIndex = matchIndex + ocrSearchQuery.length
                                            }
                                        }
                                    }
                                    Text(
                                        text = annotatedString,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                                    )
                                } else {
                                    Text(
                                        text = textToDisplay,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val text = ocrPagesList.joinToString("\n")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Extracted OCR Text", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied OCR text to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ocrPagesList.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Text")
                }

                Button(
                    onClick = {
                        val text = ocrPagesList.joinToString("\n")
                        val path = OcrService.exportTextFile(context, loadedDoc?.name ?: "scanned_text", text)
                        if (path != null) {
                            Toast.makeText(context, "Exported as .txt locally!", Toast.LENGTH_SHORT).show()
                            shareFileOffline(context, path, "text/plain")
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ocr_export_btn"),
                    enabled = ocrPagesList.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export TXT")
                }
            }
        }
    }
}

// --- 12. SETTINGS SCREEN ---
@Composable
fun SettingsScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit
) {
    val darkMode by viewModel.darkMode.collectAsState()
    val pdfQuality by viewModel.pdfQuality.collectAsState()
    val defaultScanMode by viewModel.defaultScanMode.collectAsState()
    val defaultFilter by viewModel.defaultFilter.collectAsState()
    val autoOcr by viewModel.autoOcr.collectAsState()
    val saveOriginals by viewModel.saveOriginals.collectAsState()

    val storageSize by viewModel.storageSizeString.collectAsState()
    val cacheSize by viewModel.cacheSizeString.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.updateStorageMetrics()
    }

    Scaffold(
        topBar = { Header(title = "App Settings", onBackClick = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Appearance
            Text("APPEARANCE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark Theme / Light Theme", fontWeight = FontWeight.Bold)
                        Text("Toggle default application theme mode", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("settings_dark_mode_switch")
                    )
                }
            }

            // Section 2: PDF & OCR Quality
            Text("PDF & OCR PREFERENCES", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default PDF Quality (${pdfQuality}%)", fontWeight = FontWeight.Bold)
                    Slider(
                        value = pdfQuality.toFloat(),
                        onValueChange = { viewModel.setPdfQuality(it.toInt()) },
                        valueRange = 40f..100f,
                        steps = 5,
                        modifier = Modifier.testTag("settings_pdf_quality_slider")
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic OCR Text Recognition", fontWeight = FontWeight.Bold)
                            Text("Extract searchable words from images offline", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = autoOcr,
                            onCheckedChange = { viewModel.toggleAutoOcr() },
                            modifier = Modifier.testTag("settings_ocr_switch")
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save Unfiltered Originals", fontWeight = FontWeight.Bold)
                            Text("Keep uncropped/unfiltered draft pages on storage", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = saveOriginals,
                            onCheckedChange = { viewModel.toggleSaveOriginals() },
                            modifier = Modifier.testTag("settings_originals_switch")
                        )
                    }
                }
            }

            // Section 3: Storage & Cache Cleaners
            Text("STORAGE UTILITIES", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("App Storage Used", fontWeight = FontWeight.Bold)
                            Text("Total document database space occupied", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text(storageSize, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cache Temp Files", fontWeight = FontWeight.Bold)
                            Text("Temporary preview sizes: $cacheSize", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                viewModel.clearCache()
                                Toast.makeText(viewModel.getApplication(), "Cache cleared!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.testTag("settings_clear_cache_btn")
                        ) {
                            Text("Clear Cache")
                        }
                    }
                }
            }
        }
    }
}
