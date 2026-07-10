package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CropEditorScreen
import com.example.ui.DocumentDetailsScreen
import com.example.ui.FilterEditorScreen
import com.example.ui.HomeScreen
import com.example.ui.OcrTextScreen
import com.example.ui.OnboardingScreen
import com.example.ui.PageManagerScreen
import com.example.ui.PdfPreviewScreen
import com.example.ui.SavedDocumentsScreen
import com.example.ui.ScannerScreen
import com.example.ui.ScannerViewModel
import com.example.ui.Screen
import com.example.ui.SettingsScreen
import com.example.ui.SplashScreen
import com.example.ui.components.LoadingOverlay
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: ScannerViewModel = viewModel()
      val isDarkThemeSetting by viewModel.darkMode.collectAsState()

      MyApplicationTheme(darkTheme = isDarkThemeSetting) {
        val isLoading by viewModel.isLoading.collectAsState()
        val loadingText by viewModel.loadingText.collectAsState()

        var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
        var activeDocId by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize()) {
          when (currentScreen) {
            Screen.SPLASH -> SplashScreen(
              onNavigateToOnboarding = { currentScreen = Screen.ONBOARDING },
              onNavigateToHome = { currentScreen = Screen.HOME }
            )
            Screen.ONBOARDING -> OnboardingScreen(
              onGetStartedClick = { currentScreen = Screen.HOME }
            )
            Screen.HOME -> HomeScreen(
              viewModel = viewModel,
              onNavigateToScanner = { currentScreen = Screen.SCANNER },
              onNavigateToSaved = { currentScreen = Screen.SAVED_DOCUMENTS },
              onNavigateToSettings = { currentScreen = Screen.SETTINGS },
              onNavigateToDetails = { docId ->
                activeDocId = docId
                currentScreen = Screen.DOCUMENT_DETAILS
              }
            )
            Screen.SCANNER -> ScannerScreen(
              viewModel = viewModel,
              onBack = {
                viewModel.clearDraft()
                currentScreen = Screen.HOME
              },
              onGoToCrop = { currentScreen = Screen.CROP_EDITOR }
            )
            Screen.CROP_EDITOR -> CropEditorScreen(
              viewModel = viewModel,
              onBack = { currentScreen = Screen.SCANNER },
              onGoToFilter = { currentScreen = Screen.FILTER_EDITOR }
            )
            Screen.FILTER_EDITOR -> FilterEditorScreen(
              viewModel = viewModel,
              onBack = { currentScreen = Screen.CROP_EDITOR },
              onGoToManager = { currentScreen = Screen.PAGE_MANAGER }
            )
            Screen.PAGE_MANAGER -> PageManagerScreen(
              viewModel = viewModel,
              onBack = { currentScreen = Screen.FILTER_EDITOR },
              onGoToPreview = { docId ->
                activeDocId = docId
                currentScreen = Screen.PDF_PREVIEW
              }
            )
            Screen.PDF_PREVIEW -> PdfPreviewScreen(
              docId = activeDocId,
              viewModel = viewModel,
              onBack = { currentScreen = Screen.HOME },
              onGoToDetails = { docId ->
                activeDocId = docId
                currentScreen = Screen.DOCUMENT_DETAILS
              }
            )
            Screen.SAVED_DOCUMENTS -> SavedDocumentsScreen(
              viewModel = viewModel,
              onBack = { currentScreen = Screen.HOME },
              onNavigateToDetails = { docId ->
                activeDocId = docId
                currentScreen = Screen.DOCUMENT_DETAILS
              }
            )
            Screen.DOCUMENT_DETAILS -> DocumentDetailsScreen(
              docId = activeDocId,
              viewModel = viewModel,
              onBack = { currentScreen = Screen.HOME },
              onNavigateToOcr = { docId ->
                activeDocId = docId
                currentScreen = Screen.OCR_TEXT
              }
            )
            Screen.OCR_TEXT -> OcrTextScreen(
              docId = activeDocId,
              viewModel = viewModel,
              onBack = { currentScreen = Screen.DOCUMENT_DETAILS }
            )
            Screen.SETTINGS -> SettingsScreen(
              viewModel = viewModel,
              onBack = { currentScreen = Screen.HOME }
            )
          }

          LoadingOverlay(visible = isLoading, text = loadingText)
        }
      }
    }
  }
}
