package com.kaenova.m.data.api

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.security.MessageDigest

class DigestAuthenticator(
    private val username: String,
    private val password: String
) : Authenticator {

    private var lastNonce: String = ""
    private var lastRealm: String = ""
    private var lastQop: String = ""
    private var nc: Int = 0

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code == 401) {
            val authHeader = response.header("WWW-Authenticate") ?: return null
            if (authHeader.contains("Digest", ignoreCase = true)) {
                return createAuthRequest(response.request, authHeader)
            }
        }
        return null
    }

    private fun createAuthRequest(originalRequest: Request, authHeader: String): Request? {
        val params = parseAuthHeader(authHeader)
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val qop = params["qop"] ?: "auth"
        val opaque = params["opaque"] ?: ""

        lastNonce = nonce
        lastRealm = realm
        lastQop = qop
        nc++

        val method = originalRequest.method
        val uri = originalRequest.url.encodedPath

        val response = calculateDigestResponse(
            method = method,
            uri = uri,
            realm = realm,
            nonce = nonce,
            qop = qop,
            opaque = opaque
        )

        val authHeader = buildAuthorizationHeader(
            realm = realm,
            nonce = nonce,
            uri = uri,
            response = response,
            qop = qop,
            opaque = opaque
        )

        return originalRequest.newBuilder()
            .header("Authorization", authHeader)
            .build()
    }

    private fun calculateDigestResponse(
        method: String,
        uri: String,
        realm: String,
        nonce: String,
        qop: String,
        opaque: String
    ): String {
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")

        val cnonce = "test"
        val ncValue = String.format("%08d", nc)

        return if (qop == "auth") {
            val computedResponse = "$ha1:$nonce:$ncValue:$cnonce:$qop:$ha2"
            md5(computedResponse)
        } else {
            val computedResponse = "$ha1:$nonce:$ha2"
            md5(computedResponse)
        }
    }

    private fun buildAuthorizationHeader(
        realm: String,
        nonce: String,
        uri: String,
        response: String,
        qop: String,
        opaque: String
    ): String {
        val cnonce = "test"
        val ncValue = String.format("%08d", nc)

        return if (qop == "auth") {
            """Digest username="$username", realm="$realm", nonce="$nonce", uri="$uri", response="$response", qop=$qop, nc=$ncValue, cnonce="$cnonce", opaque="$opaque""""
        } else {
            """Digest username="$username", realm="$realm", nonce="$nonce", uri="$uri", response="$response", opaque="$opaque""""
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseAuthHeader(header: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val parts = header.split(Regex(",\\s*"))

        for (part in parts) {
            if (part.startsWith("Digest", ignoreCase = true)) {
                continue
            }

            val keyValue = part.split("=", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0].trim()
                var value = keyValue[1].trim()

                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length - 1)
                }

                params[key] = value
            }
        }

        return params
    }
}
