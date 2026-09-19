package com.example.poster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.poster.model.Language
import com.example.poster.theme.Spacing

/**
 * Brown, not the default pink, for a chosen language. Pink is the app's one
 * signal for liking; ordinary selection wears the primary family.
 */
@Composable
private fun languageChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * The languages somebody reads.
 *
 * Each is written in itself — "Русский", not "Russian" — because the person who
 * needs to find it is the one who reads that language.
 *
 * Turning the last one off is refused rather than allowed and corrected later:
 * a feed that empties itself the moment you save is worse than a chip that does
 * not respond, and the server would put it back anyway.
 */
@Composable
fun LanguagesField(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Language.ALL.forEach { code ->
                val chosen = code in selected
                FilterChip(
                    selected = chosen,
                    onClick = {
                        val next = if (chosen) selected - code else selected + code
                        if (next.isNotEmpty()) onChange(Language.ALL.filter { it in next })
                    },
                    // Padded rather than pinned to a height. These carried
                    // .height(32.dp), which is the chip's own minimum, so the
                    // label sat against the edges and two of them side by side
                    // read as one crowded block. The type is untouched; only
                    // the room around it changed.
                    //
                    // The height is set on the chip rather than left to the
                    // label's padding. Material3 constrains a chip's content
                    // height itself, so padding past a point is simply clamped
                    // and nothing moves — which is what happened at 12dp. 48dp
                    // is also a proper touch target, which 32 was not.
                    label = {
                        Text(
                            text = Language.nameOf(code),
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                    },
                    colors = languageChipColors(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag(if (chosen) "language_${code}_selected" else "language_$code"),
                )
            }
        }
    }
}

/**
 * Which language a post is written in.
 *
 * Absent when somebody reads only one: the answer is already known, and a
 * control with one option asks a question that has no second answer. It appears
 * when they read more than one, defaulted to the language they write in.
 */
@Composable
fun PostLanguageField(
    selected: String,
    available: List<String>,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (available.size < 2) return
    Column(modifier = modifier.testTag("post_language_field")) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            available.forEach { code ->
                FilterChip(
                    selected = code == selected,
                    onClick = { onChange(code) },
                    label = {
                        Text(
                            text = Language.nameOf(code),
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                    },
                    colors = languageChipColors(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag(
                            if (code == selected) "post_language_${code}_selected"
                            else "post_language_$code"
                        ),
                )
            }
        }
    }
}
