package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ParsedCsvLead
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DialingAndCsvUtils {

    fun sanitizePhoneNumber(raw: String, defaultCountryCode: String = "+91"): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""
        if (hasLeadingPlus) return "+$digitsOnly".take(24)
        if (digitsOnly.length == 10 && defaultCountryCode.startsWith("+")) {
            return "${defaultCountryCode.trim()}$digitsOnly".take(24)
        }
        return digitsOnly.take(24)
    }

    fun launchPhoneCall(
        context: Context,
        phoneNumber: String,
        hasCallPermission: Boolean
    ): Boolean {
        val cleaned = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleaned.length < 3) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return false
        }
        val uri = Uri.parse("tel:$cleaned")
        val primaryIntent = Intent(
            if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            uri
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(primaryIntent)
            true
        } catch (_: SecurityException) {
            launchDialerFallback(context, uri)
        } catch (_: ActivityNotFoundException) {
            if (hasCallPermission) {
                launchDialerFallback(context, uri)
            } else {
                Toast.makeText(context, "No dialer app available on this device", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun launchDialerFallback(context: Context, uri: Uri): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No phone dialer app found", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun launchWhatsApp(context: Context, phoneNumber: String, message: String): Boolean {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 7) {
            Toast.makeText(context, "Invalid phone number for WhatsApp", Toast.LENGTH_SHORT).show()
            return false
        }
        val encoded = URLEncoder.encode(message, "UTF-8")
        val url = "https://wa.me/$digits?text=$encoded"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Unable to open WhatsApp or browser", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun launchSms(context: Context, phoneNumber: String, message: String): Boolean {
        val cleaned = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleaned.length < 3) {
            Toast.makeText(context, "Invalid phone number for SMS", Toast.LENGTH_SHORT).show()
            return false
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleaned")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No SMS app found", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun parseCsvInput(rawText: String, defaultCountryCode: String = "+91"): List<ParsedCsvLead> {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<ParsedCsvLead>()
        lines.forEachIndexed { index, line ->
            val delimiter = when {
                line.contains(',') -> ','
                line.contains('\t') -> '\t'
                line.contains(';') -> ';'
                else -> ','
            }
            val cols = splitCsvLine(line, delimiter).map { it.trim().trim('"') }
            if (cols.isEmpty()) return@forEachIndexed

            // Skip header row if first line contains common header keywords and no real phone digits
            if (index == 0) {
                val lower = line.lowercase(Locale.ROOT)
                val looksLikeHeader = (lower.contains("name") || lower.contains("phone") || lower.contains("mobile")) &&
                    cols.none { col -> col.count { it.isDigit() } >= 7 }
                if (looksLikeHeader) return@forEachIndexed
            }

            if (cols.size == 1) {
                val phone = sanitizePhoneNumber(cols[0], defaultCountryCode)
                if (phone.length >= 6) {
                    results.add(ParsedCsvLead(fullName = "Lead ${results.size + 1}", phoneNumber = phone))
                }
            } else {
                // Detect if column 0 is phone or column 1 is phone
                val col0Digits = cols[0].count { it.isDigit() }
                val col1Digits = cols.getOrNull(1)?.count { it.isDigit() } ?: 0
                val phoneFirst = col0Digits >= 7 && col1Digits < 7

                val rawPhone = if (phoneFirst) cols[0] else cols.getOrElse(1) { "" }
                val rawName = if (phoneFirst) cols.getOrElse(1) { "" } else cols[0]
                val phone = sanitizePhoneNumber(rawPhone, defaultCountryCode)
                if (phone.length >= 6) {
                    val name = rawName.ifBlank { "Lead ${results.size + 1}" }.take(80)
                    val company = cols.getOrElse(2) { "" }.take(80)
                    val email = cols.getOrElse(3) { "" }.take(120)
                    val notes = cols.getOrElse(4) { "" }.take(500)
                    results.add(
                        ParsedCsvLead(
                            fullName = name,
                            phoneNumber = phone,
                            company = company,
                            email = email,
                            notes = notes
                        )
                    )
                }
            }
        }
        return results.distinctBy { it.phoneNumber }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val mins = safe / 60L
        val secs = safe % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return ""
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun personalizeTemplate(body: String, leadName: String): String {
        val firstName = leadName.trim().ifBlank { "there" }
        return body.replace("{name}", firstName, ignoreCase = true)
    }
}
