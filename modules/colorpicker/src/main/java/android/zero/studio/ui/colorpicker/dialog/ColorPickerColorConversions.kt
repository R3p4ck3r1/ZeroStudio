package android.zero.studio.ui.colorpicker.dialog

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class ColorCodeFormat(val label: String) {
  HexLower("#d9d9d9"),
  HexUpper("#D9D9D9"),
  ColorSrgbPercent("color(srgb 85% 85% 85%)"),
  ColorSrgbDecimal("color(srgb 0.85 0.85 0.85)"),
  RgbPercent("rgb(85% 85% 85%)"),
  RgbInteger("rgb(217 217 217)"),
  HslModern("hsl(none 0% 85%)"),
  HslLegacy("hsl(0 0% 85%)"),
  HwbModern("hwb(none 85% 15%)"),
  HwbLegacy("hwb(0 85% 15%)"),
}

internal enum class StandardColorSpace(val label: String) {
  Srgb("sRGB"),
  LinearSrgb("线性 sRGB"),
  AdobeRgb("Adobe RGB"),
  DisplayP3("Display P3"),
  Rec2020("Rec. 2020"),
  ProPhotoRgb("ProPhoto RGB"),
  CieLch("CIE LCH"),
  OkLch("OK LCH"),
  CieLab("CIE LAB"),
  OkLab("OK LAB"),
  XyzD65("CIE XYZ D65"),
  XyzD50("CIE XYZ D50"),
}

internal fun parseCssColor(raw: String?): Color? {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return null
  parseHexColor(text)?.let { return it }
  val lower = text.lowercase()
  return when {
    lower.startsWith("rgb(") -> parseRgbColor(lower)
    lower.startsWith("color(srgb") -> parseColorSrgb(lower)
    lower.startsWith("hsl(") -> parseHslColor(lower)
    lower.startsWith("hwb(") -> parseHwbColor(lower)
    else -> null
  }
}

internal fun formatColorCode(color: Color, format: ColorCodeFormat): String {
  val r = color.red.coerceIn(0f, 1f)
  val g = color.green.coerceIn(0f, 1f)
  val b = color.blue.coerceIn(0f, 1f)
  val ri = (r * 255).roundToInt().coerceIn(0, 255)
  val gi = (g * 255).roundToInt().coerceIn(0, 255)
  val bi = (b * 255).roundToInt().coerceIn(0, 255)
  val (h, s, l) = rgbToHsl(r, g, b)
  val (hh, ww, bb) = rgbToHwb(r, g, b)
  val hue = if (s <= 0.0001f) "none" else fmt(h)
  val hwbHue = if (hh.isNaN()) "none" else fmt(hh)
  return when (format) {
    ColorCodeFormat.HexLower -> "#%02x%02x%02x".format(ri, gi, bi)
    ColorCodeFormat.HexUpper -> "#%02X%02X%02X".format(ri, gi, bi)
    ColorCodeFormat.ColorSrgbPercent -> "color(srgb ${pct(r)} ${pct(g)} ${pct(b)})"
    ColorCodeFormat.ColorSrgbDecimal -> "color(srgb ${dec(r)} ${dec(g)} ${dec(b)})"
    ColorCodeFormat.RgbPercent -> "rgb(${pct(r)} ${pct(g)} ${pct(b)})"
    ColorCodeFormat.RgbInteger -> "rgb($ri $gi $bi)"
    ColorCodeFormat.HslModern -> "hsl($hue ${pct(s)} ${pct(l)})"
    ColorCodeFormat.HslLegacy -> "hsl(${if (s <= 0.0001f) "0" else fmt(h)} ${pct(s)} ${pct(l)})"
    ColorCodeFormat.HwbModern -> "hwb($hwbHue ${pct(ww)} ${pct(bb)})"
    ColorCodeFormat.HwbLegacy -> "hwb(${if (hh.isNaN()) "0" else fmt(hh)} ${pct(ww)} ${pct(bb)})"
  }
}

internal fun colorSpaceValues(color: Color, space: StandardColorSpace): List<Pair<String, String>> {
  val rgb = doubleArrayOf(color.red.toDouble(), color.green.toDouble(), color.blue.toDouble())
  val lin = rgb.map(::srgbToLinear).toDoubleArray()
  val xyzD65 = multiply(SRGB_TO_XYZ_D65, lin)
  val xyzD50 = multiply(BRADFORD_D65_TO_D50, xyzD65)
  return when (space) {
    StandardColorSpace.Srgb -> listOf("R" to dec(rgb[0]), "G" to dec(rgb[1]), "B" to dec(rgb[2]))
    StandardColorSpace.LinearSrgb -> listOf("R" to dec(lin[0]), "G" to dec(lin[1]), "B" to dec(lin[2]))
    StandardColorSpace.AdobeRgb -> rgbProfileValues(xyzD65, XYZ_D65_TO_ADOBE_RGB, ::adobeEncode)
    StandardColorSpace.DisplayP3 -> rgbProfileValues(xyzD65, XYZ_D65_TO_DISPLAY_P3, ::srgbEncode)
    StandardColorSpace.Rec2020 -> rgbProfileValues(xyzD65, XYZ_D65_TO_REC2020, ::rec2020Encode)
    StandardColorSpace.ProPhotoRgb -> rgbProfileValues(xyzD50, XYZ_D50_TO_PROPHOTO, ::proPhotoEncode)
    StandardColorSpace.CieLab -> labValues(xyzD50)
    StandardColorSpace.CieLch -> lchValues(labValuesRaw(xyzD50))
    StandardColorSpace.OkLab -> okLabValues(xyzD65)
    StandardColorSpace.OkLch -> okLchValues(okLabRaw(xyzD65))
    StandardColorSpace.XyzD65 -> listOf("X" to dec(xyzD65[0]), "Y" to dec(xyzD65[1]), "Z" to dec(xyzD65[2]))
    StandardColorSpace.XyzD50 -> listOf("X" to dec(xyzD50[0]), "Y" to dec(xyzD50[1]), "Z" to dec(xyzD50[2]))
  }
}

