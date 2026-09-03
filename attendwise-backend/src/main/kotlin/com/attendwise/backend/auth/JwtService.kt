package com.attendwise.backend.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secretKey: String,
    @Value("\${jwt.expiration:86400000}") private val jwtExpiration: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }

    fun generateToken(userId: String): String {
        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(key)
            .compact()
    }

    fun extractUserId(token: String): String? {
        return extractClaim(token, Claims::getSubject)
    }

    fun isTokenValid(token: String, userId: String): Boolean {
        val extractedUserId = extractUserId(token)
        return extractedUserId == userId && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractExpiration(token).before(Date())
    }

    private fun extractExpiration(token: String): Date {
        return extractClaim(token, Claims::getExpiration)
    }

    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return claimsResolver(claims)
    }
}
