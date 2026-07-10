package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentDatabase
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import com.example.data.FolderEntity
import com.example.data.ScannedPage
import com.example.services.FilterService
import com.example.services.ImageService
import com.example.services.OcrService
import com.example.services.PdfService
import com.example.services.ScannerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val prefs = application.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    // Exposed flows for Room queries
    val allDocuments: StateFlow<List<DocumentEntity>>
    val allFolders: StateFlow<List<FolderEntity>>

    // Search and filter queries
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow<Long?>(null)
    val selectedFolderFilter = _selectedFolderFilter.asStateFlow()

    private val _sortBy = MutableStateFlow("newest") // newest, oldest, name, size
    val sortBy = _sortBy.asStateFlow()

    // Combined filtered & sorted list of documents
    val filteredDocuments: StateFlow<List<DocumentEntity>>

    // Draft scanner session state
    private val _draftPages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val draftPages = _draftPages.asStateFlow()

    private val _currentEditingPageIndex = MutableStateFlow(0)
    val currentEditingPageIndex = _currentEditingPageIndex.asStateFlow()

    // Global loading overlay status
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loadingText = MutableStateFlow("")
    val loadingText = _loadingText.asStateFlow()

    // Settings
    private val _darkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val darkMode = _darkMode.asStateFlow()

    private val _pdfQuality = MutableStateFlow(prefs.getInt("pdf_quality", 80))
    val pdfQuality = _pdfQuality.asStateFlow()

    private val _defaultScanMode = MutableStateFlow(prefs.getString("default_scan_mode", "Color") ?: "Color")
    val defaultScanMode = _defaultScanMode.asStateFlow()

    private val _defaultFilter = MutableStateFlow(prefs.getString("default_filter", "Original") ?: "Original")
    val defaultFilter = _defaultFilter.asStateFlow()

    private val _autoOcr = MutableStateFlow(prefs.getBoolean("auto_ocr", true))
    val autoOcr = _autoOcr.asStateFlow()

    private val _saveOriginals = MutableStateFlow(prefs.getBoolean("save_originals", true))
    val saveOriginals = _saveOriginals.asStateFlow()

    // Storage info
    private val _storageSizeString = MutableStateFlow("0 KB")
    val storageSizeString = _storageSizeString.asStateFlow()

    private val _cacheSizeString = MutableStateFlow("0 KB")
    val cacheSizeString = _cacheSizeString.asStateFlow()

    init {
        // Initialize offline files directories
        ScannerService.initDirectories(application)

        // Initialize database and repository
        val db = DocumentDatabase.getDatabase(application)
        repository = DocumentRepository(db.documentDao())

        allDocuments = repository.allDocuments.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allFolders = repository.allFolders.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Combine search, sort, and folder filters with list
        filteredDocuments = combine(
            allDocuments,
            _searchQuery,
            _selectedFolderFilter,
            _sortBy
        ) { docs, query, folderId, sort ->
            var list = docs
            if (folderId != null) {
                list = list.filter { it.folderId == folderId }
            }
            if (query.isNotEmpty()) {
                list = list.filter {
                    it.name.contains(query, ignoreCase = true) || 
                    it.ocrText.contains(query, ignoreCase = true)
                }
            }
            when (sort) {
                "newest" -> list.sortedByDescending { it.createdAt }
                "oldest" -> list.sortedBy { it.createdAt }
                "name" -> list.sortedBy { it.name.lowercase() }
                "size" -> list.sortedByDescending { it.fileSize }
                else -> list
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        updateStorageMetrics()
    }

    // --- Search, Filters & Sorting ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFolderFilter(folderId: Long?) {
        _selectedFolderFilter.value = folderId
    }

    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }

    // --- Active Scanner Draft Management ---
    fun setDraftPages(paths: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = paths.map { path ->
                // Apply default filter if configured in settings
                val defaultFilterVal = FilterService.FilterType.entries.firstOrNull { 
                    it.displayName.equals(_defaultFilter.value, ignoreCase = true) 
                } ?: FilterService.FilterType.ORIGINAL

                val enhancedPath = if (defaultFilterVal != FilterService.FilterType.ORIGINAL) {
                    val originalBitmap = ImageService.loadBitmap(path)
                    if (originalBitmap != null) {
                        val enhancedBitmap = FilterService.applyFilter(originalBitmap, defaultFilterVal)
                        val imagesDir = ScannerService.getSubDir(getApplication(), "Images")
                        val file = File(imagesDir, "enhanced_${UUID.randomUUID()}.jpg")
                        ImageService.saveBitmap(enhancedBitmap, file)
                        originalBitmap.recycle()
                        enhancedBitmap.recycle()
                        file.absolutePath
                    } else {
                        path
                    }
                } else {
                    path
                }

                ScannedPage(
                    originalPath = path,
                    enhancedPath = enhancedPath,
                    filterType = defaultFilterVal
                )
            }
            _draftPages.value = pages
            _currentEditingPageIndex.value = 0
        }
    }

    fun addDraftPage(path: String) {
        val current = _draftPages.value.toMutableList()
        current.add(ScannedPage(originalPath = path, enhancedPath = path))
        _draftPages.value = current
    }

    fun updateDraftPage(updated: ScannedPage, index: Int) {
        val current = _draftPages.value.toMutableList()
        if (index in current.indices) {
            current[index] = updated
            _draftPages.value = current
        }
    }

    fun deleteDraftPage(index: Int) {
        val current = _draftPages.value.toMutableList()
        if (index in current.indices) {
            val page = current.removeAt(index)
            // Delete files to save storage
            try {
                if (page.enhancedPath != page.originalPath) {
                    File(page.enhancedPath).delete()
                }
                if (!_saveOriginals.value) {
                    File(page.originalPath).delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _draftPages.value = current
            if (_currentEditingPageIndex.value >= current.size && current.isNotEmpty()) {
                _currentEditingPageIndex.value = current.size - 1
            }
        }
    }

    fun duplicateDraftPage(index: Int) {
        val current = _draftPages.value.toMutableList()
        if (index in current.indices) {
            val page = current[index]
            val copy = page.copy(id = UUID.randomUUID().toString())
            current.add(index + 1, copy)
            _draftPages.value = current
        }
    }

    fun reorderDraftPages(fromIndex: Int, toIndex: Int) {
        val current = _draftPages.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _draftPages.value = current
        }
    }

    fun clearDraft() {
        _draftPages.value = emptyList()
        _currentEditingPageIndex.value = 0
    }

    fun setCurrentEditingPageIndex(index: Int) {
        _currentEditingPageIndex.value = index
    }

    // --- Crop & Rotate Operations ---
    fun rotateCurrentPage(degrees: Float) {
        val index = _currentEditingPageIndex.value
        val current = _draftPages.value
        if (index in current.indices) {
            val page = current[index]
            showLoading("Rotating...")
            viewModelScope.launch(Dispatchers.IO) {
                // Rotate both original and enhanced
                val rotOriginal = ImageService.rotateImage(page.originalPath, degrees)
                val rotEnhanced = if (page.enhancedPath != page.originalPath) {
                    ImageService.rotateImage(page.enhancedPath, degrees)
                } else {
                    rotOriginal
                }
                
                if (rotOriginal != null && rotEnhanced != null) {
                    val updatedPage = page.copy(
                        originalPath = rotOriginal,
                        enhancedPath = rotEnhanced,
                        rotation = (page.rotation + degrees) % 360f
                    )
                    withContext(Dispatchers.Main) {
                        updateDraftPage(updatedPage, index)
                    }
                }
                hideLoading()
            }
        }
    }

    fun cropCurrentPage(rect: RectF) {
        val index = _currentEditingPageIndex.value
        val current = _draftPages.value
        if (index in current.indices) {
            val page = current[index]
            showLoading("Cropping...")
            viewModelScope.launch(Dispatchers.IO) {
                // Crop original image and regenerate enhanced image
                val croppedOriginal = ImageService.cropImage(page.originalPath, rect)
                if (croppedOriginal != null) {
                    // Re-apply filter on the new cropped base
                    val newEnhancedPath = if (page.filterType != FilterService.FilterType.ORIGINAL) {
                        val croppedBitmap = ImageService.loadBitmap(croppedOriginal)
                        if (croppedBitmap != null) {
                            val enhancedBitmap = FilterService.applyFilter(croppedBitmap, page.filterType)
                            val imagesDir = ScannerService.getSubDir(getApplication(), "Images")
                            val file = File(imagesDir, "enhanced_${UUID.randomUUID()}.jpg")
                            ImageService.saveBitmap(enhancedBitmap, file)
                            croppedBitmap.recycle()
                            enhancedBitmap.recycle()
                            file.absolutePath
                        } else {
                            croppedOriginal
                        }
                    } else {
                        croppedOriginal
                    }

                    val updatedPage = page.copy(
                        originalPath = croppedOriginal,
                        enhancedPath = newEnhancedPath,
                        cropRect = rect
                    )
                    withContext(Dispatchers.Main) {
                        updateDraftPage(updatedPage, index)
                    }
                }
                hideLoading()
            }
        }
    }

    fun applyFilterToCurrentPage(filterType: FilterService.FilterType) {
        val index = _currentEditingPageIndex.value
        val current = _draftPages.value
        if (index in current.indices) {
            val page = current[index]
            showLoading("Applying ${filterType.displayName}...")
            viewModelScope.launch(Dispatchers.IO) {
                val originalBitmap = ImageService.loadBitmap(page.originalPath)
                if (originalBitmap != null) {
                    val enhancedBitmap = FilterService.applyFilter(originalBitmap, filterType)
                    val imagesDir = ScannerService.getSubDir(getApplication(), "Images")
                    val file = File(imagesDir, "enhanced_${UUID.randomUUID()}.jpg")
                    ImageService.saveBitmap(enhancedBitmap, file)
                    originalBitmap.recycle()
                    enhancedBitmap.recycle()

                    // If old enhanced was custom and distinct from original, clean it up
                    if (page.enhancedPath != page.originalPath) {
                        File(page.enhancedPath).delete()
                    }

                    val updatedPage = page.copy(
                        enhancedPath = file.absolutePath,
                        filterType = filterType
                    )
                    withContext(Dispatchers.Main) {
                        updateDraftPage(updatedPage, index)
                    }
                }
                hideLoading()
            }
        }
    }

    fun resetCurrentPageEdits() {
        val index = _currentEditingPageIndex.value
        val current = _draftPages.value
        if (index in current.indices) {
            val page = current[index]
            val updated = page.copy(
                enhancedPath = page.originalPath,
                filterType = FilterService.FilterType.ORIGINAL,
                cropRect = RectF(0f, 0f, 1f, 1f),
                rotation = 0f
            )
            updateDraftPage(updated, index)
        }
    }

    // --- Save Document (Generate PDF & Local OCR) ---
    fun saveDraftAsDocument(
        pdfName: String,
        addPageNumbers: Boolean,
        watermarkText: String?,
        compressBeforePdf: Boolean,
        onComplete: (String) -> Unit
    ) {
        val pages = _draftPages.value
        if (pages.isEmpty()) return

        showLoading("Generating Document...")
        viewModelScope.launch(Dispatchers.IO) {
            val imagePaths = pages.map { it.enhancedPath }
            
            // 1. Generate PDF
            val finalPdfName = if (pdfName.trim().isEmpty()) {
                "Document_${System.currentTimeMillis()}"
            } else {
                pdfName.trim()
            }
            val pdfFile = PdfService.generatePdfFromImages(
                getApplication(),
                imagePaths,
                finalPdfName,
                addPageNumbers,
                watermarkText,
                compressBeforePdf
            )

            if (pdfFile != null) {
                // 2. Generate page-by-page OCR Text offline using ML Kit
                showLoading("Extracting Text (OCR)...")
                val ocrTextsList = if (_autoOcr.value) {
                    OcrService.extractTextFromPages(getApplication(), imagePaths)
                } else {
                    List(imagePaths.size) { "" }
                }
                
                // Format full searchable index text
                val fullOcrText = ocrTextsList.joinToString("\n--- PAGE BREAK ---\n")

                // 3. Create a Thumbnail of the first page
                showLoading("Creating Preview...")
                val thumbPath = ImageService.createThumbnail(getApplication(), imagePaths.first()) ?: ""

                // 4. Save to Room database
                showLoading("Saving metadata...")
                val documentId = UUID.randomUUID().toString()
                val originalPathsJoined = pages.map { it.originalPath }.joinToString(",")
                val enhancedPathsJoined = pages.map { it.enhancedPath }.joinToString(",")

                val doc = DocumentEntity(
                    id = documentId,
                    name = finalPdfName,
                    pdfPath = pdfFile.absolutePath,
                    pageCount = pages.size,
                    fileSize = pdfFile.length(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    folderId = _selectedFolderFilter.value, // attach to currently active folder filter if any
                    ocrText = fullOcrText,
                    thumbnailPath = thumbPath,
                    originalImagePaths = originalPathsJoined,
                    enhancedImagePaths = enhancedPathsJoined
                )

                repository.saveDocument(doc)

                // Clean up draft and unneeded originals/enhanced images if not saved
                if (!_saveOriginals.value) {
                    pages.forEach { page ->
                        if (page.originalPath != page.enhancedPath) {
                            File(page.originalPath).delete()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    clearDraft()
                    updateStorageMetrics()
                    hideLoading()
                    onComplete(documentId)
                }
            } else {
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    // --- Document CRUD Operations ---
    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete associated PDF file
            doc.pdfPath?.let {
                PdfService.deletePdf(it)
            }
            // Delete thumbnail
            if (doc.thumbnailPath.isNotEmpty()) {
                File(doc.thumbnailPath).delete()
            }
            // Delete original / enhanced images
            doc.originalPathsList.forEach { File(it).delete() }
            doc.enhancedPathsList.forEach { File(it).delete() }

            repository.deleteDocument(doc)
            updateStorageMetrics()
        }
    }

    fun renameDocument(docId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = repository.getDocumentByIdSync(docId)
            if (doc != null && doc.pdfPath != null) {
                val oldFile = File(doc.pdfPath)
                if (oldFile.exists()) {
                    val parent = oldFile.parentFile
                    val sanitizedName = if (newName.endsWith(".pdf", ignoreCase = true)) newName else "$newName.pdf"
                    val newFile = File(parent, sanitizedName)
                    if (oldFile.renameTo(newFile)) {
                        val updatedDoc = doc.copy(
                            name = newName,
                            pdfPath = newFile.absolutePath,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateDocument(updatedDoc)
                    } else {
                        repository.renameDocument(docId, newName)
                    }
                } else {
                    repository.renameDocument(docId, newName)
                }
            }
        }
    }

    fun moveDocumentToFolder(docId: String, folderId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveDocumentToFolder(docId, folderId)
        }
    }

    // --- Folder Operations ---
    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createFolder(name)
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFolder(id)
            // Detach any documents associated with this folder (move to null)
            val docs = allDocuments.value.filter { it.folderId == id }
            docs.forEach { doc ->
                repository.moveDocumentToFolder(doc.id, null)
            }
        }
    }

    // --- Settings UI Toggles ---
    fun toggleDarkMode() {
        val newVal = !_darkMode.value
        _darkMode.value = newVal
        prefs.edit().putBoolean("dark_mode", newVal).apply()
    }

    fun setPdfQuality(quality: Int) {
        _pdfQuality.value = quality
        prefs.edit().putInt("pdf_quality", quality).apply()
    }

    fun setDefaultScanMode(mode: String) {
        _defaultScanMode.value = mode
        prefs.edit().putString("default_scan_mode", mode).apply()
    }

    fun setDefaultFilter(filter: String) {
        _defaultFilter.value = filter
        prefs.edit().putString("default_filter", filter).apply()
    }

    fun toggleAutoOcr() {
        val newVal = !_autoOcr.value
        _autoOcr.value = newVal
        prefs.edit().putBoolean("auto_ocr", newVal).apply()
    }

    fun toggleSaveOriginals() {
        val newVal = !_saveOriginals.value
        _saveOriginals.value = newVal
        prefs.edit().putBoolean("save_originals", newVal).apply()
    }

    // --- Cache and Storage Utils ---
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            ScannerService.clearCache(getApplication())
            updateStorageMetrics()
        }
    }

    fun updateStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheBytes = ScannerService.getCacheSize(getApplication())
            val appBytes = ScannerService.getAppStorageSize(getApplication())
            
            _cacheSizeString.value = formatBytes(cacheBytes)
            _storageSizeString.value = formatBytes(appBytes)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024f
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024f
        return String.format("%.1f MB", mb)
    }

    // --- Loading UI Helper ---
    private fun showLoading(text: String) {
        _loadingText.value = text
        _isLoading.value = true
    }

    private fun hideLoading() {
        _isLoading.value = false
        _loadingText.value = ""
    }
}
