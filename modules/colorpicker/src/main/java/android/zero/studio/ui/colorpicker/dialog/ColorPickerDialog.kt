package android.zero.studio.ui.colorpicker.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.zero.studio.ui.colorpicker.picker.*
import android.zero.studio.ui.colorpicker.ui.Blue400
import com.smarttoolfactory.extendedcolors.util.ColorUtil

@Composable
fun ColorPickerRingDiamondHSLDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      ColorPickerRingDiamondHSL(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(Color(0xcc212121), shape = RoundedCornerShape(5.dp))
                  .padding(horizontal = 10.dp, vertical = 2.dp),
          initialColor = initialColor,
          ringOuterRadiusFraction = ringOuterRadiusFraction,
          ringInnerRadiusFraction = ringInnerRadiusFraction,
          ringBackgroundColor = ringBackgroundColor,
          ringBorderStrokeColor = ringBorderStrokeColor,
          ringBorderStrokeWidth = ringBorderStrokeWidth,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingDiamondHEXDialog(
    initialColor: Color,
    initialQuery: String? = null,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(parseCssColor(initialQuery) ?: initialColor.copy()) }
  var queryText by remember { mutableStateOf(initialQuery?.takeIf { it.isNotBlank() } ?: ColorUtil.colorToHexAlpha(color)) }
  var codeFormat by remember { mutableStateOf(ColorCodeFormat.HexUpper) }
  var colorSpace by remember { mutableStateOf(StandardColorSpace.Srgb) }
  var pickerMode by remember { mutableStateOf(IntegratedPickerMode.RingDiamondHex) }
  var showSpectrum by remember { mutableStateOf(true) }
  var showCurves by remember { mutableStateOf(false) }
  var showDial by remember { mutableStateOf(false) }
  var colorSeed by remember { mutableStateOf(0) }
  val selectedCode = formatColorCode(color, codeFormat)

  Dialog(onDismissRequest = { onDismiss(color, selectedCode) }) {
    Surface(color = Color(0xfff7f7f7), shape = RoundedCornerShape(12.dp), elevation = 8.dp) {
      Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IntegratedColorPickerTopBar(
            color = color,
            colorSpace = colorSpace,
            onColorSpaceChange = { colorSpace = it },
            showSpectrum = showSpectrum,
            onToggleSpectrum = { showSpectrum = !showSpectrum },
            showCurves = showCurves,
            onToggleCurves = { showCurves = !showCurves },
            showDial = showDial,
            onToggleDial = { showDial = !showDial },
        )

        if (showSpectrum) {
          ColorSpectrumDetails(color = color, colorSpace = colorSpace)
        }

        SimpleDropdown(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = "颜色盘",
            selected = pickerMode.label,
            items = IntegratedPickerMode.values().map { it.label },
            onSelected = { label -> pickerMode = IntegratedPickerMode.values().first { it.label == label } },
        )

        key(pickerMode, colorSeed) {
          IntegratedPicker(
              mode = pickerMode,
              initialColor = color,
              ringOuterRadiusFraction = ringOuterRadiusFraction,
              ringInnerRadiusFraction = ringInnerRadiusFraction,
              ringBackgroundColor = ringBackgroundColor,
              ringBorderStrokeColor = ringBorderStrokeColor,
              ringBorderStrokeWidth = ringBorderStrokeWidth,
              selectionRadius = selectionRadius,
          ) { colorChange, hexChange ->
            color = colorChange
            queryText = if (codeFormat == ColorCodeFormat.HexUpper || codeFormat == ColorCodeFormat.HexLower) {
              formatColorCode(colorChange, codeFormat)
            } else {
              queryText.ifBlank { hexChange }
            }
          }
        }

        if (showCurves) {
          CurveTonePanel(color = color, onColorChange = { color = it; colorSeed++ })
        }
        if (showDial) {
          DialTonePanel(color = color, onColorChange = { color = it; colorSeed++ })
        }

        ColorCodeQueryRow(
            text = queryText,
            selectedFormat = codeFormat,
            currentCode = selectedCode,
            onFormatChange = {
              codeFormat = it
              queryText = formatColorCode(color, it)
            },
            onTextChange = { value ->
              queryText = value
              parseCssColor(value)?.let { parsed ->
                color = parsed
                colorSeed++
              }
            },
        )

        FloatingActionButton(
            onClick = { onDismiss(color, selectedCode) },
            backgroundColor = Color.Black,
        ) {
          Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
        }
      }
    }
  }
}

