package com.kitsuneapp.data

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private val decimal = Regex("-?[0-9]+(?:\\.[0-9]+)?")
fun decimalMinor(value: String, scale: Int, positive: Boolean = true): Long {
    val text = value.trim()
    require(text.length <= 64 && decimal.matches(text)) { "请输入十进制金额" }
    val number = BigDecimal(text)
    require(number.scale() <= scale) { "金额精度最多为 $scale 位小数" }
    val result = storedMinor(number.movePointRight(scale).toBigIntegerExact())
    require(!positive || result > 0) { "金额必须大于 0" }
    return result
}

fun money(minor: BigInteger, scale: Int): String = BigDecimal(minor, scale).toPlainString()
fun money(minor: Long, scale: Int): String = money(minor.toBigInteger(), scale)

private fun storedMinor(minor: BigInteger): Long =
    minor.toString().toLongOrNull() ?: throw IllegalArgumentException("单笔金额超出 64 位范围")

private class Fraction private constructor(val n: BigInteger, val d: BigInteger) {
    operator fun plus(other: Fraction) = of(n * other.d + other.n * d, d * other.d)
    operator fun minus(other: Fraction) = of(n * other.d - other.n * d, d * other.d)
    operator fun times(other: Fraction) = of(n * other.n, d * other.d)
    operator fun div(other: Fraction) = of(n * other.d, d * other.n)
    fun roundMinor(scale: Int): Long =
        storedMinor(BigDecimal(n * BigInteger.TEN.pow(scale)).divide(BigDecimal(d), 0, RoundingMode.HALF_UP).toBigIntegerExact())

    companion object {
        fun of(n: BigInteger, d: BigInteger): Fraction {
            require(d.signum() != 0) { "除数不能为零" }
            val divisor = n.gcd(d) * d.signum().toBigInteger()
            return Fraction(n / divisor, d / divisor)
        }

        fun exact(decimal: BigDecimal) = of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
    }
}

fun calculate(expression: String, scale: Int): Long {
    val text = expression.replace('×', '*').replace('÷', '/').replace('−', '-')
    require(text.length in 1..512) { "请输入不超过 512 字符的表达式" }
    val parser = MoneyExpression(text)
    val result = parser.read().roundMinor(scale)
    require(result > 0) { "舍入后金额必须大于零" }
    return result
}

private class MoneyExpression(private val text: String) {
    private var index = 0

    fun read(): Fraction {
        var sum = term()
        while (true) {
            sum = when (val op = peek()) {
                '+', '-' -> {
                    index++
                    val term = term()
                    if (op == '+') sum + term else sum - term
                }
                else -> break
            }
        }
        require(peek() == null) { "表达式包含无效字符" }
        return sum
    }

    private fun term(): Fraction {
        var product = number()
        while (true) {
            product = when (val op = peek()) {
                '*', '/' -> {
                    index++
                    val next = number()
                    if (op == '*') product * next else product / next
                }
                else -> return product
            }
        }
    }

    private fun number(): Fraction {
        peek()
        val start = index
        if (text.getOrNull(index) in listOf('+', '-')) index++
        while (text.getOrNull(index)?.let { it in '0'..'9' || it == '.' } == true) index++
        val value = text.substring(start, index).toBigDecimalOrNull()
        require(value != null) { "表达式缺少有效数字" }
        return Fraction.exact(value)
    }

    private fun peek(): Char? {
        while (text.getOrNull(index)?.isWhitespace() == true) index++
        return text.getOrNull(index)
    }
}
