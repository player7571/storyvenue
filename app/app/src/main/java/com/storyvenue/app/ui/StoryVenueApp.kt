package com.storyvenue.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.storyvenue.app.auth.LoginUiState
import com.storyvenue.app.auth.LoginViewModel
import com.storyvenue.app.voice.MediaRecorderVoiceRecorder
import com.storyvenue.app.voice.VoiceInterviewPhase
import com.storyvenue.app.voice.VoiceInterviewUiState
import com.storyvenue.app.voice.VoiceInterviewViewModel
import com.storyvenue.app.voice.VoiceRecorder
import com.storyvenue.app.voice.VoiceRecordingFileStore

private enum class PendingRecordingAction {
    Start,
    Retry,
}

private enum class StoryVenueScreen(val route: String, val title: String) {
    Login("login", "로그인"),
    Home("home", "홈"),
    VoiceInterview("voice_interview", "음성 인터뷰"),
    Draft("draft", "초안"),
    BookPreview("book_preview", "책 미리보기"),
}

@Composable
fun StoryVenueApp() {
    val navController = rememberNavController()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StoryVenueScaffold(navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryVenueScaffold(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentScreen = StoryVenueScreen.entries.firstOrNull { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    } ?: StoryVenueScreen.Login

    Scaffold(
        topBar = {
            if (currentScreen != StoryVenueScreen.Login) {
                CenterAlignedTopAppBar(
                    title = { Text(text = currentScreen.title) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = StoryVenueScreen.Login.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(StoryVenueScreen.Login.route) {
                LoginRoute(
                    onContinue = {
                        navController.navigate(StoryVenueScreen.Home.route) {
                            popUpTo(StoryVenueScreen.Login.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(StoryVenueScreen.Home.route) {
                HomePlaceholder(
                    onVoiceInterviewClick = {
                        navController.navigate(StoryVenueScreen.VoiceInterview.route)
                    },
                    onDraftClick = {
                        navController.navigate(StoryVenueScreen.Draft.route)
                    },
                    onBookPreviewClick = {
                        navController.navigate(StoryVenueScreen.BookPreview.route)
                    },
                )
            }
            composable(StoryVenueScreen.VoiceInterview.route) {
                VoiceInterviewRoute(
                    onBackHome = {
                        navController.navigate(StoryVenueScreen.Home.route) {
                            popUpTo(StoryVenueScreen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(StoryVenueScreen.Draft.route) {
                DraftPlaceholder(
                    onOpenPreview = { navController.navigate(StoryVenueScreen.BookPreview.route) },
                )
            }
            composable(StoryVenueScreen.BookPreview.route) {
                BookPreviewPlaceholder(
                    onReturnHome = { navController.navigate(StoryVenueScreen.Home.route) },
                )
            }
        }
    }
}

@Composable
private fun LoginRoute(
    onContinue: () -> Unit,
    loginViewModel: LoginViewModel = viewModel(),
) {
    val uiState = loginViewModel.uiState

    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) {
            onContinue()
            loginViewModel.onLoginNavigationComplete()
        }
    }

    LoginScreen(
        uiState = uiState,
        onEmailChanged = loginViewModel::onEmailChanged,
        onPasswordChanged = loginViewModel::onPasswordChanged,
        onLoginClick = loginViewModel::onLoginClick,
    )
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
) {
    ScreenContainer {
        HeadingText(text = "편하게 시작해 보세요")
        BodyText(text = "실제 Supabase 인증 전 단계입니다. 지금은 이메일 입력, 로딩, 실패, 성공 이동 흐름만 먼저 연결합니다.")
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = { Text("비밀번호") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.isLoading) {
            LoadingPlaceholder()
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (uiState.errorMessage != null) {
            StatusCard(
                title = "로그인 실패 placeholder",
                content = uiState.errorMessage,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        PrimaryActionButton(
            label = if (uiState.isLoading) "로그인 중..." else "로그인",
            onClick = onLoginClick,
            enabled = !uiState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        BodyText(text = "TODO: 실제 Supabase Auth 연동 후 세션 유지와 에러 메시지를 교체합니다.")
    }
}

@Composable
private fun HomePlaceholder(
    onVoiceInterviewClick: () -> Unit,
    onDraftClick: () -> Unit,
    onBookPreviewClick: () -> Unit,
) {
    ScreenContainer {
        HeadingText(text = "오늘의 시작 화면")
        BodyText(text = "큰 버튼으로 주요 화면만 먼저 제공합니다.")
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(label = "음성 인터뷰 열기", onClick = onVoiceInterviewClick)
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(label = "초안 보기", onClick = onDraftClick)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(label = "책 미리보기", onClick = onBookPreviewClick)
    }
}

@Composable
private fun VoiceInterviewRoute(
    onBackHome: () -> Unit,
    voiceInterviewViewModel: VoiceInterviewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState = voiceInterviewViewModel.uiState
    val recorder: VoiceRecorder = remember { MediaRecorderVoiceRecorder() }
    val fileStore = remember { VoiceRecordingFileStore() }
    var pendingRecordingAction by remember { mutableStateOf(PendingRecordingAction.Start) }

    val startRecording: (Boolean) -> Unit = { isRetry ->
        val outputFile = fileStore.createTempFile(context)
        recorder.start(outputFile).fold(
            onSuccess = {
                voiceInterviewViewModel.onRecordingStarted(
                    recordingPath = outputFile.absolutePath,
                    isRetry = isRetry,
                )
            },
            onFailure = {
                outputFile.delete()
                voiceInterviewViewModel.onRecordingStartFailed(
                    "녹음을 시작하지 못했습니다. 기기 설정을 확인해 주세요.",
                )
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            voiceInterviewViewModel.onPermissionGranted()
            startRecording(pendingRecordingAction == PendingRecordingAction.Retry)
        } else {
            voiceInterviewViewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        voiceInterviewViewModel.onPermissionStateChecked(
            isGranted = hasRecordAudioPermission(context),
        )
    }

    LaunchedEffect(uiState.isSessionEnded) {
        if (uiState.isSessionEnded) {
            onBackHome()
            voiceInterviewViewModel.onSessionNavigationComplete()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.discard()
        }
    }

    val requestPermissionAndRecord: (PendingRecordingAction) -> Unit = { action ->
        pendingRecordingAction = action
        voiceInterviewViewModel.onPermissionRequestStarted()
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val stopRecording: () -> Unit = {
        recorder.stop().fold(
            onSuccess = { recordedFile ->
                voiceInterviewViewModel.onRecordingStopped(recordedFile.absolutePath)
            },
            onFailure = {
                voiceInterviewViewModel.onRecordingStopFailed(
                    "녹음을 멈추지 못했습니다. 다시 시도해 주세요.",
                )
            },
        )
    }

    VoiceInterviewScreen(
        uiState = uiState,
        onMicrophoneClick = {
            when {
                uiState.phase == VoiceInterviewPhase.Listening -> stopRecording()
                uiState.hasRecordAudioPermission -> startRecording(false)
                else -> requestPermissionAndRecord(PendingRecordingAction.Start)
            }
        },
        onRepeatLastQuestion = voiceInterviewViewModel::onRepeatLastQuestion,
        onRetrySpeech = {
            voiceInterviewViewModel.onRetrySpeechReady()
            if (uiState.hasRecordAudioPermission) {
                startRecording(true)
            } else {
                requestPermissionAndRecord(PendingRecordingAction.Retry)
            }
        },
        onEndSession = {
            recorder.discard()
            voiceInterviewViewModel.onEndSession()
        },
    )
}

@Composable
private fun VoiceInterviewScreen(
    uiState: VoiceInterviewUiState,
    onMicrophoneClick: () -> Unit,
    onRepeatLastQuestion: () -> Unit,
    onRetrySpeech: () -> Unit,
    onEndSession: () -> Unit,
) {
    ScreenContainer {
        HeadingText(text = "음성 인터뷰 준비 화면")
        StatusCard(
            title = "현재 상태",
            content = voicePhaseLabel(uiState.phase),
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "마지막 질문",
            content = uiState.lastAssistantQuestion,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "마지막 인식 결과",
            content = uiState.transcriptPlaceholder,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "안내",
            content = uiState.helperText,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "임시 저장 경로",
            content = uiState.currentRecordingPath
                ?: uiState.lastSavedRecordingPath
                ?: "cache/voice-recordings/voice_turn_{timestamp}.m4a",
        )
        if (uiState.isPermissionDenied) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = "권한 필요",
                content = "마이크 권한이 거부되어 녹음을 시작할 수 없습니다. 버튼을 다시 눌러 권한을 다시 요청해 주세요.",
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(
            label = voiceMicButtonLabel(uiState.phase),
            onClick = onMicrophoneClick,
            enabled = uiState.phase != VoiceInterviewPhase.Transcribing &&
                uiState.phase != VoiceInterviewPhase.Responding,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "다시 듣기",
                onClick = onRepeatLastQuestion,
                modifier = Modifier.weight(1f),
                enabled = uiState.phase != VoiceInterviewPhase.Listening &&
                    uiState.phase != VoiceInterviewPhase.Transcribing &&
                    uiState.phase != VoiceInterviewPhase.Responding,
            )
            OutlinedActionButton(
                label = "다시 말하기",
                onClick = onRetrySpeech,
                modifier = Modifier.weight(1f),
                enabled = uiState.phase != VoiceInterviewPhase.Transcribing &&
                    uiState.phase != VoiceInterviewPhase.Responding,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "세션 종료",
            onClick = onEndSession,
            enabled = true,
        )
    }
}

@Composable
private fun DraftPlaceholder(onOpenPreview: () -> Unit) {
    ScreenContainer {
        HeadingText(text = "장 초안 placeholder")
        StatusCard(
            title = "장 제목",
            content = "1장 어린 시절",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "본문",
            content = "이곳에 생성된 장별 초안이 크게 표시됩니다. 다음 단계에서 수정 요청과 재생성 흐름을 연결합니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "저장 상태",
            content = "아직 저장되지 않았습니다.",
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "수정 요청",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            OutlinedActionButton(
                label = "다시 생성",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(label = "책 미리보기 열기", onClick = onOpenPreview)
    }
}

@Composable
private fun BookPreviewPlaceholder(onReturnHome: () -> Unit) {
    ScreenContainer {
        HeadingText(text = "책 미리보기 placeholder")
        StatusCard(
            title = "미리보기 안내",
            content = "여러 장을 하나의 흐름으로 이어서 보여주는 화면입니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "본문 예시",
            content = "어린 시절의 장면들이 한 권의 이야기처럼 이어집니다. 다음 단계에서 저장된 chapter draft 를 조합해 실제 미리보기를 구성합니다.",
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(label = "홈으로 돌아가기", onClick = onReturnHome)
    }
}

@Composable
private fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        content()
    }
}

@Composable
private fun HeadingText(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 18.sp,
        lineHeight = 28.sp,
    )
}

private fun voicePhaseLabel(phase: VoiceInterviewPhase): String {
    return when (phase) {
        VoiceInterviewPhase.Idle -> "대기 중"
        VoiceInterviewPhase.Listening -> "듣는 중"
        VoiceInterviewPhase.Transcribing -> "변환 중"
        VoiceInterviewPhase.Responding -> "답변하는 중"
        VoiceInterviewPhase.Playing -> "재생 중"
    }
}

private fun voiceMicButtonLabel(phase: VoiceInterviewPhase): String {
    return when (phase) {
        VoiceInterviewPhase.Idle -> "큰 마이크 버튼"
        VoiceInterviewPhase.Listening -> "말하기 마치기"
        VoiceInterviewPhase.Transcribing -> "변환 중"
        VoiceInterviewPhase.Responding -> "답변 생성 중"
        VoiceInterviewPhase.Playing -> "다음 답변 말하기"
    }
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun StatusCard(title: String, content: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "로그인을 확인하는 중입니다.",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text(text = label, fontSize = 20.sp)
    }
}

@Composable
private fun OutlinedActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, fontSize = 18.sp)
        }
    }
}
