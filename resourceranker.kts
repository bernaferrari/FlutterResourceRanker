#!/usr/bin/env kotlinc -script

import java.io.File
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
+ num         How many magical numbers you are using and how many times                 +
+ class       How many lines of code each class has (approximate).                      +
+ <int>       Max limit. If == 0, shows all elements. Default is 10.                    +
+                                                                                       +
+ EXAMPLE:                                                                              +
+    ${"$ ./resourceranker.kts documents/project color 10".cyan()}                                  +
+    ${"$ ./resourceranker.kts ../ class -1".cyan()}                                                +
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

file.walkTopDown().forEach { file ->
    if (file.isFile && file.extension == "dart") {
        val input = file.readText()

        // Most used Color(...)
        if (shouldRunColor)
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
