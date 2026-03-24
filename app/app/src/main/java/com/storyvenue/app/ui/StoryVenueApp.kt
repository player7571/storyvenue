package com.storyvenue.app.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

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
                LoginPlaceholder(
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
                VoiceInterviewPlaceholder(
                    onBackHome = { navController.navigate(StoryVenueScreen.Home.route) },
                    onOpenDraft = { navController.navigate(StoryVenueScreen.Draft.route) },
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
private fun LoginPlaceholder(onContinue: () -> Unit) {
    ScreenContainer {
        HeadingText(text = "편하게 시작해 보세요")
        BodyText(text = "로그인 화면 placeholder 입니다. 다음 단계에서 이메일 로그인과 세션 유지 흐름을 연결합니다.")
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryActionButton(
            label = "홈으로 이동",
            onClick = onContinue,
        )
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
private fun VoiceInterviewPlaceholder(
    onBackHome: () -> Unit,
    onOpenDraft: () -> Unit,
) {
    ScreenContainer {
        HeadingText(text = "음성 인터뷰 준비 화면")
        StatusCard(
            title = "현재 상태",
            content = "듣기 전 대기 중",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "마지막 질문",
            content = "어릴 때 가장 먼저 떠오르는 장소를 말씀해 주세요.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            title = "마지막 인식 결과",
            content = "아직 인식된 내용이 없습니다.",
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(label = "큰 마이크 버튼 placeholder", onClick = {})
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "다시 듣기",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            OutlinedActionButton(
                label = "다시 말하기",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedActionButton(
                label = "세션 종료",
                onClick = onBackHome,
                modifier = Modifier.weight(1f),
            )
            PrimaryActionButton(
                label = "초안 화면 열기",
                onClick = onOpenDraft,
                modifier = Modifier.weight(1f),
            )
        }
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
private fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
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
) {
    OutlinedButton(
        onClick = onClick,
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