private val SRGB_TO_XYZ_D65 = arrayOf(
  doubleArrayOf(0.4124564, 0.3575761, 0.1804375),
  doubleArrayOf(0.2126729, 0.7151522, 0.0721750),
  doubleArrayOf(0.0193339, 0.1191920, 0.9503041),
)
private val BRADFORD_D65_TO_D50 = arrayOf(
  doubleArrayOf(1.0479298, 0.0229468, -0.0501922),
  doubleArrayOf(0.0296278, 0.9904345, -0.0170738),
  doubleArrayOf(-0.0092430, 0.0150552, 0.7518743),
)
private val XYZ_D65_TO_ADOBE_RGB = arrayOf(
  doubleArrayOf(2.0413690, -0.5649464, -0.3446944),
  doubleArrayOf(-0.9692660, 1.8760108, 0.0415560),
  doubleArrayOf(0.0134474, -0.1183897, 1.0154096),
)
private val XYZ_D65_TO_DISPLAY_P3 = arrayOf(
  doubleArrayOf(2.4934969, -0.9313836, -0.4027108),
  doubleArrayOf(-0.8294890, 1.7626640, 0.0236247),
  doubleArrayOf(0.0358458, -0.0761724, 0.9568845),
)
private val XYZ_D65_TO_REC2020 = arrayOf(
  doubleArrayOf(1.7166512, -0.3556708, -0.2533663),
  doubleArrayOf(-0.6666844, 1.6164812, 0.0157685),
  doubleArrayOf(0.0176399, -0.0427706, 0.9421031),
)
private val XYZ_D50_TO_PROPHOTO = arrayOf(
  doubleArrayOf(1.3459433, -0.2556075, -0.0511118),
  doubleArrayOf(-0.5445989, 1.5081673, 0.0205351),
  doubleArrayOf(0.0000000, 0.0000000, 1.2118128),
)

private fun parseHexColor(text: String): Color? {
  val value = text.removePrefix("#")
  if (value.length !in setOf(3, 4, 6, 8) || value.any { it.digitToIntOrNull(16) == null }) return null
  val expanded = if (value.length <= 4) value.map { "$it$it" }.joinToString("") else value
  val withAlpha = if (expanded.length == 6) "ff$expanded" else expanded.substring(6, 8) + expanded.substring(0, 6)
  return Color(android.graphics.Color.parseColor("#$withAlpha"))
}

private fun parseRgbColor(text: String): Color? {
  val parts = functionParts(text) ?: return null
  if (parts.size < 3) return null
  return Color(channel(parts[0]), channel(parts[1]), channel(parts[2]))
}

private fun parseColorSrgb(text: String): Color? {
  val parts = functionParts(text.removePrefix("color")) ?: return null
  val values = parts.filterNot { it == "srgb" }
  if (values.size < 3) return null
  return Color(srgbComponent(values[0]), srgbComponent(values[1]), srgbComponent(values[2]))
}

private fun parseHslColor(text: String): Color? {
  val parts = functionParts(text) ?: return null
  if (parts.size < 3) return null
  val h = if (parts[0] == "none") 0f else parts[0].removeSuffix("deg").toFloatOrNull() ?: return null
  val s = percent(parts[1]) ?: return null
  val l = percent(parts[2]) ?: return null
  return Color.hsl(normalizeHue(h), s, l)
}

private fun parseHwbColor(text: String): Color? {
  val parts = functionParts(text) ?: return null
  if (parts.size < 3) return null
  val h = if (parts[0] == "none") 0f else parts[0].removeSuffix("deg").toFloatOrNull() ?: return null
  var w = percent(parts[1]) ?: return null
  var bl = percent(parts[2]) ?: return null
  val sum = w + bl
  if (sum > 1f) { w /= sum; bl /= sum }
  val base = Color.hsv(normalizeHue(h), 1f, 1f)
  return Color(base.red * (1 - w - bl) + w, base.green * (1 - w - bl) + w, base.blue * (1 - w - bl) + w)
}

