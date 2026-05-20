package com.itsaky.androidide.templates

enum class MaterialColor(val colorName: String, val color: String) {
  PURPLE_80("purple_80", "FFD0BCFF"),
  PURPLE_GREY_80("purple_grey_80", "FFCCC2DC"),
  PINK_80("pink_80", "FFEFB8C8"),
  PURPLE_40("purple_40", "FF6650a4"),
  PURPLE_GREY_40("purple_grey_40", "FF625b71"),
  PINK_40("pink_40", "FF7D5260");

  fun kotlinComposeVal(): String = """val ${underscoreToCamelCase(colorName)} = Color(0x$color)"""
}
