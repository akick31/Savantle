package com.savantle.backend.filters

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class CorsFilter(
    @Value("\${cors.allowed.origins}") private val allowedOrigins: String
) : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        val origin = httpRequest.getHeader("Origin")

        if (origin != null && allowedOrigins.split(",").map { it.trim() }.contains(origin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin)
        }
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Admin-Key")
        httpResponse.setHeader("Access-Control-Max-Age", "3600")

        if ("OPTIONS" == httpRequest.method) {
            httpResponse.status = 200
            return
        }
        chain.doFilter(request, response)
    }
}
