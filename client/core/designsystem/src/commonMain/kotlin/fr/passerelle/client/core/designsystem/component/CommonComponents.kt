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

private val PATH_SUBPATH_RECT_REGEX = Regex("""(-?[\d.]+)[\s,]+(-?[\d.]+)h(-?[\d.]+)v(-?[\d.]+)""")

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

    val pathRects = PATH_D_REGEX.findAll(svg).flatMap { match ->
        parseQrPathRects(match.groupValues[1], width, height)
    }.toList()
    val allRects = rects + pathRects
    if (allRects.isEmpty()) return null
    return SvgQrData(width = width, height = height, rects = allRects)
}

fun parseQrPathRects(d: String, canvasWidth: Float, canvasHeight: Float): List<QrRect> =
    d.split('M', 'm')
        .filter { it.isNotBlank() }
        .mapNotNull { subpath ->
            val match = PATH_SUBPATH_RECT_REGEX.find(subpath) ?: return@mapNotNull null
            val x = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val y = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val w = kotlin.math.abs(match.groupValues[3].toFloatOrNull() ?: return@mapNotNull null)
            val h = kotlin.math.abs(match.groupValues[4].toFloatOrNull() ?: return@mapNotNull null)
            if (w <= 0f || h <= 0f) return@mapNotNull null
            if (w >= canvasWidth * 0.9f && h >= canvasHeight * 0.9f) return@mapNotNull null
            QrRect(x = x, y = y, width = w, height = h)
        }

/**
 * Multiplatform QR code renderer. Supports SVG `<rect>` modules and the `<path>` output of the
 * Rust `qrcode` crate (`M x y h w v h H x V y` subpaths at any module scale).
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
