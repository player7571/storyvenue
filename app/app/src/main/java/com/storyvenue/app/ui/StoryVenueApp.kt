package com.storyvenue.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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

private val NavyDark = Color(0xFF03192E)
private val BeigeLight = Color(0xFFFFFCF6)
private val BeigeMuted = Color(0xFFF4EFE3)
private val GoldAccent = Color(0xFF6A5630)
private val GoldSoft = Color(0xFFD9CCAC)
private val TextGray = Color(0xFF333333)
private val ErrorRose = Color(0xFF8A2D2D)
private val NewsreaderFamily = FontFamily.SansSerif

private enum class PendingRecordingAction {
    Start,
    Retry,
}

private enum class StoryVenueScreen(
    val route: String,
    val title: String,
    val icon: ImageVector?,
) {
    Login("login", "로그인", null),
    Home("home", "홈", Icons.Filled.Home),
    VoiceInterview("voice_interview", "음성 인터뷰", Icons.Filled.Mic),
    Draft("draft", "장 초안", Icons.Filled.AutoStories),
    BookPreview("book_preview", "책 미리보기", Icons.Filled.MenuBook),
    Feed("feed", "공감 피드", Icons.Filled.Public),
    FeedPost("feed_post", "자서전", null),
    Chat("chat", "채팅", Icons.Filled.ChatBubble),
}