private enum class IntegratedPickerMode(val label: String) {
  RingDiamondHex("轮盘 + 菱形 HEX"),
  RingRectHsl("轮盘 + 长方形 HSL"),
  RingRectHsv("轮盘 + 长方形 HSV"),
  SpectrumSv("长方形三原色 HSV"),
  CircleValue("圆形明度 HSV"),
  HueSaturation("色相/饱和曲线"),
  HueLightness("色相/亮度曲线"),
}

@Composable
private fun IntegratedPicker(
    mode: IntegratedPickerMode,
    initialColor: Color,
    ringOuterRadiusFraction: Float,
    ringInnerRadiusFraction: Float,
    ringBackgroundColor: Color,
    ringBorderStrokeColor: Color,
    ringBorderStrokeWidth: Dp,
    selectionRadius: Dp,
    onColorChange: (Color, String) -> Unit,
) {
  val pickerModifier = Modifier.fillMaxWidth().background(Color(0xcc212121), shape = RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 2.dp)
  when (mode) {
    IntegratedPickerMode.RingDiamondHex -> ColorPickerRingDiamondHEX(pickerModifier, initialColor, ringOuterRadiusFraction, ringInnerRadiusFraction, ringBackgroundColor, ringBorderStrokeColor, ringBorderStrokeWidth, selectionRadius, onColorChange)
    IntegratedPickerMode.RingRectHsl -> ColorPickerRingRectHSL(pickerModifier, initialColor, ringOuterRadiusFraction, ringInnerRadiusFraction, ringBackgroundColor, ringBorderStrokeColor, ringBorderStrokeWidth, selectionRadius, onColorChange)
    IntegratedPickerMode.RingRectHsv -> ColorPickerRingRectHSV(pickerModifier, initialColor, ringOuterRadiusFraction, ringInnerRadiusFraction, ringBackgroundColor, ringBorderStrokeColor, ringBorderStrokeWidth, selectionRadius, onColorChange)
    IntegratedPickerMode.SpectrumSv -> ColorPickerRectSaturationValueHSV(modifier = pickerModifier, selectionRadius = selectionRadius, initialColor = initialColor, onColorChange = onColorChange)
    IntegratedPickerMode.CircleValue -> ColorPickerCircleValueHSV(modifier = pickerModifier, selectionRadius = selectionRadius, initialColor = initialColor, onColorChange = onColorChange)
    IntegratedPickerMode.HueSaturation -> ColorPickerRectHueSaturationHSL(modifier = pickerModifier, selectionRadius = selectionRadius, initialColor = initialColor, onColorChange = onColorChange)
    IntegratedPickerMode.HueLightness -> ColorPickerRectHueLightnessHSL(modifier = pickerModifier, selectionRadius = selectionRadius, initialColor = initialColor, onColorChange = onColorChange)
  }
}

@Composable
private fun IntegratedColorPickerTopBar(
    color: Color,
    colorSpace: StandardColorSpace,
    onColorSpaceChange: (StandardColorSpace) -> Unit,
    showSpectrum: Boolean,
    onToggleSpectrum: () -> Unit,
    showCurves: Boolean,
    onToggleCurves: () -> Unit,
    showDial: Boolean,
    onToggleDial: () -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(44.dp).background(color, RoundedCornerShape(8.dp)))
    SimpleDropdown(
        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        label = "标准算法",
        selected = colorSpace.label,
        items = StandardColorSpace.values().map { it.label },
        onSelected = { label -> onColorSpaceChange(StandardColorSpace.values().first { it.label == label }) },
    )
    TextButton(onClick = onToggleSpectrum) { Text(if (showSpectrum) "隐藏频谱" else "频谱") }
    TextButton(onClick = onToggleCurves) { Text(if (showCurves) "隐藏曲线" else "曲线") }
    TextButton(onClick = onToggleDial) { Text(if (showDial) "隐藏拨盘" else "拨盘") }
  }
}

@Composable
private fun ColorSpectrumDetails(color: Color, colorSpace: StandardColorSpace) {
  val values = colorSpaceValues(color, colorSpace)
  Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp)) {
    Row(Modifier.fillMaxWidth().height(18.dp)) {
      listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red).forEach { swatch ->
        Box(Modifier.weight(1f).fillMaxHeight().background(swatch))
      }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
      values.forEach { (name, value) -> Text("$name $value", color = Color(0xff303030)) }
    }
  }
}

