package org.cmchat.app.tools

import kotlin.math.sqrt

/**
 * Immediate-execution pocket-calculator logic modelled on the CASIO CT-200N
 * key set: digits, . , + − × ÷ = , % , √ , C/CE, and memory MRC / M- / M+.
 * This is the engine only (pure, unit-tested); the UI wears Google-calculator
 * styling. No expression parsing — each operator applies the pending one first,
 * exactly like the physical calculator.
 */
class CalcEngine {
    var display: String = "0"; private set
    var memory: Double = 0.0; private set
    var hasMemory: Boolean = false; private set

    private var acc: Double? = null       // left operand / running total
    private var pending: Char? = null     // + - * /
    private var fresh = true              // next digit starts a new number
    private var lastCe = false            // C/CE: first = clear entry, second = all

    private fun current(): Double = display.toDoubleOrNull() ?: 0.0

    fun digit(d: Int) {
        lastCe = false
        if (fresh || display == "0") display = d.toString() else display += d.toString()
        fresh = false
    }

    fun dot() {
        lastCe = false
        if (fresh) { display = "0."; fresh = false }
        else if (!display.contains('.')) display += "."
    }

    /** +, -, *, / */
    fun op(o: Char) {
        lastCe = false
        val cur = current()
        acc = if (acc == null || pending == null) cur else apply(acc!!, cur, pending!!)
        display = format(acc!!)
        pending = o
        fresh = true
    }

    fun equals() {
        lastCe = false
        val p = pending ?: return
        val result = apply(acc ?: 0.0, current(), p)
        display = format(result)
        acc = null; pending = null; fresh = true
    }

    fun percent() {
        // CT-200N: a% of the accumulator (or of itself with no pending op).
        lastCe = false
        val base = acc ?: current()
        val result = base * current() / 100.0
        display = format(result); fresh = true
    }

    fun sqrt() {
        lastCe = false
        val v = current()
        display = if (v < 0) "Error" else format(sqrt(v))
        fresh = true
    }

    /** C/CE: first press clears the current entry; a second clears everything. */
    fun clearCe() {
        if (lastCe) { acc = null; pending = null; memory = memory; }
        display = "0"; fresh = true
        lastCe = !lastCe
    }

    fun memPlus() { memory += current(); hasMemory = memory != 0.0; fresh = true }
    fun memMinus() { memory -= current(); hasMemory = memory != 0.0; fresh = true }

    /** MRC: recall memory; a second press clears it. */
    fun memRecall() {
        if (display == format(memory) && !fresh) { memory = 0.0; hasMemory = false; display = "0" }
        else display = format(memory)
        fresh = true
    }

    private fun apply(a: Double, b: Double, o: Char): Double = when (o) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    private fun format(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Error"
        return if (d == d.toLong().toDouble()) d.toLong().toString()
        else d.toString()
    }
}
