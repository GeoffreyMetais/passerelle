package fr.passerelle.client.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import fr.passerelle.client.core.designsystem.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltipText)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.pointerHoverIcon(
                if (enabled) PointerIcon.Hand else PointerIcon.Default
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltipText,
                tint = tint,
            )
        }
    }
}

data class SvgQrData(
    val width: Float,
    val height: Float,
    val rects: List<QrRect>,
    val pathCommands: List<String>,
)

data class QrRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private val VIEWBOX_REGEX = Regex("""viewBox\s*=\s*"([^"]+)"""")
private val RECT_TAG_REGEX = Regex("""<rect\b([^>]*)/?>""")
private val PATH_D_REGEX = Regex("""<path\b[^>]*\bd\s*=\s*"([^"]+)"""")
private val ATTR_REGEX = Regex("""(\w+)\s*=\s*"([^"]+)"""")

fun parseQrSvg(svg: String): SvgQrData? {
    val viewBox = VIEWBOX_REGEX.find(svg)?.groupValues?.get(1)
        ?.trim()
        ?.split(Regex("""[\s,]+"""))
        ?.mapNotNull { it.toFloatOrNull() }
    val width = viewBox?.getOrNull(2) ?: 29f
    val height = viewBox?.getOrNull(3) ?: 29f

    val rects = RECT_TAG_REGEX.findAll(svg).mapNotNull { match ->
        val attrs = ATTR_REGEX.findAll(match.groupValues[1])
            .associate { it.groupValues[1] to it.groupValues[2] }
        val fill = attrs["fill"]
        if (fill != null && (fill.equals("#fff", true) || fill.equals("#ffffff", true) || fill.equals("white", true) || fill.equals("none", true))) {
            return@mapNotNull null
        }
        val x = attrs["x"]?.toFloatOrNull() ?: return@mapNotNull null
        val y = attrs["y"]?.toFloatOrNull() ?: return@mapNotNull null
        val w = attrs["width"]?.toFloatOrNull() ?: return@mapNotNull null
        val h = attrs["height"]?.toFloatOrNull() ?: return@mapNotNull null
        if (w >= width * 0.9f && h >= height * 0.9f) return@mapNotNull null
        QrRect(x = x, y = y, width = w, height = h)
    }.toList()

    val paths = PATH_D_REGEX.findAll(svg).map { it.groupValues[1] }.toList()
    if (rects.isEmpty() && paths.isEmpty()) return null
    return SvgQrData(width = width, height = height, rects = rects, pathCommands = paths)
}

/**
 * Multiplatform QR code renderer supporting SVG `<rect>` modules (as emitted by Rust `qrcode` crate)
 * and simple `M x y h w v h h -w z` / `M x y L ...` path squares.
 */
@Composable
fun SvgQrCodeView(
    svgContent: String,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(svgContent) { parseQrSvg(svgContent) }
    val moduleColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLowest

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Spacing.md),
        color = containerColor,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(Spacing.md)
                .background(Color.White, RoundedCornerShape(Spacing.sm))
                .padding(Spacing.sm),
        ) {
            if (parsed != null) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    val scaleX = size.width / parsed.width
                    val scaleY = size.height / parsed.height
                    parsed.rects.forEach { r ->
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(r.x * scaleX, r.y * scaleY),
                            size = Size(r.width * scaleX, r.height * scaleY),
                        )
                    }
                    if (parsed.rects.isEmpty()) {
                        parsed.pathCommands.forEach { d ->
                            drawSvgSquareSubpaths(
                                d = d,
                                scaleX = scaleX,
                                scaleY = scaleY,
                                color = Color.Black,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "QR",
                    style = MaterialTheme.typography.titleLarge,
                    color = moduleColor,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSvgSquareSubpaths(
    d: String,
    scaleX: Float,
    scaleY: Float,
    color: Color,
) {
    val subpaths = d.split('M', 'm').filter { it.isNotBlank() }
    val numRegex = Regex("""-?\d+(?:\.\d+)?""")
    for (sub in subpaths) {
        val nums = numRegex.findAll(sub).mapNotNull { it.value.toFloatOrNull() }.toList()
        if (nums.size >= 4) {
            val x = nums[0]
            val y = nums[1]
            val w = kotlin.math.abs(nums[2])
            val h = kotlin.math.abs(nums[3])
            if (w > 0f && h > 0f && w <= 4f && h <= 4f) {
                drawRect(
                    color = color,
                    topLeft = Offset(x * scaleX, y * scaleY),
                    size = Size(w * scaleX, h * scaleY),
                )
            }
        }
    }
}
