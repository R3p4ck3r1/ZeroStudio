package com.smarttoolfactory.colordetector

import androidx.compose.ui.graphics.Color
import com.smarttoolfactory.extendedcolors.util.ColorUtil
import com.smarttoolfactory.extendedcolors.util.fractionToIntPercent
import kotlin.math.roundToInt

data class ColorData(
    val color: Color,
    val name: String = "",
) {
  val hexText: String = ColorUtil.colorToHex(color)

  val rgb: String by lazy {
    val rgb = ColorUtil.colorToRGBArray(color)
    "R: ${rgb[0]} G: ${rgb[1]} B: ${rgb[2]}"
  }

  val hslString: String by lazy {
    val hsl = ColorUtil.colorToHSL(color)
    "H: ${hsl[0].roundToInt()}° S: ${hsl[1].fractionToIntPercent()}% L: ${hsl[2].fractionToIntPercent()}%"
  }

  val hsvString: String by lazy {
    val hsv = ColorUtil.colorToHSV(color)
    "H: ${hsv[0].roundToInt()}° S: ${hsv[1].fractionToIntPercent()}% V: ${hsv[2].fractionToIntPercent()}%"
  }
}