@Composable
private fun StoryVenueTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = NavyDark,
        onPrimary = Color.White,
        secondary = GoldAccent,
        onSecondary = Color.White,
        background = BeigeLight,
        onBackground = NavyDark,
        surface = BeigeLight,
        onSurface = NavyDark,
        surfaceVariant = BeigeMuted,
        onSurfaceVariant = TextGray,
        outline = GoldSoft,
        error = ErrorRose,
        onError = Color.White,
    )
    val typography = Typography(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = NewsreaderFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 42.sp,
        ),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = NewsreaderFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
        ),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = NewsreaderFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = NewsreaderFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleMedium = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 19.sp,
            lineHeight = 30.sp,
        ),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 17.sp,
            lineHeight = 28.sp,
        ),
        labelLarge = MaterialTheme.typography.labelLarge.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = MaterialTheme.typography.labelMedium.copy(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelSmall = MaterialTheme.typography.labelSmall.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

@Composable
fun StoryVenueApp() {
    val navController = rememberNavController()
    val storyVenueViewModel: StoryVenueViewModel = viewModel()

    StoryVenueTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    val bottomNavigationScreen = navSelectionForScreen(currentScreen)
    val showBackButton = currentScreen == StoryVenueScreen.VoiceInterview ||
        currentScreen == StoryVenueScreen.BookPreview ||
        currentScreen == StoryVenueScreen.FeedPost ||
        currentScreen == StoryVenueScreen.Chat
    val showBottomBar = uiState.authSession != null && when (currentScreen) {
        StoryVenueScreen.Home,
        StoryVenueScreen.VoiceInterview,
        StoryVenueScreen.Draft,
        StoryVenueScreen.Feed,
        -> true
        else -> false
    }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentScreen != StoryVenueScreen.Login) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    navigationIcon = {
                        if (showBackButton) {
                            OutlinedIconButton(
                                onClick = { navController.popBackStack() },
                                icon = Icons.Filled.ArrowBack,
                                contentDescription = "뒤로 가기",
                            )
                        }
                    },
                    title = {
                        Text(
                            text = currentScreen.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = NavyDark,
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    listOf(
                        StoryVenueScreen.Home,
                        StoryVenueScreen.VoiceInterview,
                        StoryVenueScreen.Draft,
                        StoryVenueScreen.Feed,
                    ).forEach { screen ->
                        NavigationBarItem(
                            selected = bottomNavigationScreen == screen,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(StoryVenueScreen.Home.route) { saveState = true }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                IconOrNull(
                                    icon = screen.icon,
                                    contentDescription = screen.title,
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NavyDark,
                                selectedTextColor = NavyDark,
                                unselectedIconColor = GoldAccent.copy(alpha = 0.7f),
                                unselectedTextColor = GoldAccent.copy(alpha = 0.7f),
                                indicatorColor = BeigeMuted,
                            ),
                        )
                    }
                }
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
        HeroCard(
            eyebrow = "로그인",
            title = "편하게 시작해 보세요",
            body = "이메일과 비밀번호를 입력하면 바로 이야기를 시작할 수 있습니다.",
        )
        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "접속 정보")
        StoryTextField(
            value = uiState.serverBaseUrl,
            onValueChange = onServerBaseUrlChanged,
            label = "서버 주소",
            enabled = !uiState.isAuthLoading,
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "로그인",
            onClick = { onAuthModeChanged(AuthMode.SignIn) },
            enabled = uiState.authMode != AuthMode.SignIn,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "회원가입",
            onClick = { onAuthModeChanged(AuthMode.SignUp) },
            enabled = uiState.authMode != AuthMode.SignUp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StoryTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = "이메일",
            enabled = !uiState.isAuthLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(modifier = Modifier.height(12.dp))
        StoryTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = "비밀번호",
            enabled = !uiState.isAuthLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.isRestoringSession || uiState.isAuthLoading) {
            LoadingPlaceholder(
                message = if (uiState.isRestoringSession) {
                    "저장된 로그인 정보를 확인하는 중입니다."
                } else if (uiState.authMode == AuthMode.SignIn) {
                    "로그인 중입니다."
                } else {
                    "가입 중입니다."
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        uiState.authMessage?.let { message ->
            NoticeCard(
                title = "안내",
                content = message,
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
        HeroCard(
            eyebrow = "홈",
            title = selectedSession?.title ?: "오늘의 인터뷰를 준비해 보세요",
            body = selectedSession?.theme?.let { "선택한 주제는 $it 입니다." }
                ?: "세션 제목을 적고 큰 버튼을 눌러 시작하면 됩니다.",
            actionLabel = if (selectedSession != null) "음성 인터뷰 시작" else null,
            onAction = if (selectedSession != null) onOpenVoiceInterview else null,
        )
        Spacer(modifier = Modifier.height(20.dp))
        NoticeCard(
            title = "로그인 상태",
            content = uiState.authSession?.email?.let { "$it 로 로그인되어 있습니다." }
                ?: "로그인 정보가 없습니다.",
        )
        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(title = "새 인터뷰 세션 만들기")
        NoticeCard(
            title = "간단히 입력해 주세요",
            content = "세션 제목과 주제를 짧게 적으면 됩니다. 예: 어린 시절, 가족, 일과 삶",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StoryTextField(
            value = uiState.newSessionTitle,
            onValueChange = onNewSessionTitleChanged,
            label = "세션 제목",
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StoryTextField(
            value = uiState.newSessionTheme,
            onValueChange = onNewSessionThemeChanged,
            label = "주제",
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "새 세션 만들기",
            onClick = onCreateSession,
            enabled = !uiState.isHomeLoading,
        )
        if (uiState.isHomeLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = "세션 정보를 불러오는 중입니다.")
        }
        uiState.homeMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "안내", content = message)
        }
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "내 세션 목록")
        if (uiState.sessions.isEmpty()) {
            NoticeCard(
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
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "다음 작업")
        PrimaryActionButton(
            label = "음성 인터뷰 시작",
            onClick = onOpenVoiceInterview,
            enabled = uiState.selectedSessionId != null,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
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
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "테스트용 설정")
        NoticeCard(
            title = "서버 주소",
            content = "테스트 중일 때만 바꾸면 됩니다.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StoryTextField(
            value = uiState.serverBaseUrl,
            onValueChange = onServerBaseUrlChanged,
            label = "서버 주소",
            singleLine = true,
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
    val isListening = uiState.phase == VoiceInterviewPhase.Listening
    val isBusy = uiState.phase == VoiceInterviewPhase.Transcribing ||
        uiState.phase == VoiceInterviewPhase.Responding

    ScreenContainer {
        if (selectedSession == null) {
            NoticeCard(
                title = "세션 필요",
                content = "홈 화면에서 먼저 인터뷰 세션을 만들고 선택해 주세요.",
            )
            return@ScreenContainer
        }

        HeroCard(
            eyebrow = "음성 인터뷰",
            title = "AI 질문",
            body = uiState.lastAssistantQuestion,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MetaPill(
            label = "현재 상태",
            value = voicePhaseLabel(uiState.phase),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        NoticeCard(
            title = "현재 세션",
            content = selectedSession.title,
        )
        Spacer(modifier = Modifier.height(20.dp))
        VoiceRecorderCard(
            phase = uiState.phase,
            helperText = uiState.helperText,
            onMicrophoneClick = onMicrophoneClick,
            enabled = !isBusy,
            isListening = isListening,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedActionButton(
            label = "AI 질문 다시 듣기",
            onClick = onRepeatLastQuestion,
            enabled = !isListening && !isBusy,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "내 답변 다시 말하기",
            onClick = onRetrySpeech,
            enabled = !isBusy,
        )
        if (isBusy) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = uiState.helperText)
        }
        uiState.voiceErrorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "실패 상태", content = message)
        }
        if (uiState.isPermissionDenied) {
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(
                title = "권한 필요",
                content = "마이크 권한이 거부되어 녹음을 시작할 수 없습니다. 버튼을 다시 눌러 권한을 다시 요청해 주세요.",
            )
        }
        if (uiState.consecutiveVoiceFailures >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(
                title = "복구 안내",
                content = "제가 잘 못 들었을 수 있습니다. 한 문장씩 천천히 말씀해 주세요. 화면의 텍스트 결과도 함께 확인해 주세요.",
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(title = "마지막 인식 결과")
        NoticeCard(
            title = "텍스트 보기",
            content = uiState.transcriptPlaceholder,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "최근 대화")
        if (uiState.messages.isEmpty()) {
            NoticeCard(
                title = "대화 없음",
                content = "아직 저장된 대화가 없습니다. 큰 마이크 버튼을 눌러 말씀해 주세요.",
            )
        } else {
            uiState.messages.takeLast(6).forEach { message ->
                ConversationCard(message = message)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedActionButton(
            label = "세션 화면 닫기",
            onClick = onEndSession,
            modifier = Modifier.fillMaxWidth(),
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
        if (selectedSession == null) {
            NoticeCard(
                title = "세션 필요",
                content = "홈 화면에서 먼저 인터뷰 세션을 선택해 주세요.",
            )
            return@ScreenContainer
        }

        HeroCard(
            eyebrow = "장 초안",
            title = selectedSession.title,
            body = "음성 인터뷰 내용을 바탕으로 장 초안을 만듭니다.",
        )
        Spacer(modifier = Modifier.height(16.dp))
        StoryTextField(
            value = uiState.chapterTypeInput,
            onValueChange = onChapterTypeChanged,
            label = "장 주제",
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "장 초안 생성",
            onClick = onGenerateChapter,
            enabled = !uiState.isDraftLoading,
        )
        if (uiState.isDraftLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = "장 초안을 처리하는 중입니다.")
        }
        uiState.chapterStatusMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "안내", content = message)
        }
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "생성된 장")
        if (uiState.chapters.isEmpty()) {
            NoticeCard(
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
        selectedChapter?.let { chapter ->
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "선택한 장")
            NoticeCard(
                title = chapter.title,
                content = chapter.content,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MetaPill(
                label = "저장 상태",
                value = "버전 ${chapter.versionNo}",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            StoryTextField(
                value = uiState.chapterInstructionInput,
                onValueChange = onChapterInstructionChanged,
                label = "수정 요청",
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedActionButton(
                label = "수정 요청",
                onClick = onReviseChapter,
                enabled = !uiState.isDraftLoading,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedActionButton(
                label = "다시 생성",
                onClick = onRegenerateChapter,
                enabled = !uiState.isDraftLoading,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
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
        if (uiState.chapters.isEmpty()) {
            NoticeCard(
                title = "초안 필요",
                content = "먼저 장 초안을 하나 이상 만들어 주세요.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(label = "홈으로 돌아가기", onClick = onReturnHome)
            return@ScreenContainer
        }

        HeroCard(
            eyebrow = "책 미리보기",
            title = uiState.bookTitleInput,
            body = "장 순서를 확인한 뒤 최종 자서전으로 저장합니다.",
        )
        Spacer(modifier = Modifier.height(16.dp))
        StoryTextField(
            value = uiState.bookTitleInput,
            onValueChange = onBookTitleChanged,
            label = "책 제목",
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "최종 자서전 저장",
            onClick = onCompileBook,
            enabled = !uiState.isBookLoading,
        )
        if (uiState.isBookLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = "책 미리보기를 저장하는 중입니다.")
        }
        uiState.bookStatusMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "안내", content = message)
        }
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "장 순서")
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
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "저장된 버전")
        if (uiState.books.isEmpty()) {
            NoticeCard(
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
        uiState.compiledBook?.let { book ->
            Spacer(modifier = Modifier.height(24.dp))
            NoticeCard(
                title = book.title,
                content = book.content,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedActionButton(
                label = "피드에 올리기",
                onClick = onPublishToFeed,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedActionButton(
                label = "피드 보기",
                onClick = onOpenFeed,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedActionButton(
            label = "홈으로 돌아가기",
            onClick = onReturnHome,
            modifier = Modifier.fillMaxWidth(),
        )
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
        HeroCard(
            eyebrow = "공감 피드",
            title = "비슷한 경험을 가진 사람들을 만나 보세요",
            body = "글을 읽고 댓글을 남기거나 채팅을 시작할 수 있습니다.",
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (selectedBook != null) {
            NoticeCard(
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
        StoryTextField(
            value = uiState.feedQueryInput,
            onValueChange = onFeedQueryChanged,
            label = "찾고 싶은 주제나 경험",
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "추천 다시 불러오기",
            onClick = onRefreshFeed,
            enabled = !uiState.isFeedLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "채팅 보기",
            onClick = onOpenChat,
        )
        if (uiState.isFeedLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = "추천 피드를 불러오는 중입니다.")
        }
        uiState.feedStatusMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "안내", content = message)
        }
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "비슷한 사람")
        if (uiState.recommendedPeople.isEmpty()) {
            NoticeCard(
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
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "추천 자서전")
        if (uiState.feedPosts.isEmpty()) {
            NoticeCard(
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
        if (uiState.isFeedLoading) {
            LoadingPlaceholder(message = "피드 글을 불러오는 중입니다.")
        } else {
            NoticeCard(
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
        HeroCard(
            eyebrow = "자서전",
            title = post.title,
            body = "${post.authorName} 님의 이야기",
        )
        Spacer(modifier = Modifier.height(16.dp))
        post.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            NoticeCard(title = "AI 요약", content = summary)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (post.topics.isNotEmpty() || post.experiences.isNotEmpty()) {
            NoticeCard(
                title = "관련 키워드",
                content = buildTagSummary(post),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        NoticeCard(title = "본문", content = post.content)
        uiState.feedStatusMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "안내", content = message)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "끝까지 읽었어요",
            onClick = onMarkCompleted,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = if (canStartChat) "이 작성자와 채팅" else "내 글입니다",
            onClick = onStartChat,
            enabled = canStartChat,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "댓글")
        if (uiState.feedComments.isEmpty()) {
            NoticeCard(
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
        StoryTextField(
            value = uiState.feedCommentInput,
            onValueChange = onFeedCommentChanged,
            label = "댓글 남기기",
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryActionButton(
            label = "댓글 등록",
            onClick = onSubmitComment,
            enabled = !uiState.isFeedLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "피드로 돌아가기",
            onClick = onBackToFeed,
            modifier = Modifier.fillMaxWidth(),
        )
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
        HeroCard(
            eyebrow = "채팅",
            title = selectedRoom?.otherUserName ?: "대화를 시작해 보세요",
            body = "아래에서 대화방을 고르고 큰 버튼으로 메시지를 보낼 수 있습니다.",
        )
        if (uiState.isChatLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingPlaceholder(message = "채팅 정보를 불러오는 중입니다.")
        }
        uiState.chatStatusMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            NoticeCard(title = "안내", content = message)
        }
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "대화방")
        if (uiState.chatRooms.isEmpty()) {
            NoticeCard(
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
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = selectedRoom?.otherUserName ?: "대화 내용")
        if (selectedRoom == null) {
            NoticeCard(
                title = "선택 필요",
                content = "먼저 대화방을 하나 선택해 주세요.",
            )
        } else {
            if (uiState.chatMessages.isEmpty()) {
                NoticeCard(
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
            StoryTextField(
                value = uiState.chatMessageInput,
                onValueChange = onChatMessageChanged,
                label = "메시지",
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                label = "메시지 보내기",
                onClick = onSendMessage,
                enabled = !uiState.isChatLoading,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedActionButton(
            label = "피드로 돌아가기",
            onClick = onBackToFeed,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        content()
    }
}

@Composable
private fun HeroCard(
    eyebrow: String,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, GoldSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.6.sp,
                color = GoldAccent,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NavyDark,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = TextGray,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDark,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRecorderCard(
    phase: VoiceInterviewPhase,
    helperText: String,
    onMicrophoneClick: () -> Unit,
    enabled: Boolean,
    isListening: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BeigeMuted),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, GoldSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = voicePhaseLabel(phase),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.2.sp,
                color = GoldAccent,
            )
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(pulseScale),
                    shape = CircleShape,
                    color = NavyDark.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, NavyDark.copy(alpha = 0.12f)),
                ) {}
                Button(
                    onClick = onMicrophoneClick,
                    enabled = enabled,
                    modifier = Modifier.size(156.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDark,
                        contentColor = Color.White,
                        disabledContainerColor = NavyDark.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        IconOrNull(
                            icon = Icons.Filled.SettingsVoice,
                            contentDescription = "마이크",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = voiceMicButtonLabel(phase),
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = NavyDark,
    )
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun NoticeCard(
    title: String,
    content: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                color = TextGray,
            )
        }
    }
}

@Composable
private fun MetaPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = BeigeMuted,
        border = BorderStroke(2.dp, GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = GoldAccent,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
        }
    }
}

@Composable
private fun ThemeSuggestionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GoldSoft),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = NavyDark,
        )
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
            .heightIn(min = 68.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NavyDark,
            contentColor = Color.White,
            disabledContainerColor = NavyDark.copy(alpha = 0.45f),
        ),
    ) {
        Text(text = label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, GoldSoft),
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = NavyDark,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OutlinedIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        border = BorderStroke(2.dp, GoldSoft),
    ) {
        IconOrNull(
            icon = icon,
            contentDescription = contentDescription,
            tint = NavyDark,
        )
    }
}

@Composable
private fun StoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = GoldSoft,
            focusedLabelColor = GoldAccent,
            unfocusedLabelColor = TextGray,
            cursorColor = NavyDark,
            focusedTextColor = NavyDark,
            unfocusedTextColor = NavyDark,
            disabledTextColor = TextGray,
            focusedContainerColor = BeigeLight,
            unfocusedContainerColor = BeigeLight,
            disabledContainerColor = BeigeMuted,
        ),
    )
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
            CircularProgressIndicator(color = GoldAccent)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextGray,
                textAlign = TextAlign.Center,
            )
        }
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = session.theme ?: "주제 없음",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
            )
            Text(
                text = "상태: ${session.status}",
                style = MaterialTheme.typography.labelMedium,
                color = GoldAccent,
            )
            session.createdAt?.let { createdAt ->
                Text(
                    text = createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray,
                )
            }
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = "버전 ${chapter.versionNo}",
                style = MaterialTheme.typography.labelMedium,
                color = GoldAccent,
            )
            Text(
                text = chapter.content.take(140) + if (chapter.content.length > 140) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = book.content.take(180) + if (book.content.length > 180) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = "${post.authorName} 님",
                style = MaterialTheme.typography.labelMedium,
                color = GoldAccent,
            )
            Text(
                text = post.summary ?: post.excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
            )
            if (post.topics.isNotEmpty()) {
                Text(
                    text = "주제: ${post.topics.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextGray,
                )
            }
            post.score?.let { score ->
                Text(
                    text = "추천 점수: ${"%.1f".format(score)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldAccent,
                )
            }
            OutlinedActionButton(label = "읽기", onClick = onOpen)
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = person.authorName,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            if (person.sharedTopics.isNotEmpty()) {
                Text(
                    text = "공통 주제: ${person.sharedTopics.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                )
            }
            if (person.sharedExperiences.isNotEmpty()) {
                Text(
                    text = "공통 경험: ${person.sharedExperiences.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                )
            }
            Text(
                text = "연결 점수: ${"%.1f".format(person.score)}",
                style = MaterialTheme.typography.labelMedium,
                color = GoldAccent,
            )
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = comment.authorName,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
            )
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BeigeMuted),
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else GoldSoft),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = room.otherUserName,
                style = MaterialTheme.typography.titleMedium,
                color = NavyDark,
            )
            Text(
                text = room.lastMessagePreview ?: "아직 주고받은 메시지가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
            )
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
    val containerColor = if (isMine) NavyDark else BeigeMuted
    val contentColor = if (isMine) Color.White else NavyDark

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isMine) "나" else message.senderName,
                style = MaterialTheme.typography.labelLarge,
                color = if (isMine) GoldSoft else GoldAccent,
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ConversationCard(message: SessionMessage) {
    val isAssistant = message.role == "assistant"
    val containerColor = if (isAssistant) BeigeMuted else NavyDark
    val contentColor = if (isAssistant) NavyDark else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isAssistant) "assistant" else "사용자",
                style = MaterialTheme.typography.labelLarge,
                color = if (isAssistant) GoldAccent else GoldSoft,
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            if (message.safetyMode) {
                Text(
                    text = "안전 응답 모드",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAssistant) ErrorRose else GoldSoft,
                )
            }
        }
    }
}

@Composable
private fun IconOrNull(
    icon: ImageVector?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (icon != null) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}

private fun navSelectionForScreen(screen: StoryVenueScreen): StoryVenueScreen {
    return when (screen) {
        StoryVenueScreen.BookPreview -> StoryVenueScreen.Draft
        StoryVenueScreen.FeedPost -> StoryVenueScreen.Feed
        StoryVenueScreen.VoiceInterview -> StoryVenueScreen.Home
        else -> screen
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
        VoiceInterviewPhase.Idle -> "기록 시작"
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
