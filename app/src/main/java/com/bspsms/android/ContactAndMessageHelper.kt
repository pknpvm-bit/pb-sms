package com.bspsms.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ContactAndMessageHelper {
    const val ADMIN_PHONE_LOCAL = AuthRepository.ADMIN_ID // "9679843774"
    const val ADMIN_WHATSAPP_INTL = "919679843774"

    fun openAdminWhatsApp(context: Context, prefillMessage: String) {
        openWhatsAppWithFallback(
            context = context,
            phone10OrIntl = ADMIN_WHATSAPP_INTL,
            fallbackLocalPhone = ADMIN_PHONE_LOCAL,
            message = prefillMessage
        )
    }

    fun openUserWhatsApp(context: Context, rawPhone: String, prefillMessage: String) {
        val digits = rawPhone.filter { it.isDigit() }
        val local10 = if (digits.length >= 10) digits.takeLast(10) else digits
        val intl = when {
            local10.length == 10 -> "91$local10"
            digits.isNotBlank() -> digits
            else -> ADMIN_WHATSAPP_INTL
        }
        openWhatsAppWithFallback(
            context = context,
            phone10OrIntl = intl,
            fallbackLocalPhone = local10.ifBlank { ADMIN_PHONE_LOCAL },
            message = prefillMessage
        )
    }

    fun shareBroadcastMessage(context: Context, message: String, targetWhatsAppOnly: Boolean = false) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "Enter a message to send.", Toast.LENGTH_SHORT).show()
            return
        }
        val encoded = Uri.encode(trimmed)
        if (targetWhatsAppOnly) {
            val waSend = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching { context.startActivity(waSend); true }.getOrDefault(false)
            if (started) return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, trimmed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Send Message via"))
        }.onFailure {
            Toast.makeText(context, "No messaging app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppWithFallback(
        context: Context,
        phone10OrIntl: String,
        fallbackLocalPhone: String,
        message: String
    ) {
        val encoded = Uri.encode(message.trim())
        val waUri = Uri.parse("https://wa.me/$phone10OrIntl?text=$encoded")
        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedWa = runCatching {
            context.startActivity(waIntent)
            true
        }.getOrDefault(false)
        if (launchedWa) return

        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$fallbackLocalPhone")).apply {
            putExtra("sms_body", message.trim())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedSms = runCatching {
            context.startActivity(smsIntent)
            true
        }.getOrDefault(false)
        if (launchedSms) return

        shareBroadcastMessage(context, message.trim(), targetWhatsAppOnly = false)
    }
}
