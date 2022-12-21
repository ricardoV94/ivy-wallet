package com.ivy.import_data

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.util.IvyPreview

import com.ivy.import_data.flow.ImportFrom
import com.ivy.import_data.flow.ImportProcessing
import com.ivy.import_data.flow.ImportResultUI
import com.ivy.import_data.flow.instructions.ImportInstructions
import com.ivy.onboarding.viewmodel.OnboardingViewModel
import com.ivy.wallet.domain.deprecated.logic.csv.model.ImportApp
import com.ivy.wallet.domain.deprecated.logic.csv.model.ImportResult

@OptIn(ExperimentalStdlibApi::class)
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.ImportCSVScreen() {
    val viewModel: ImportViewModel = hiltViewModel()

    val importStep by viewModel.importStep.observeAsState(ImportStep.IMPORT_FROM)
    val importType by viewModel.importType.observeAsState()
    val importProgressPercent by viewModel.importProgressPercent.observeAsState(0)
    val importResult by viewModel.importResult.observeAsState()

    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    val context = LocalContext.current

    UI(
//        screen = screen,
        importStep = importStep,
        importApp = importType,
        importProgressPercent = importProgressPercent,
        importResult = importResult,

        onChooseImportType = viewModel::setImportType,
        onUploadCSVFile = { viewModel.uploadFile(context) },
        onSkip = {
            viewModel.skip(
                onboardingViewModel = onboardingViewModel
            )
        },
        onFinish = {
            viewModel.finish(
                onboardingViewModel = onboardingViewModel
            )
        }
    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
//    screen: Import,

    importStep: ImportStep,
    importApp: ImportApp?,
    importProgressPercent: Int,
    importResult: ImportResult?,

    onChooseImportType: (ImportApp) -> Unit = {},
    onUploadCSVFile: () -> Unit = {},
    onSkip: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    when (importStep) {
        ImportStep.IMPORT_FROM -> {
            ImportFrom(
                hasSkip = false, //screen.launchedFromOnboarding,
                onSkip = onSkip,
                onImportFrom = onChooseImportType
            )
        }
        ImportStep.INSTRUCTIONS -> {
            ImportInstructions(
                hasSkip = false, //screen.launchedFromOnboarding,
                importApp = importApp!!,
                onSkip = onSkip,
                onUploadClick = onUploadCSVFile
            )
        }
        ImportStep.LOADING -> {
            ImportProcessing(
                progressPercent = importProgressPercent
            )
        }
        ImportStep.RESULT -> {
            ImportResultUI(
                result = importResult!!
            ) {
                onFinish()
            }
        }
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview() {
    IvyPreview {
        UI(
//            screen = Import(launchedFromOnboarding = true),
            importStep = ImportStep.IMPORT_FROM,
            importApp = null,
            importProgressPercent = 0,
            importResult = null
        )
    }
}