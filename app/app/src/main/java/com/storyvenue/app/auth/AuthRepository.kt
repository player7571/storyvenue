package com.storyvenue.app.auth

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<Unit>
}
