package com.example.dailygoals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.ui.theme.LocalStatusColors
import com.example.dailygoals.ui.theme.accentBrush

private const val STROKE_UNITS = 8f
private const val DISABLED_ALPHA = 0.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TickMark(
    status: DayStatus,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentBrush
    val failed = LocalStatusColors.current.failed
    val idleRing = MaterialTheme.colorScheme.onSurfaceVariant
    val idleTick = MaterialTheme.colorScheme.outline
    val doneTick = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = description },
    ) {
        val logo = LogoGeometry(this, STROKE_UNITS)
        when (status) {
            DayStatus.FAILED -> {
                drawCircle(failed, radius = logo.ringRadius, center = logo.center, style = logo.stroke)
                drawPath(logo.cross, failed, style = logo.stroke)
            }
            DayStatus.DONE -> {
                drawLogoRing(logo, accent)
                drawPath(logo.tick, doneTick, style = logo.stroke)
            }
            DayStatus.PENDING -> {
                drawLogoRing(logo, SolidColor(idleRing))
                drawPath(logo.tick, idleTick, style = logo.stroke)
            }
        }
    }
}
