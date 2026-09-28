package org.cmchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.tools.Calculator
import org.cmchat.app.tools.Converter
import org.cmchat.app.tools.ToolsState
import org.cmchat.app.ui.theme.*

@Composable
fun ToolsScreen(which: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CmBackground)) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("‹ Back", color = CmBlue, fontFamily = Nunito, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
            Text(which.replaceFirstChar { it.uppercase() }, color = CmText, fontFamily = Nunito,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
        when (which) {
            "calculator" -> CalculatorUi()
            "notes" -> NotesUi()
            "converter" -> ConverterUi()
        }
    }
}

@Composable
private fun CalculatorUi() {
    var expr by remember { mutableStateOf("") }
    val result = remember(expr) { Calculator.eval(expr) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(expr, "e.g. (12 + 3) * 4") { expr = it }
        Text(result?.let { trimNum(it) } ?: "—", color = CmBlue, fontFamily = Nunito,
            fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotesUi() {
    val notes by ToolsState.notes.collectAsState()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RAM only — cleared when the app closes.", color = CmTextDim, fontFamily = Nunito, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 200.dp)
            .clip(RoundedCornerShape(12.dp)).background(CmCard).padding(12.dp)) {
            BasicTextField(
                value = notes, onValueChange = { ToolsState.notes.value = it },
                textStyle = TextStyle(color = CmText, fontFamily = Nunito, fontSize = 15.sp),
                cursorBrush = SolidColor(CmBlue), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConverterUi() {
    var value by remember { mutableStateOf("1") }
    var from by remember { mutableStateOf("km") }
    var to by remember { mutableStateOf("mi") }
    val out = remember(value, from, to) {
        value.toDoubleOrNull()?.let { Converter.convert(it, from, to) }
    }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(value, "value") { value = it }
        UnitRow("From", from) { from = it }
        UnitRow("To", to) { to = it }
        Text(out?.let { trimNum(it) } ?: "—", color = CmBlue, fontFamily = Nunito,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UnitRow(label: String, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = CmTextDim, fontFamily = Nunito, fontSize = 12.sp)
        FlowLikeRow(Converter.units.map { it.symbol }, selected, onSelect)
    }
}

@Composable
private fun FlowLikeRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    // simple wrapping via chunks of 6
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { s ->
                    val sel = s == selected
                    Box(Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (sel) CmBlue else CmCard).clickable { onSelect(s) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(s, color = if (sel) CmBackground else CmTextDim,
                            fontFamily = Nunito, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CmCard).padding(14.dp)) {
        if (value.isEmpty()) Text(hint, color = CmTextDim, fontFamily = Nunito, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = CmText, fontFamily = Nunito, fontSize = 16.sp),
            cursorBrush = SolidColor(CmBlue), modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun trimNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.6f".format(d).trimEnd('0').trimEnd('.')
