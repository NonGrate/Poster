package com.example.poster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.apple_logo_black
import poster.composeapp.generated.resources.apple_logo_white
import poster.composeapp.generated.resources.login_with_apple

/**
 * Sign in with Apple, following Apple's Human Interface Guidelines for a custom
 * button.
 *
 * Two of Apple's three approved styles, chosen by the app's theme so the button
 * always contrasts with the background: a **black button** with the white logo
 * and white title on a light background; a **white button** with the black logo
 * and black title on a dark one. The rules the guidelines are strict about are
 * kept: Apple's own logo, unaltered (clear space included, never trimmed) and in
 * the matching tone; an approved title ("Sign in with Apple"). The corner radius
 * follows the app's other buttons, and — using no explicit height — so does the
 * height, so this button lines up with the Google/email buttons beside it.
 */
@Composable
fun SignInWithAppleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Logo tone follows the button, not the page: white logo on the black
    // button, black logo on the white one.
    val container = if (onDark) Color.White else Color.Black
    val content = if (onDark) Color.Black else Color.White
    val logo = if (onDark) Res.drawable.apple_logo_black else Res.drawable.apple_logo_white

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Apple's asset is unaltered — its transparent clear space is part of
            // the logo and must not be trimmed. It takes the same 18dp layout
            // slot as the Google mark, so this button ends up the same height as
            // the others; but it *draws* unbounded at 42dp so the visible glyph
            // matches the Google mark's size. The extra size is only the asset's
            // transparent clear space, which overflows invisibly instead of
            // growing the button or the gap to the title.
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .wrapContentSize(unbounded = true)
                    .size(42.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(Res.string.login_with_apple))
        }
    }
}
