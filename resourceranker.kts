#!/usr/bin/env kotlinc -script

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.system.exitProcess


val textColorsUsed = mutableMapOf<String, Int>()
val magicNumbersUsed = mutableMapOf<Int, Int>()
val largestClasses = mutableMapOf<String, Int>()

// Bernardo Ferrari
// APACHE-2 License
val DEBUG = false

// from https://github.com/importre/crayon
fun String.cyan() = "\u001b[36m${this}\u001b[0m"
fun String.yellow() = "\u001b[33m${this}\u001b[0m"
fun String.white() = "\u001b[37m${this}\u001b[0m"
fun String.black() = "\u001b[30m${this}\u001b[0m"

val intro = """
+---------------------------------------------------------------------------------------+
+                        ${"Flutter Resource Ranker".yellow()}                                        +
+---------------------------------------------------------------------------------------+
+ This is a tool to rank colors, numbers and classes by size.                           +
+---------------------------------------------------------------------------------------+
+ USAGE:                                                                                +
+    ${"$ ./resourceranker.kts <project directory> [OPTIONS]".cyan()}                               +
+    ${"$ kotlinc -script resourceranker.kts <project directory> [OPTIONS]".cyan()}                 +
+                                                                                       +
+ OPTIONS:                                                                              +
+ color       How many colors you are using and how many times                          +
+ contrast    How many colors and how they compare to black and white                   +
+ num         How many magical numbers you are using and how many times                 +
+ class       How many lines of code each class has (approximate).                      +
+ help        Show this text.                                                           +
+ <int>       Max limit. If 0, shows all elements. Default is 10.                       +
+                                                                                       +
+ EXAMPLE:                                                                              +
+    ${"$ ./resourceranker.kts documents/project color 10".cyan()}                                  +
+    ${"$ ./resourceranker.kts ../ class 0".cyan()}                                                 +
+    ${"$ ./resourceranker.kts ../../ contrast 5".cyan()}                                           +
+    ${"$ ./resourceranker.kts ./ num".cyan()}                                                      +
+---------------------------------------------------------------------------------------+
+        ${"Get started here: https://github.com/bernaferrari/FlutterResourceRanker".yellow()}        +
+---------------------------------------------------------------------------------------+
"""

// for some reason it seems there is a bug when calling args directly, without parsing to a list.
val arguments = args.toList()

if (arguments.size < 2) println(intro)

if (args.contains("help")) {
    println(intro)
    exitProcess(0)
}

val defaultMode = arguments.size < 2 || arguments.getOrNull(1)?.toIntOrNull() != null

val path = if (arguments.isNotEmpty()) args.first() else "./"

val shouldRunColor = args.contains("color") || defaultMode
val shouldRunClass = args.contains("class") || defaultMode
val shouldRunNum = args.contains("num") || defaultMode
val shouldCalculateContrast = args.contains("contrast")

val maxTake = args.find { it.toIntOrNull() != null }?.toInt() ?: 10

val file = File(path)

if (DEBUG || args.size < 2) {
    println("Opening directory: ${file.absolutePath}\n")
}

if (!file.exists()) {
    println("Error! Directory does not exist: ${file.absolutePath}\n")
    exitProcess(0)
}

fun mostUsedColors(input: String) {
    // retrieve Color(0xff1da1f3) from multiple lines
    //
    // regex test:
    // analogous(Color(0xffff0000)) | ok
    // Color(0xffff0000) | ok
    //     Color(0xffff0000) | ok
    // analogousColor(0xffff0000) | fail
    val retrieveColorFromParam = """(\W|^)Color\(.+?\)""".toRegex()
    retrieveColorFromParam.findAll(input).forEach {
        // remove Color(...) to retrieve 0xff1da1f3 from single line
        val element = it.value.replace("(\\W|^)Color\\(".toRegex(), "").trimEnd(')')
        textColorsUsed[element] = textColorsUsed.getOrDefault(element, 0) + 1
    }

    // retrieve CupertinoTheme\n.of(context)\n.primaryColor
    val retrieveColorFromVar = """\WColor \w+\s*=\s*\w+[\s\S]*?;""".toRegex()
    retrieveColorFromVar.findAll(input).forEach {
        // remove "Color primaryColor = "
        val replaceRegex = "\\WColor \\w+\\s*=\\s*".toRegex()
        val element = it.value.replace(replaceRegex, "")
        textColorsUsed[element] = textColorsUsed.getOrDefault(element, 0) + 1
    }
}

fun shouldCheckMagicNumbers(input: String, last: Int): Boolean {
    // only detect magic numbers when they are not near a ';' or ','.
    for (item in last..input.length) {
        if (input[item] != ' ' || input[item] != '\n') {
            return input[item] != ';' && input[item] != ','
        }
    }
    return false
}

fun mostUsedMagicNumbers(input: String) {
    // retrieve every number from every line
    val retrieveDigits = "\\d+".toRegex()
    retrieveDigits.findAll(input).forEach {
        // this will ignore when a value is near a ';' or ',', which means it was probably declared there.
        // it.range.last + 1 because otherwise for(..) is inclusive and would catch a number.
        if (shouldCheckMagicNumbers(input, it.range.last + 1)) {
            val element = it.value.toIntOrNull() ?: 0
            if (element < -1 || element > 2) {
                magicNumbersUsed[element] = magicNumbersUsed.getOrDefault(element, 0) + 1
            }
        }
    }
}

