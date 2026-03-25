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
import com.storyvenue.app.ChatMessage
import com.storyvenue.app.ChatRoom
import com.storyvenue.app.FeedComment
import com.storyvenue.app.FeedPerson
import com.storyvenue.app.FeedPost
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
    Feed("feed", "피드"),
    FeedPost("feed_post", "자서전"),
    Chat("chat", "채팅"),
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

    LaunchedEffect(uiState.pendingChatNavigationRoomId) {
        val roomId = uiState.pendingChatNavigationRoomId ?: return@LaunchedEffect
        if (currentScreen != StoryVenueScreen.Chat) {
            navController.navigate(StoryVenueScreen.Chat.route) {
                launchSingleTop = true
            }
        }
        storyVenueViewModel.onPendingChatNavigationConsumed()
        storyVenueViewModel.selectChatRoom(roomId)
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
                    onOpenFeed = {
                        navController.navigate(StoryVenueScreen.Feed.route)
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
                    onPublishToFeed = storyVenueViewModel::publishSelectedBookToFeed,
                    onOpenFeed = {
                        navController.navigate(StoryVenueScreen.Feed.route)
                    },
                    onReturnHome = {
                        navController.navigate(StoryVenueScreen.Home.route) {
                            popUpTo(StoryVenueScreen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(StoryVenueScreen.Feed.route) {
                LaunchedEffect(Unit) {
                    storyVenueViewModel.refreshFeedRecommendations()
                    storyVenueViewModel.loadChatRooms()
                }
                FeedScreen(
                    uiState = uiState,
                    onFeedQueryChanged = storyVenueViewModel::onFeedQueryChanged,
                    onRefreshFeed = storyVenueViewModel::refreshFeedRecommendations,
                    onPublishToFeed = storyVenueViewModel::publishSelectedBookToFeed,
                    onOpenPost = { postId ->
                        storyVenueViewModel.selectFeedPost(postId)
                        navController.navigate(StoryVenueScreen.FeedPost.route)
                    },
                    onOpenChat = {
                        navController.navigate(StoryVenueScreen.Chat.route)
                    },
                    onStartChatWithUser = storyVenueViewModel::startChatWithUser,
                )
            }
            composable(StoryVenueScreen.FeedPost.route) {
                FeedPostRoute(
                    uiState = uiState,
                    storyVenueViewModel = storyVenueViewModel,
                    onBackToFeed = {
                        navController.navigate(StoryVenueScreen.Feed.route) {
                            popUpTo(StoryVenueScreen.Feed.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(StoryVenueScreen.Chat.route) {
                LaunchedEffect(Unit) {
                    storyVenueViewModel.loadChatRooms()
                }
                ChatScreen(
                    uiState = uiState,
                    onSelectRoom = storyVenueViewModel::selectChatRoom,
                    onChatMessageChanged = storyVenueViewModel::onChatMessageChanged,
                    onSendMessage = storyVenueViewModel::sendChatMessage,
                    onBackToFeed = {
                        navController.navigate(StoryVenueScreen.Feed.route) {
                            popUpTo(StoryVenueScreen.Feed.route) { inclusive = false }
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
    onOpenFeed: () -> Unit,
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
            label = "공감 피드",
            onClick = onOpenFeed,
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
    onPublishToFeed: () -> Unit,
    onOpenFeed: () -> Unit,
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
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedActionButton(
                    label = "피드에 올리기",
                    onClick = onPublishToFeed,
                    modifier = Modifier.weight(1f),
                )
                OutlinedActionButton(
                    label = "피드 보기",
                    onClick = onOpenFeed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(label = "홈으로 돌아가기", onClick = onReturnHome)
    }
}

@Composable
private fun FeedScreen(
    uiState: StoryVenueUiState,
    onFeedQueryChanged: (String) -> Unit,
    onRefreshFeed: () -> Unit,
    onPublishToFeed: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenChat: () -> Unit,
    onStartChatWithUser: (String) -> Unit,
) {
    val selectedBook = uiState.compiledBook
        ?: uiState.selectedBookId?.let { selectedId -> uiState.books.firstOrNull { it.id == selectedId } }
        ?: uiState.books.firstOrNull()
    val currentUserId = uiState.authSession?.userId

    ScreenContainer {
        HeadingText(text = "공감 피드")
        BodyText(text = "비슷한 삶의 경험과 관심사를 바탕으로 자서전을 추천하고, 댓글과 채팅으로 연결할 수 있습니다.")
        Spacer(modifier = Modifier.height(12.dp))
        if (selectedBook != null) {
            StatusCard(
                title = "올릴 준비가 된 자서전",
                content = selectedBook.title,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                label = "선택한 자서전 피드에 올리기",
                onClick = onPublishToFeed,
                enabled = !uiState.isFeedLoading,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = uiState.feedQueryInput,
            onValueChange = onFeedQueryChanged,
            label = { Text("찾고 싶은 주제나 경험") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "추천 새로고침",
                onClick = onRefreshFeed,
                modifier = Modifier.weight(1f),
                enabled = !uiState.isFeedLoading,
            )
            OutlinedActionButton(
                label = "채팅 보기",
                onClick = onOpenChat,
                modifier = Modifier.weight(1f),
            )
        }
        if (uiState.isFeedLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = "추천 피드를 불러오는 중입니다.")
        }
        if (uiState.feedStatusMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(
                title = "안내",
                content = uiState.feedStatusMessage,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        HeadingText(text = "비슷한 사람")
        if (uiState.recommendedPeople.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 추천할 사람이 없습니다. 자서전을 올리거나 몇 개의 글을 읽어 보세요.",
            )
        } else {
            uiState.recommendedPeople.forEach { person ->
                RecommendedPersonCard(
                    person = person,
                    onStartChat = { onStartChatWithUser(person.userId) },
                    canChat = currentUserId != null && currentUserId != person.userId,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        HeadingText(text = "추천 자서전")
        if (uiState.feedPosts.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 피드 글이 없습니다. 먼저 자서전을 피드에 올려 보세요.",
            )
        } else {
            uiState.feedPosts.forEach { post ->
                FeedPostSummaryCard(
                    post = post,
                    onOpen = { onOpenPost(post.id) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FeedPostRoute(
    uiState: StoryVenueUiState,
    storyVenueViewModel: StoryVenueViewModel,
    onBackToFeed: () -> Unit,
) {
    val selectedPostId = uiState.selectedFeedPostId

    LaunchedEffect(selectedPostId) {
        if (selectedPostId != null && uiState.selectedFeedPost?.id != selectedPostId) {
            storyVenueViewModel.selectFeedPost(selectedPostId)
        }
    }

    val post = uiState.selectedFeedPost
    if (post != null) {
        var markedCompleted by remember(post.id) { mutableStateOf(false) }
        DisposableEffect(post.id) {
            val startedAt = System.currentTimeMillis()
            onDispose {
                val dwellSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                storyVenueViewModel.recordFeedRead(
                    postId = post.id,
                    dwellSeconds = dwellSeconds,
                    completed = markedCompleted,
                )
            }
        }

        FeedPostScreen(
            uiState = uiState,
            post = post,
            onFeedCommentChanged = storyVenueViewModel::onFeedCommentChanged,
            onSubmitComment = storyVenueViewModel::submitFeedComment,
            onMarkCompleted = { markedCompleted = true },
            onStartChat = { storyVenueViewModel.startChatWithUser(post.userId) },
            onBackToFeed = onBackToFeed,
        )
        return
    }

    ScreenContainer {
        HeadingText(text = "피드 글")
        if (uiState.isFeedLoading) {
            LoadingPlaceholder(message = "피드 글을 불러오는 중입니다.")
        } else {
            StatusCard(
                title = "선택 필요",
                content = "먼저 피드 목록에서 읽고 싶은 자서전을 선택해 주세요.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(label = "피드로 돌아가기", onClick = onBackToFeed)
        }
    }
}

@Composable
private fun FeedPostScreen(
    uiState: StoryVenueUiState,
    post: FeedPost,
    onFeedCommentChanged: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onMarkCompleted: () -> Unit,
    onStartChat: () -> Unit,
    onBackToFeed: () -> Unit,
) {
    val canStartChat = uiState.authSession?.userId != post.userId

    ScreenContainer {
        HeadingText(text = post.title)
        BodyText(text = "${post.authorName} 님의 이야기")
        Spacer(modifier = Modifier.height(12.dp))
        if (!post.summary.isNullOrBlank()) {
            StatusCard(
                title = "AI 요약",
                content = post.summary,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (post.topics.isNotEmpty() || post.experiences.isNotEmpty()) {
            StatusCard(
                title = "관련 키워드",
                content = buildTagSummary(post),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        StatusCard(
            title = "본문",
            content = post.content,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.feedStatusMessage != null) {
            StatusCard(
                title = "안내",
                content = uiState.feedStatusMessage,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "끝까지 읽었어요",
                onClick = onMarkCompleted,
                modifier = Modifier.weight(1f),
            )
            OutlinedActionButton(
                label = if (canStartChat) "이 작성자와 채팅" else "내 글입니다",
                onClick = onStartChat,
                modifier = Modifier.weight(1f),
                enabled = canStartChat,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        HeadingText(text = "댓글")
        if (uiState.feedComments.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 댓글이 없습니다. 첫 댓글을 남겨 보세요.",
            )
        } else {
            uiState.feedComments.forEach { comment ->
                FeedCommentCard(comment = comment)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.feedCommentInput,
            onValueChange = onFeedCommentChanged,
            label = { Text("댓글 남기기") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "댓글 등록",
            onClick = onSubmitComment,
            enabled = !uiState.isFeedLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(label = "피드로 돌아가기", onClick = onBackToFeed)
    }
}

@Composable
private fun ChatScreen(
    uiState: StoryVenueUiState,
    onSelectRoom: (String) -> Unit,
    onChatMessageChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onBackToFeed: () -> Unit,
) {
    val selectedRoom = uiState.chatRooms.firstOrNull { it.id == uiState.selectedChatRoomId }
    val currentUserId = uiState.authSession?.userId

    ScreenContainer {
        HeadingText(text = "채팅")
        if (uiState.isChatLoading) {
            LoadingPlaceholder(message = "채팅 정보를 불러오는 중입니다.")
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (uiState.chatStatusMessage != null) {
            StatusCard(
                title = "안내",
                content = uiState.chatStatusMessage,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        HeadingText(text = "대화방")
        if (uiState.chatRooms.isEmpty()) {
            StatusCard(
                title = "빈 상태",
                content = "아직 시작한 대화가 없습니다. 피드에서 비슷한 사람에게 먼저 채팅을 걸어 보세요.",
            )
        } else {
            uiState.chatRooms.forEach { room ->
                ChatRoomCard(
                    room = room,
                    isSelected = room.id == uiState.selectedChatRoomId,
                    onSelect = { onSelectRoom(room.id) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        HeadingText(text = selectedRoom?.otherUserName ?: "대화 내용")
        if (selectedRoom == null) {
            StatusCard(
                title = "선택 필요",
                content = "먼저 대화방을 하나 선택해 주세요.",
            )
        } else {
            if (uiState.chatMessages.isEmpty()) {
                StatusCard(
                    title = "첫 메시지",
                    content = "아직 메시지가 없습니다. 인사를 남겨 보세요.",
                )
            } else {
                uiState.chatMessages.forEach { message ->
                    ChatMessageCard(
                        message = message,
                        isMine = currentUserId == message.senderId,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.chatMessageInput,
                onValueChange = onChatMessageChanged,
                label = { Text("메시지") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                label = "메시지 보내기",
                onClick = onSendMessage,
                enabled = !uiState.isChatLoading,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(label = "피드로 돌아가기", onClick = onBackToFeed)
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
private fun FeedPostSummaryCard(
    post: FeedPost,
    onOpen: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "${post.authorName} 님")
            if (!post.summary.isNullOrBlank()) {
                Text(text = post.summary)
            } else {
                Text(text = post.excerpt)
            }
            if (post.topics.isNotEmpty()) {
                Text(text = "주제: ${post.topics.joinToString(", ")}")
            }
            if (post.score != null) {
                Text(text = "추천 점수: ${"%.1f".format(post.score)}")
            }
            OutlinedActionButton(
                label = "읽기",
                onClick = onOpen,
            )
        }
    }
}

@Composable
private fun RecommendedPersonCard(
    person: FeedPerson,
    onStartChat: () -> Unit,
    canChat: Boolean,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = person.authorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (person.sharedTopics.isNotEmpty()) {
                Text(text = "공통 주제: ${person.sharedTopics.joinToString(", ")}")
            }
            if (person.sharedExperiences.isNotEmpty()) {
                Text(text = "공통 경험: ${person.sharedExperiences.joinToString(", ")}")
            }
            Text(text = "연결 점수: ${"%.1f".format(person.score)}")
            OutlinedActionButton(
                label = if (canChat) "채팅 시작" else "내 프로필",
                onClick = onStartChat,
                enabled = canChat,
            )
        }
    }
}

@Composable
private fun FeedCommentCard(comment: FeedComment) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = comment.authorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = comment.content)
        }
    }
}

@Composable
private fun ChatRoomCard(
    room: ChatRoom,
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
                text = room.otherUserName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = room.lastMessagePreview ?: "아직 주고받은 메시지가 없습니다.")
            OutlinedActionButton(
                label = if (isSelected) "선택됨" else "대화 열기",
                onClick = onSelect,
                enabled = !isSelected,
            )
        }
    }
}

@Composable
private fun ChatMessageCard(
    message: ChatMessage,
    isMine: Boolean,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isMine) "나" else message.senderName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = message.content)
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

private fun buildTagSummary(post: FeedPost): String {
    val parts = mutableListOf<String>()
    if (post.topics.isNotEmpty()) {
        parts += "주제: ${post.topics.joinToString(", ")}"
    }
    if (post.experiences.isNotEmpty()) {
        parts += "경험: ${post.experiences.joinToString(", ")}"
    }
    if (post.emotions.isNotEmpty()) {
        parts += "감정: ${post.emotions.joinToString(", ")}"
    }
    return parts.joinToString("\n")
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
}
