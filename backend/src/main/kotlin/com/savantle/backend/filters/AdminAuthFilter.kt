package com.savantle.backend.filters

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class AdminAuthFilter(
    @Value("\${savantle.admin.api-key}") private val apiKey: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.contains("/admin/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val provided = request.getHeader("X-Admin-Key")
        if (provided.isNullOrBlank() || !constantTimeEquals(provided, apiKey)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("{\"error\":\"Unauthorized\"}")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
