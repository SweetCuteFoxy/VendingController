package com.vending.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vending.dao.UserDAO
import com.vending.model.SessionInfo
import com.vending.model.User
import com.vending.model.Role
import org.slf4j.LoggerFactory
import java.util.Date

object AuthService {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private const val SECRET = "VendingControllerJWTSecret2026"
    private const val ISSUER = "VendingController"
    private const val TOKEN_VALIDITY_MS = 8 * 60 * 60 * 1000L // 8 hours
    private val algorithm = Algorithm.HMAC256(SECRET)

    val session = SessionInfo()

    fun login(email: String, password: String): Boolean {
        return try {
            val user = UserDAO.authenticate(email, password)
            if (user != null) {
                val role = UserDAO.getRoleById(user.roleId)
                val token = generateToken(user)
                session.currentUser = user
                session.token = token
                session.role = role
                logger.info("User ${user.email} logged in successfully")
                true
            } else {
                logger.warn("Failed login attempt for $email")
                false
            }
        } catch (e: Exception) {
            logger.error("Login error", e)
            false
        }
    }

    fun logout() {
        logger.info("User ${session.currentUser?.email} logged out")
        session.currentUser = null
        session.token = null
        session.role = null
    }

    fun isLoggedIn(): Boolean = session.token != null && session.currentUser != null

    fun getCurrentUser(): User? = session.currentUser
    fun getCurrentRole(): Role? = session.role

    fun getUserInitials(): String {
        val user = session.currentUser ?: return "?"
        val parts = user.fullName.split(" ")
        return if (parts.size >= 2) {
            "${parts[0]} ${parts[1].firstOrNull() ?: ""}."
        } else {
            user.fullName
        }
    }

    fun getUserShortName(): String {
        val user = session.currentUser ?: return "Гость"
        val parts = user.fullName.split(" ")
        return when {
            parts.size >= 3 -> "${parts[0]} ${parts[1].first()}.${parts[2].first()}."
            parts.size == 2 -> "${parts[0]} ${parts[1].first()}."
            else -> user.fullName
        }
    }

    private fun generateToken(user: User): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("role", user.roleId)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS))
            .sign(algorithm)
    }

    fun validateToken(token: String): Boolean {
        return try {
            val verifier = JWT.require(algorithm).withIssuer(ISSUER).build()
            verifier.verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