// this method will be used to count lines between class { and the corresponding }.
fun countExpressionBlock(input: String, expression: Regex) {

    val stringSize = input.length - 1

    expression.findAll(input)
        .forEach { matchResult ->
            // rangeStarts starts at first {
            val rangeStart = matchResult.range.last + input.substring(matchResult.range.last).indexOfFirst { it == '{' }

            // make "class BlindScreen extends StatelessWidget" become "BlindScreen"
            val className = matchResult.value.split(' ')[1]

            // rangeEnd initially stops at the end of the string, but later will be set to end after the correct }
            var rangeEnd = stringSize

            var count = 0

            if (DEBUG) {
                println("[DEBUG] - range: ${matchResult.range} totalSize: $stringSize | $rangeStart class: $className")
            }

            for (item in rangeStart..stringSize) {
                if (input[item] == '{') count += 1 else if (input[item] == '}') count -= 1
                if (count == 0) {
                    rangeEnd = item
                    break
                }
            }

            // you can count the number of chars, the number of '\n' or the number of ';'.
            // This is dart, so the script is going to use ';'.
            // For number of chars, code would be: rangeEnd - rangeStart
            largestClasses[className] = input.substring(rangeStart, rangeEnd).count { it == ';' }
        }
}


// Where File is read and methods are called.
file.walkTopDown().forEach { file ->
    if (file.isFile && file.extension == "dart") {
        val input = file.readText()

        // Most used Color(...)
        if (shouldRunColor || shouldCalculateContrast)
            mostUsedColors(input)

        // Most used Magic Numbers
        if (shouldRunNum)
            mostUsedMagicNumbers(input)

        // Largest Classes
        if (shouldRunClass)
            countExpressionBlock(input, "class\\s+.*(?=\\{)".toRegex())
    }
}

fun printFinalValues(map: MutableMap<*, Int>) {
    val finalValues = map.entries.sortedByDescending { (_, value) -> value }
    if (maxTake > 0) println(finalValues.take(maxTake)) else println(finalValues)
}


// Color Contrast
// This will compare colors in textColorsUsed with white and black, to show the WACG contrast.
// Ideally, this would be in another file.
//
// ColorRGBa was modified from: Modified from https://github.com/openrndr/openrndr/blob/master/openrndr-color/src/main/kotlin/org/openrndr/color/ColorRGBa.kt

data class ColorRGBa(val r: Double, val g: Double, val b: Double) {

    operator fun invoke(r: Double = this.r, g: Double = this.g, b: Double = this.b) =
        ColorRGBa(r, g, b)

    companion object {

        fun fromHex(hex: String): ColorRGBa {
            val parsedHex = hex.replace("#", "")
            val len = parsedHex.length
            val mult = len / 3

            val colors = (0..2).map { idx ->
                var c = parsedHex.substring(idx * mult, (idx + 1) * mult)

                c = if (len == 3) c + c else c

                Integer.valueOf(c, 16)
            }

            val (r, g, b) = colors

            return ColorRGBa(r / 255.0, g / 255.0, b / 255.0)
        }

        // See <https://www.w3.org/TR/WCAG20/#relativeluminancedef>
        private fun linearizeColorComponent(component: Double): Double {
            if (component <= 0.03928)
                return component / 12.92
            return ((component + 0.055) / 1.055).pow(2.4)
        }
    }

    fun computeLuminance(): Double { // See <https://www.w3.org/TR/WCAG20/#relativeluminancedef>
        val r: Double = linearizeColorComponent(this.r)
        val g: Double = linearizeColorComponent(this.g)
        val b: Double = linearizeColorComponent(this.b)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}

fun calculateContrast(color1: ColorRGBa, color2: ColorRGBa): Double {
    val colorFirstLum: Double = color1.computeLuminance()
    val colorSecondLum: Double = color2.computeLuminance()
    val l1: Double = min(colorFirstLum, colorSecondLum)
    val l2: Double = max(colorFirstLum, colorSecondLum)
    return 1 / ((l1 + 0.05) / (l2 + 0.05))
}

fun getContrastLetters(contrast: Double): String {
    return when {
        contrast < 2.9 -> "fail"
        contrast < 4.5 -> "AA+"
        contrast < 7.0 -> "AA"
        contrast < 25 -> "AAA"
        else -> ""
    }
}

if (shouldCalculateContrast) {

    val sortedColors = textColorsUsed
        .toList()
        .sortedByDescending { (_, value) -> value }

    (sortedColors.takeIf { maxTake > 0 }?.take(maxTake) ?: sortedColors)
        .forEach { pair ->
            // "0x([a-f]|\\d){8}"
            pair.first.takeIf { it.length == 10 }?.takeLast(6)?.also {
                val color = ColorRGBa.fromHex(it)

                val blackContrast = calculateContrast(color, ColorRGBa.fromHex("000000"))
                val whiteContrast = calculateContrast(color, ColorRGBa.fromHex("ffffff"))

                println("${pair.first}: ${pair.second} times")
                println(
                    "${"▒".black()} Black: ${String.format("%.2f", blackContrast)}" +
                            " (${getContrastLetters(blackContrast)})" +
                            " / ${"▒".white()} White: ${String.format("%.2f", whiteContrast)}" +
                            " (${getContrastLetters(whiteContrast)})"
                )
                println()
            }
        }
}

// Where print happens

if (shouldRunColor) {
    println("Top Colors:")
    printFinalValues(textColorsUsed)
    println()
}
if (shouldRunNum) {
    println("Top Magic Numbers:")
    printFinalValues(magicNumbersUsed)
    println()
}
if (shouldRunClass) {
    println("Top Largest Classes:")
    printFinalValues(largestClasses)
    println()
}
