package com.storyvenue.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import com.storyvenue.app.AuthMode
import com.storyvenue.app.BookVersion
import com.storyvenue.app.ChapterDraft
import com.storyvenue.app.InterviewSessionSummary
import com.storyvenue.app.SessionMessage
import com.storyvenue.app.StoryVenueUiState
import com.storyvenue.app.StoryVenueViewModel
import com.storyvenue.app.voice.AudioReplyPlayer
import com.storyvenue.app.voice.MediaPlayerAudioReplyPlayer
import com.storyvenue.app.voice.MediaRecorderVoiceRecorder
import com.storyvenue.app.voice.VoiceInterviewPhase
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
    val storyVenueViewModel: StoryVenueViewModel = viewModel()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StoryVenueScaffold(
                navController = navController,
                storyVenueViewModel = storyVenueViewModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryVenueScaffold(
    navController: NavHostController,
    storyVenueViewModel: StoryVenueViewModel,
) {
    val uiState = storyVenueViewModel.uiState
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentScreen = StoryVenueScreen.entries.firstOrNull { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    } ?: StoryVenueScreen.Login

    LaunchedEffect(uiState.authSession, uiState.isRestoringSession) {
        if (uiState.isRestoringSession) {
            return@LaunchedEffect
        }

        if (uiState.authSession == null && currentScreen != StoryVenueScreen.Login) {
            navController.navigate(StoryVenueScreen.Login.route) {
                popUpTo(0)
                launchSingleTop = true
            }
        } else if (uiState.authSession != null && currentScreen == StoryVenueScreen.Login) {
            navController.navigate(StoryVenueScreen.Home.route) {
                popUpTo(StoryVenueScreen.Login.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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
                LoginScreen(
                    uiState = uiState,
                    onServerBaseUrlChanged = storyVenueViewModel::onServerBaseUrlChanged,
                    onEmailChanged = storyVenueViewModel::onEmailChanged,
                    onPasswordChanged = storyVenueViewModel::onPasswordChanged,
                    onAuthModeChanged = storyVenueViewModel::onAuthModeChanged,
                    onSubmit = storyVenueViewModel::submitAuth,
                )
            }
            composable(StoryVenueScreen.Home.route) {
                HomeScreen(
                    uiState = uiState,
                    onServerBaseUrlChanged = storyVenueViewModel::onServerBaseUrlChanged,
                    onNewSessionTitleChanged = storyVenueViewModel::onNewSessionTitleChanged,
                    onNewSessionThemeChanged = storyVenueViewModel::onNewSessionThemeChanged,
                    onCreateSession = storyVenueViewModel::createSession,
                    onSelectSession = storyVenueViewModel::selectSession,
                    onOpenVoiceInterview = {
                        navController.navigate(StoryVenueScreen.VoiceInterview.route)
                    },
                    onOpenDraft = {
                        navController.navigate(StoryVenueScreen.Draft.route)
                    },
                    onOpenBookPreview = {
                        navController.navigate(StoryVenueScreen.BookPreview.route)
                    },
                    onSignOut = storyVenueViewModel::signOut,
                )
            }
            composable(StoryVenueScreen.VoiceInterview.route) {
                VoiceInterviewRoute(
                    uiState = uiState,
                    storyVenueViewModel = storyVenueViewModel,
                    onBackHome = {
                        navController.navigate(StoryVenueScreen.Home.route) {
                            popUpTo(StoryVenueScreen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(StoryVenueScreen.Draft.route) {
                DraftScreen(
                    uiState = uiState,
                    onChapterTypeChanged = storyVenueViewModel::onChapterTypeChanged,
                    onGenerateChapter = storyVenueViewModel::generateChapter,
                    onSelectChapter = storyVenueViewModel::selectChapter,
                    onMoveChapterUp = storyVenueViewModel::moveChapterUp,
                    onMoveChapterDown = storyVenueViewModel::moveChapterDown,
                    onChapterInstructionChanged = storyVenueViewModel::onChapterInstructionChanged,
                    onReviseChapter = storyVenueViewModel::reviseSelectedChapter,
                    onRegenerateChapter = storyVenueViewModel::regenerateSelectedChapter,
                    onOpenPreview = {
                        navController.navigate(StoryVenueScreen.BookPreview.route)
                    },
                )
            }
            composable(StoryVenueScreen.BookPreview.route) {
                BookPreviewScreen(
                    uiState = uiState,
                    onBookTitleChanged = storyVenueViewModel::onBookTitleChanged,
                    onCompileBook = storyVenueViewModel::compileBook,
                    onSelectBook = storyVenueViewModel::selectBook,
                    onMoveChapterUp = storyVenueViewModel::moveChapterUp,
                    onMoveChapterDown = storyVenueViewModel::moveChapterDown,
                    onReturnHome = {
                        navController.navigate(StoryVenueScreen.Home.route) {
                            popUpTo(StoryVenueScreen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    uiState: StoryVenueUiState,
    onServerBaseUrlChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onAuthModeChanged: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
) {
    ScreenContainer {
        HeadingText(text = "편하게 시작해 보세요")
        BodyText(text = "이메일로 로그인하거나 가입한 뒤, 같은 세션에서 음성 인터뷰와 자서전 초안 작성을 이어갈 수 있습니다.")
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = uiState.serverBaseUrl,
            onValueChange = onServerBaseUrlChanged,
            label = { Text("서버 주소") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isAuthLoading,
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "로그인 모드",
                onClick = { onAuthModeChanged(AuthMode.SignIn) },
                modifier = Modifier.weight(1f),
                enabled = uiState.authMode != AuthMode.SignIn,
            )
            OutlinedActionButton(
                label = "회원가입 모드",
                onClick = { onAuthModeChanged(AuthMode.SignUp) },
                modifier = Modifier.weight(1f),
                enabled = uiState.authMode != AuthMode.SignUp,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isAuthLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = { Text("비밀번호") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isAuthLoading,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.isRestoringSession || uiState.isAuthLoading) {
            LoadingPlaceholder(
                message = if (uiState.isRestoringSession) {
                    "저장된 로그인 정보를 확인하는 중입니다."
                } else {
                    if (uiState.authMode == AuthMode.SignIn) "로그인 중입니다." else "가입 중입니다."
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (uiState.authMessage != null) {
            StatusCard(
                title = "안내",
                content = uiState.authMessage,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        PrimaryActionButton(
            label = if (uiState.authMode == AuthMode.SignIn) "로그인" else "회원가입",
            onClick = onSubmit,
            enabled = !uiState.isRestoringSession && !uiState.isAuthLoading,
        )
    }
}

@Composable
private fun HomeScreen(
    uiState: StoryVenueUiState,
    onServerBaseUrlChanged: (String) -> Unit,
    onNewSessionTitleChanged: (String) -> Unit,
    onNewSessionThemeChanged: (String) -> Unit,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onOpenVoiceInterview: () -> Unit,
    onOpenDraft: () -> Unit,
    onOpenBookPreview: () -> Unit,
    onSignOut: () -> Unit,
) {
    val selectedSession = uiState.sessions.firstOrNull { it.id == uiState.selectedSessionId }

    ScreenContainer {
        HeadingText(text = "오늘의 시작 화면")
        BodyText(text = uiState.authSession?.email?.let { "$it 로 로그인되어 있습니다." } ?: "로그인 정보가 없습니다.")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.serverBaseUrl,
            onValueChange = onServerBaseUrlChanged,
            label = { Text("서버 주소") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "선택된 세션",
            content = selectedSession?.let { "${it.title}\n${it.theme ?: "주제 없음"}" }
                ?: "아직 선택된 인터뷰 세션이 없습니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.homeMessage != null) {
            StatusCard(
                title = "안내",
                content = uiState.homeMessage,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (uiState.isHomeLoading) {
            LoadingPlaceholder(message = "세션 정보를 불러오는 중입니다.")
            Spacer(modifier = Modifier.height(12.dp))
        }
        StatusCard(
            title = "새 인터뷰 세션 만들기",
            content = "짧은 제목과 주제를 적어 두면 나중에 초안 생성 때 구분하기 쉽습니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.newSessionTitle,
            onValueChange = onNewSessionTitleChanged,
            label = { Text("세션 제목") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.newSessionTheme,
            onValueChange = onNewSessionThemeChanged,
            label = { Text("주제") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "새 세션 만들기",
            onClick = onCreateSession,
            enabled = !uiState.isHomeLoading,
        )
        Spacer(modifier = Modifier.height(20.dp))
        HeadingText(text = "내 세션 목록")
        if (uiState.sessions.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 인터뷰 세션이 없습니다. 위에서 새 세션을 먼저 만들어 주세요.",
            )
        } else {
            uiState.sessions.forEach { session ->
                SessionSummaryCard(
                    session = session,
                    isSelected = session.id == uiState.selectedSessionId,
                    onSelect = { onSelectSession(session.id) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryActionButton(
            label = "음성 인터뷰 열기",
            onClick = onOpenVoiceInterview,
            enabled = uiState.selectedSessionId != null,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "장 초안 보기",
            onClick = onOpenDraft,
            enabled = uiState.selectedSessionId != null,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "책 미리보기",
            onClick = onOpenBookPreview,
            enabled = uiState.chapters.isNotEmpty() || uiState.books.isNotEmpty(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "로그아웃",
            onClick = onSignOut,
        )
    }
}

@Composable
private fun VoiceInterviewRoute(
    uiState: StoryVenueUiState,
    storyVenueViewModel: StoryVenueViewModel,
    onBackHome: () -> Unit,
) {
    val context = LocalContext.current
    val recorder: VoiceRecorder = remember { MediaRecorderVoiceRecorder() }
    val audioReplyPlayer: AudioReplyPlayer = remember { MediaPlayerAudioReplyPlayer() }
    val fileStore = remember { VoiceRecordingFileStore() }
    var pendingRecordingAction by remember { mutableStateOf(PendingRecordingAction.Start) }

    val startRecording: (Boolean) -> Unit = { isRetry ->
        audioReplyPlayer.stop()
        val outputFile = fileStore.createTempFile(context)
        recorder.start(outputFile).fold(
            onSuccess = {
                storyVenueViewModel.onRecordingStarted(
                    recordingPath = outputFile.absolutePath,
                    isRetry = isRetry,
                )
            },
            onFailure = {
                outputFile.delete()
                storyVenueViewModel.onRecordingStartFailed(
                    "녹음을 시작하지 못했습니다. 기기 설정을 확인해 주세요.",
                )
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            storyVenueViewModel.onPermissionGranted()
            startRecording(pendingRecordingAction == PendingRecordingAction.Retry)
        } else {
            storyVenueViewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        storyVenueViewModel.onPermissionStateChecked(
            isGranted = hasRecordAudioPermission(context),
        )
    }

    LaunchedEffect(uiState.isSessionEnded) {
        if (uiState.isSessionEnded) {
            onBackHome()
            storyVenueViewModel.onSessionNavigationComplete()
        }
    }

    LaunchedEffect(uiState.pendingAssistantPlaybackUrl) {
        val audioUrl = uiState.pendingAssistantPlaybackUrl ?: return@LaunchedEffect
        storyVenueViewModel.onAssistantPlaybackRequestConsumed()
        audioReplyPlayer.play(
            url = audioUrl,
            onStarted = storyVenueViewModel::onAssistantPlaybackStarted,
            onCompleted = storyVenueViewModel::onAssistantPlaybackCompleted,
            onError = storyVenueViewModel::onAssistantPlaybackFailed,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.discard()
            audioReplyPlayer.release()
        }
    }

    val requestPermissionAndRecord: (PendingRecordingAction) -> Unit = { action ->
        pendingRecordingAction = action
        storyVenueViewModel.onPermissionRequestStarted()
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val stopRecording: () -> Unit = {
        recorder.stop().fold(
            onSuccess = { recordedFile ->
                storyVenueViewModel.onRecordingStopped(recordedFile.absolutePath)
            },
            onFailure = {
                storyVenueViewModel.onRecordingStopFailed(
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
        onRepeatLastQuestion = {
            audioReplyPlayer.stop()
            storyVenueViewModel.onRepeatLastQuestionRequested()
        },
        onRetrySpeech = {
            audioReplyPlayer.stop()
            storyVenueViewModel.onRetrySpeechReady()
            if (uiState.hasRecordAudioPermission) {
                startRecording(true)
            } else {
                requestPermissionAndRecord(PendingRecordingAction.Retry)
            }
        },
        onEndSession = {
            recorder.discard()
            audioReplyPlayer.stop()
            storyVenueViewModel.onEndSession()
        },
    )
}

@Composable
private fun VoiceInterviewScreen(
    uiState: StoryVenueUiState,
    onMicrophoneClick: () -> Unit,
    onRepeatLastQuestion: () -> Unit,
    onRetrySpeech: () -> Unit,
    onEndSession: () -> Unit,
) {
    val selectedSession = uiState.sessions.firstOrNull { it.id == uiState.selectedSessionId }

    ScreenContainer {
        HeadingText(text = "음성 인터뷰")
        if (selectedSession == null) {
            StatusCard(
                title = "세션 필요",
                content = "홈 화면에서 먼저 인터뷰 세션을 만들고 선택해 주세요.",
            )
            return@ScreenContainer
        }

        BodyText(text = "현재 세션: ${selectedSession.title}")
        Spacer(modifier = Modifier.height(12.dp))
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
            title = "오디오 응답 경로",
            content = uiState.lastAssistantAudioUrl ?: "아직 준비된 오디오 응답이 없습니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "임시 저장 경로",
            content = uiState.currentRecordingPath
                ?: uiState.lastSavedRecordingPath
                ?: "cache/voice-recordings/voice_turn_{timestamp}.m4a",
        )
        if (uiState.messages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = "최근 대화",
                content = uiState.messages.joinToString("\n\n") { message ->
                    val speaker = if (message.role == "assistant") "assistant" else "사용자"
                    "$speaker: ${message.content}"
                },
            )
        }
        if (uiState.consecutiveVoiceFailures >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = "복구 안내",
                content = "제가 잘 못 들었을 수 있습니다. 한 문장씩 천천히 말씀해 주세요. 화면의 텍스트 결과도 함께 확인해 주세요.",
            )
        }
        if (uiState.phase == VoiceInterviewPhase.Transcribing ||
            uiState.phase == VoiceInterviewPhase.Responding
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = uiState.helperText)
        }
        if (uiState.voiceErrorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = "실패 상태",
                content = uiState.voiceErrorMessage,
            )
        }
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
            label = "세션 화면 닫기",
            onClick = onEndSession,
        )
    }
}

@Composable
private fun DraftScreen(
    uiState: StoryVenueUiState,
    onChapterTypeChanged: (String) -> Unit,
    onGenerateChapter: () -> Unit,
    onSelectChapter: (String) -> Unit,
    onMoveChapterUp: (Int) -> Unit,
    onMoveChapterDown: (Int) -> Unit,
    onChapterInstructionChanged: (String) -> Unit,
    onReviseChapter: () -> Unit,
    onRegenerateChapter: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val selectedSession = uiState.sessions.firstOrNull { it.id == uiState.selectedSessionId }
    val selectedChapter = uiState.chapters.firstOrNull { it.id == uiState.selectedChapterId }

    ScreenContainer {
        HeadingText(text = "장 초안")
        if (selectedSession == null) {
            StatusCard(
                title = "세션 필요",
                content = "홈 화면에서 먼저 인터뷰 세션을 선택해 주세요.",
            )
            return@ScreenContainer
        }

        BodyText(text = "현재 세션: ${selectedSession.title}")
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "장 생성",
            content = "현재 세션에서 자동 추출된 기억 항목을 바탕으로 장 초안을 만듭니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.chapterTypeInput,
            onValueChange = onChapterTypeChanged,
            label = { Text("장 주제") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "장 초안 생성",
            onClick = onGenerateChapter,
            enabled = !uiState.isDraftLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.isDraftLoading) {
            LoadingPlaceholder(message = "장 초안을 처리하는 중입니다.")
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (uiState.chapterStatusMessage != null) {
            StatusCard(
                title = "안내",
                content = uiState.chapterStatusMessage,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (uiState.chapters.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 장 초안이 없습니다. 먼저 음성 인터뷰를 진행한 뒤 생성해 주세요.",
            )
        } else {
            uiState.chapters.forEachIndexed { index, chapter ->
                ChapterListCard(
                    chapter = chapter,
                    isSelected = chapter.id == uiState.selectedChapterId,
                    onSelect = { onSelectChapter(chapter.id) },
                    onMoveUp = { onMoveChapterUp(index) },
                    onMoveDown = { onMoveChapterDown(index) },
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.chapters.lastIndex,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (selectedChapter != null) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = selectedChapter.title,
                content = selectedChapter.content,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = "저장 상태",
                content = "버전 ${selectedChapter.versionNo}로 저장되었습니다.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.chapterInstructionInput,
                onValueChange = onChapterInstructionChanged,
                label = { Text("수정 요청") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedActionButton(
                    label = "수정 요청",
                    onClick = onReviseChapter,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isDraftLoading,
                )
                OutlinedActionButton(
                    label = "다시 생성",
                    onClick = onRegenerateChapter,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isDraftLoading,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "책 미리보기 열기",
            onClick = onOpenPreview,
            enabled = uiState.chapters.isNotEmpty(),
        )
    }
}

@Composable
private fun BookPreviewScreen(
    uiState: StoryVenueUiState,
    onBookTitleChanged: (String) -> Unit,
    onCompileBook: () -> Unit,
    onSelectBook: (String) -> Unit,
    onMoveChapterUp: (Int) -> Unit,
    onMoveChapterDown: (Int) -> Unit,
    onReturnHome: () -> Unit,
) {
    ScreenContainer {
        HeadingText(text = "책 미리보기")
        if (uiState.chapters.isEmpty()) {
            StatusCard(
                title = "초안 필요",
                content = "먼저 장 초안을 하나 이상 만들어 주세요.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(label = "홈으로 돌아가기", onClick = onReturnHome)
            return@ScreenContainer
        }

        BodyText(text = "장 순서를 확인한 뒤 최종 자서전 버전으로 저장합니다.")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.bookTitleInput,
            onValueChange = onBookTitleChanged,
            label = { Text("책 제목") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.isBookLoading) {
            LoadingPlaceholder(message = "책 미리보기를 저장하는 중입니다.")
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (uiState.bookStatusMessage != null) {
            StatusCard(
                title = "안내",
                content = uiState.bookStatusMessage,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        HeadingText(text = "장 순서")
        uiState.chapters.forEachIndexed { index, chapter ->
            ChapterListCard(
                chapter = chapter,
                isSelected = false,
                onSelect = {},
                onMoveUp = { onMoveChapterUp(index) },
                onMoveDown = { onMoveChapterDown(index) },
                canMoveUp = index > 0,
                canMoveDown = index < uiState.chapters.lastIndex,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        PrimaryActionButton(
            label = "최종 자서전 저장",
            onClick = onCompileBook,
            enabled = !uiState.isBookLoading,
        )
        Spacer(modifier = Modifier.height(20.dp))
        HeadingText(text = "저장된 버전")
        if (uiState.books.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 저장된 최종 자서전 버전이 없습니다.",
            )
        } else {
            uiState.books.forEach { book ->
                BookVersionCard(
                    book = book,
                    isSelected = book.id == uiState.selectedBookId,
                    onSelect = { onSelectBook(book.id) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (uiState.compiledBook != null) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = uiState.compiledBook.title,
                content = uiState.compiledBook.content,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(label = "홈으로 돌아가기", onClick = onReturnHome)
    }
}

@Composable
private fun SessionSummaryCard(
    session: InterviewSessionSummary,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = session.theme ?: "주제 없음")
            Text(text = "상태: ${session.status}")
            OutlinedActionButton(
                label = if (isSelected) "선택됨" else "이 세션 선택",
                onClick = onSelect,
                enabled = !isSelected,
            )
        }
    }
}

@Composable
private fun ChapterListCard(
    chapter: ChapterDraft,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "버전 ${chapter.versionNo}")
            Text(
                text = chapter.content.take(120) + if (chapter.content.length > 120) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedActionButton(
                    label = if (isSelected) "선택됨" else "보기",
                    onClick = onSelect,
                    modifier = Modifier.weight(1f),
                    enabled = !isSelected,
                )
                OutlinedActionButton(
                    label = "위로",
                    onClick = onMoveUp,
                    modifier = Modifier.weight(1f),
                    enabled = canMoveUp,
                )
                OutlinedActionButton(
                    label = "아래로",
                    onClick = onMoveDown,
                    modifier = Modifier.weight(1f),
                    enabled = canMoveDown,
                )
            }
        }
    }
}

@Composable
private fun BookVersionCard(
    book: BookVersion,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = book.content.take(140) + if (book.content.length > 140) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedActionButton(
                label = if (isSelected) "선택됨" else "미리보기 열기",
                onClick = onSelect,
                enabled = !isSelected,
            )
        }
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

@Composable
private fun StatusCard(
    title: String,
    content: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        enabled = enabled,
    ) {
        Text(text = label, fontSize = 18.sp)
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
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
    ) {
        Text(text = label, fontSize = 17.sp)
    }
}

@Composable
private fun LoadingPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
        }
    }
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
