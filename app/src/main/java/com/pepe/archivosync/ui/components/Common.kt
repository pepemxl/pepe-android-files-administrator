package com.pepe.archivosync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pepe.archivosync.ui.theme.AppColors

/** White rounded card with the design's 1px slate border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = AppColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline),
    ) { content() }
}

/** Pill status chip (icon + label) used in history / dashboard / p2p. */
@Composable
fun StatusChip(
    label: String,
    icon: ImageVector,
    fg: Color,
    bg: Color,
) {
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Thin determinate progress bar (track + accent fill). */
@Composable
fun ThinProgressBar(progress: Int, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = (progress.coerceIn(0, 100)) / 100f,
        label = "progress",
    )
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AppColors.Outline),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** Material-style switch matching the design's custom toggle dimensions. */
@Composable
fun AppToggle(checked: Boolean, accent: Color, onToggle: () -> Unit) {
    val knobOffset by animateFloatAsState(if (checked) 19f else 0f, label = "knob")
    Box(
        Modifier
            .width(44.dp)
            .height(25.dp)
            .clip(CircleShape)
            .background(if (checked) accent else AppColors.OutlineStrong)
            .clickable(onClick = onToggle),
    ) {
        Box(
            Modifier
                .padding(start = (2.5f + knobOffset).dp, top = 2.5.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Rounded filter chip with a count badge (history / p2p filters). */
@Composable
fun CountFilterChip(
    label: String,
    count: Int,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) accent else AppColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) accent else AppColors.Outline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                color = if (active) Color.White else Color(0xFF475569),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                color = if (active) Color.White.copy(alpha = 0.25f) else AppColors.SurfaceAlt,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(start = 6.dp),
            ) {
                Text(
                    text = count.toString(),
                    color = if (active) Color.White else AppColors.OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}
