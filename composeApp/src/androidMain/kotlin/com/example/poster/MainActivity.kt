package com.example.poster

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.poster.auth.AppleWebCallback
import com.example.poster.invite.InviteLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLink(intent?.dataString)

        setContent {
            App()
        }
    }

    /** singleTask, so a link arriving while the app is open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent.dataString)
    }

    /**
     * Returning to the app without a callback means the person closed the Apple
     * sign-in browser — resolve the waiting request as a cancel. Harmless when
     * nothing is pending, and the deep link (onNewIntent) has already completed
     * the request by the time this runs, so it is not mistaken for a cancel.
     */
    override fun onResume() {
        super.onResume()
        AppleWebCallback.cancelIfPending()
    }

    /** Apple's browser callback is consumed here; everything else is an invite link. */
    private fun handleLink(data: String?) {
        val uri = data?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri != null && AppleWebCallback.offer(uri)) return
        InviteLink.offer(data)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
