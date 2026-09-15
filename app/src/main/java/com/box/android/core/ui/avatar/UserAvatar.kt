package com.box.android.core.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AvatarUtils {
    fun getGravatarUrl(email: String): String? {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return null
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(clean.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "https://www.gravatar.com/avatar/$hash?d=404&s=200"
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String?,
    username: String?,
    email: String? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFE55B25),
    textColor: Color = Color.White,
    fontSize: TextUnit = 16.sp
) {
    val effectiveUrl = remember(avatarUrl, email) {
        when {
            !avatarUrl.isNullOrBlank() && avatarUrl != "null" -> avatarUrl
            !email.isNullOrBlank() -> AvatarUtils.getGravatarUrl(email)
            else -> null
        }
    }

    var bitmap by remember(effectiveUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(effectiveUrl) {
        if (!effectiveUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val url = URL(effectiveUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doInput = true
                    conn.instanceFollowRedirects = true
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val stream = conn.inputStream
                        val decoded = BitmapFactory.decodeStream(stream)
                        if (decoded != null) {
                            bitmap = decoded.asImageBitmap()
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    bitmap = null
                }
            }
        } else {
            bitmap = null
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = username ?: "Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = username?.trim()?.take(1)?.uppercase() ?: "U"
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                ),
                color = textColor
            )
        }
    }
}
