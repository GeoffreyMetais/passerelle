package fr.passerelle.client.core.designsystem.format

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return ""
    if (bytes < 1024L) return "$bytes o"
    val units = arrayOf("Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val rounded = ((value * 10.0).toLong() / 10.0)
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return "$text ${units[unitIndex]}"
}
