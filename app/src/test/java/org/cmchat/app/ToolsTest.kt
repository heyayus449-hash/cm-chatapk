package org.cmchat.app

import org.cmchat.app.tools.Calculator
import org.cmchat.app.tools.Converter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToolsTest {

    private fun close(a: Double, b: Double) = assertTrue("$a vs $b", abs(a - b) < 1e-6)

    @Test
    fun calculator_precedence_and_parens() {
        close(7.0, Calculator.eval("1 + 2 * 3")!!)
        close(9.0, Calculator.eval("(1 + 2) * 3")!!)
        close(2.5, Calculator.eval("5 / 2")!!)
        close(-1.0, Calculator.eval("-3 + 2")!!)
        close(3.14, Calculator.eval("3.14")!!)
    }

    @Test
    fun calculator_rejects_bad_input() {
        assertNull(Calculator.eval("1 +"))
        assertNull(Calculator.eval("(1 + 2"))
        assertNull(Calculator.eval("1 / 0"))
        assertNull(Calculator.eval("abc"))
    }

    @Test
    fun converter_within_and_across_categories() {
        close(1000.0, Converter.convert(1.0, "km", "m")!!)
        close(1.609344, Converter.convert(1.0, "mi", "km")!!)
        close(1000.0, Converter.convert(1.0, "L", "mL")!!)
        assertNull(Converter.convert(1.0, "km", "L"))  // cross-category
        assertNull(Converter.convert(1.0, "km", "xx"))  // unknown unit
    }
}