private fun functionParts(text: String): List<String>? = text.substringAfter('(', "").substringBeforeLast(')', "").takeIf { it.isNotBlank() }?.replace(",", " ")?.split(Regex("\\s+|/"))?.filter { it.isNotBlank() }
private fun channel(value: String): Float = if (value.endsWith('%')) ((value.dropLast(1).toFloatOrNull() ?: 0f) / 100f).coerceIn(0f, 1f) else (((value.toFloatOrNull() ?: 0f) / 255f).coerceIn(0f, 1f))
private fun srgbComponent(value: String): Float = if (value.endsWith('%')) ((value.dropLast(1).toFloatOrNull() ?: 0f) / 100f).coerceIn(0f, 1f) else (value.toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
private fun normalizeHue(value: Float): Float = ((value % 360f) + 360f) % 360f
private fun percent(value: String): Float? = if (value.endsWith('%')) value.dropLast(1).toFloatOrNull()?.div(100f) else value.toFloatOrNull()

private fun rgbToHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
  val max = max(r, max(g, b)); val min = min(r, min(g, b)); val l = (max + min) / 2f
  if (max == min) return Triple(0f, 0f, l)
  val d = max - min
  val s = if (l > .5f) d / (2f - max - min) else d / (max + min)
  val h = when (max) { r -> ((g - b) / d + if (g < b) 6f else 0f); g -> ((b - r) / d + 2f); else -> ((r - g) / d + 4f) } * 60f
  return Triple(h, s, l)
}
private fun rgbToHwb(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
  val (h, s, _) = rgbToHsl(r, g, b)
  return Triple(if (s <= 0.0001f) Float.NaN else h, min(r, min(g, b)), 1f - max(r, max(g, b)))
}
private fun srgbToLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
private fun srgbEncode(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
private fun adobeEncode(c: Double) = c.coerceAtLeast(0.0).pow(1.0 / 2.19921875)
private fun rec2020Encode(c: Double) = if (c < 0.018053968510807) 4.5 * c else 1.09929682680944 * c.pow(0.45) - 0.09929682680944
private fun proPhotoEncode(c: Double) = if (c < 1.0 / 512.0) 16 * c else c.pow(1.0 / 1.8)
private fun multiply(m: Array<DoubleArray>, v: DoubleArray) = doubleArrayOf(m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2], m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2])
private fun rgbProfileValues(xyz: DoubleArray, matrix: Array<DoubleArray>, encode: (Double) -> Double) = multiply(matrix, xyz).map(encode).let { listOf("R" to dec(it[0]), "G" to dec(it[1]), "B" to dec(it[2])) }
private fun labValuesRaw(xyzD50: DoubleArray): DoubleArray {
  val x = labF(xyzD50[0] / 0.96422); val y = labF(xyzD50[1]); val z = labF(xyzD50[2] / 0.82521)
  return doubleArrayOf(116 * y - 16, 500 * (x - y), 200 * (y - z))
}
private fun labF(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (841.0 / 108.0) * t + 4.0 / 29.0
private fun labValues(xyzD50: DoubleArray) = labValuesRaw(xyzD50).let { listOf("L" to fmt(it[0]), "a" to fmt(it[1]), "b" to fmt(it[2])) }
private fun lchValues(lab: DoubleArray): List<Pair<String, String>> { val c = sqrt(lab[1] * lab[1] + lab[2] * lab[2]); val h = (Math.toDegrees(atan2(lab[2], lab[1])) + 360) % 360; return listOf("L" to fmt(lab[0]), "C" to fmt(c), "H" to fmt(h)) }
private fun okLabRaw(xyzD65: DoubleArray): DoubleArray {
  val l = cbrt(0.8189330101 * xyzD65[0] + 0.3618667424 * xyzD65[1] - 0.1288597137 * xyzD65[2])
  val m = cbrt(0.0329845436 * xyzD65[0] + 0.9293118715 * xyzD65[1] + 0.0361456387 * xyzD65[2])
  val s = cbrt(0.0482003018 * xyzD65[0] + 0.2643662691 * xyzD65[1] + 0.6338517070 * xyzD65[2])
  return doubleArrayOf(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s, 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
}
private fun okLabValues(xyzD65: DoubleArray) = okLabRaw(xyzD65).let { listOf("L" to fmt(it[0]), "a" to fmt(it[1]), "b" to fmt(it[2])) }
private fun okLchValues(lab: DoubleArray): List<Pair<String, String>> { val c = sqrt(lab[1] * lab[1] + lab[2] * lab[2]); val h = (Math.toDegrees(atan2(lab[2], lab[1])) + 360) % 360; return listOf("L" to fmt(lab[0]), "C" to fmt(c), "H" to fmt(h)) }
private fun pct(v: Float) = "${(v * 100).roundToInt()}%"
private fun dec(v: Float) = fmt(v.toDouble())
private fun dec(v: Double) = fmt(v)
private fun fmt(v: Float) = fmt(v.toDouble())
private fun fmt(v: Double) = if (kotlin.math.abs(v) < 0.0005) "0" else "%.3f".format(v).trimEnd('0').trimEnd('.')
