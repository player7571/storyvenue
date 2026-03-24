package com.storyvenue.app.auth

import kotlinx.coroutines.delay

class PlaceholderAuthRepository : AuthRepository {
    override suspend fun signIn(email: String, password: String): Result<Unit> {
        delay(800)

        if (!email.contains("@")) {
            return Result.failure(IllegalArgumentException("이메일 형식을 확인해 주세요."))
        }

        if (email.contains("fail", ignoreCase = true)) {
            return Result.failure(
                IllegalStateException("실패 상태 placeholder 입니다. 실제 인증 연결 전 예시로 남겨둡니다."),
            )
        }

        // TODO: Replace this placeholder flow with real Supabase Auth sign-in.
        return Result.success(Unit)
    }
}
