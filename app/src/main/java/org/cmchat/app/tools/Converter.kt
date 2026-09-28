package org.cmchat.app.tools

/**
 * Dead-simple offline unit converter. Each unit maps to a base factor within
 * its category; conversion is value * from / to. Returns null across
 * categories or for unknown units.
 */
object Converter {

    data class Unit(val symbol: String, val category: String, val toBase: Double)

    val units: List<Unit> = listOf(
        // length -> metre
        Unit("nm", "length", 1e-9),
        Unit("mm", "length", 1e-3),
        Unit("cm", "length", 1e-2),
        Unit("m", "length", 1.0),
        Unit("km", "length", 1000.0),
        Unit("mi", "length", 1609.344),
        // volume -> litre
        Unit("mL", "volume", 1e-3),
        Unit("L", "volume", 1.0),
        Unit("m3", "volume", 1000.0),
        Unit("gal", "volume", 3.785411784),
        // mass -> gram
        Unit("g", "mass", 1.0),
        Unit("kg", "mass", 1000.0),
        Unit("lb", "mass", 453.59237),
    )

    private val bySymbol = units.associateBy { it.symbol }

    fun convert(value: Double, from: String, to: String): Double? {
        val f = bySymbol[from] ?: return null
        val t = bySymbol[to] ?: return null
        if (f.category != t.category) return null
        return value * f.toBase / t.toBase
    }

    fun unitsFor(category: String): List<Unit> = units.filter { it.category == category }
    fun categories(): List<String> = units.map { it.category }.distinct()
}
