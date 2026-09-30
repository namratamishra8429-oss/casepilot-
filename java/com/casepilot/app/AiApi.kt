package com.casepilot.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiApi {

    private const val BASE_URL =
        "https://casepilot-backend.vercel.app/api/chat"

    suspend fun askCasePilot(
        userMessage: String,
        caseContext: String
    ): String = withContext(Dispatchers.IO) {

        try {
            val url = URL(BASE_URL)

            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            val requestBody = JSONObject().apply {
                put("userMessage", userMessage)
                put("caseContext", caseContext)
            }.toString()

            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode

            val responseText =
                if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                } else {
                    connection.errorStream?.bufferedReader()?.use {
                        it.readText()
                    } ?: ""
                }

            connection.disconnect()

            if (responseCode !in 200..299) {
                return@withContext "AI request failed. Please try again."
            }

            val json = JSONObject(responseText)

            json.optString(
                "reply",
                "Sorry, I could not generate a response."
            )

        } catch (e: Exception) {
            "Unable to connect to CasePilot AI. Please check your internet connection."
        }
    }
}