@Composable
private fun CurveTonePanel(color: Color, onColorChange: (Color) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0x11ffffff), RoundedCornerShape(8.dp)).padding(8.dp)) {
    Text("曲线调色", color = Color(0xff303030))
    Slider(value = color.red, onValueChange = { onColorChange(color.copy(red = it)) })
    Slider(value = color.green, onValueChange = { onColorChange(color.copy(green = it)) })
    Slider(value = color.blue, onValueChange = { onColorChange(color.copy(blue = it)) })
  }
}

@Composable
private fun DialTonePanel(color: Color, onColorChange: (Color) -> Unit) {
  val hsv = ColorUtil.colorToHSV(color)
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0x11ffffff), RoundedCornerShape(8.dp)).padding(8.dp)) {
    Text("拨盘调色 / Photoshop 风格 HSB", color = Color(0xff303030))
    Slider(value = hsv[0] / 360f, onValueChange = { onColorChange(Color.hsv(it * 360f, hsv[1], hsv[2], color.alpha)) })
    Slider(value = hsv[1], onValueChange = { onColorChange(Color.hsv(hsv[0], it, hsv[2], color.alpha)) })
    Slider(value = hsv[2], onValueChange = { onColorChange(Color.hsv(hsv[0], hsv[1], it, color.alpha)) })
  }
}

@Composable
private fun ColorCodeQueryRow(
    text: String,
    selectedFormat: ColorCodeFormat,
    currentCode: String,
    onFormatChange: (ColorCodeFormat) -> Unit,
    onTextChange: (String) -> Unit,
) {
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
        modifier = Modifier.weight(1f),
        value = text,
        onValueChange = onTextChange,
        label = { Text("颜色查询 / 颜色码") },
        placeholder = { Text(currentCode) },
        singleLine = true,
    )
    SimpleDropdown(
        modifier = Modifier.width(180.dp).padding(start = 6.dp),
        label = "格式",
        selected = selectedFormat.label,
        items = ColorCodeFormat.values().map { it.label },
        onSelected = { label -> onFormatChange(ColorCodeFormat.values().first { it.label == label }) },
    )
  }
}

@Composable
private fun SimpleDropdown(
    modifier: Modifier = Modifier,
    label: String,
    selected: String,
    items: List<String>,
    onSelected: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Text("$label: $selected", maxLines = 1)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      items.forEach { item ->
        DropdownMenuItem(onClick = { expanded = false; onSelected(item) }) { Text(item) }
      }
    }
  }
}

@Composable
fun ColorPickerRingRectHSLDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      ColorPickerRingRectHSL(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(Color(0xcc212121), shape = RoundedCornerShape(5.dp))
                  .padding(horizontal = 10.dp, vertical = 2.dp),
          initialColor = initialColor,
          ringOuterRadiusFraction = ringOuterRadiusFraction,
          ringInnerRadiusFraction = ringInnerRadiusFraction,
          ringBackgroundColor = ringBackgroundColor,
          ringBorderStrokeColor = ringBorderStrokeColor,
          ringBorderStrokeWidth = ringBorderStrokeWidth,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingRectHSVDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      ColorPickerRingRectHSV(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(Color(0xcc212121), shape = RoundedCornerShape(5.dp))
                  .padding(horizontal = 10.dp, vertical = 2.dp),
          initialColor = initialColor,
          ringOuterRadiusFraction = ringOuterRadiusFraction,
          ringInnerRadiusFraction = ringInnerRadiusFraction,
          ringBackgroundColor = ringBackgroundColor,
          ringBorderStrokeColor = ringBorderStrokeColor,
          ringBorderStrokeWidth = ringBorderStrokeWidth,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingHexHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRingRectHex(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerCircleHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerCircleValueHSV(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerSVRectHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectSaturationValueHSV(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerSLRectHSLDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectSaturationLightnessHSL(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHSRectHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueSaturationHSV(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHVRectHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueValueHSV(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHSRectHSLDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueSaturationHSL(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHLRectHSLDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueLightnessHSL(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerM2Dialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      M2ColorPicker { colorChange ->
        color = colorChange
        hexString = ColorUtil.colorToHex(color)
      }
    }
  }
}

@Composable
fun ColorPickerM3Dialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      M3ColorPicker { colorChange ->
        color = colorChange
        hexString = ColorUtil.colorToHex(color)
      }
    }
  }
}
