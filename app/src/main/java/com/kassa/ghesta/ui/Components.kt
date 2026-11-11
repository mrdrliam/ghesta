package com.kassa.ghesta.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Info
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.ItemState
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.amountWords
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.digitsOnly
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.relLabel
import com.kassa.ghesta.ui.theme.G
import com.kassa.ghesta.ui.theme.Palette
import com.kassa.ghesta.ui.theme.Vazir
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

// ------------------------------------------------------------------ text

@Composable
fun T(
    text: String,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    color: Color = G.c.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = Vazir,
        lineHeight = (size * 1.6f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

enum class Tone { Danger, Warn, Ok, Neutral, Gold }

@Composable
fun toneFg(t: Tone): Color = when (t) {
    Tone.Danger -> G.c.danger
    Tone.Warn -> G.c.warn
    Tone.Ok -> G.c.ok
    Tone.Neutral -> G.c.ink2
    Tone.Gold -> G.c.gold
}

@Composable
fun toneBg(t: Tone): Color = when (t) {
    Tone.Danger -> G.c.dangerBg
    Tone.Warn -> G.c.warnBg
    Tone.Ok -> G.c.okBg
    Tone.Neutral -> G.c.surface2
    Tone.Gold -> G.c.gold.copy(alpha = 0.14f)
}

@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(toneBg(tone))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { T(text, 11, FontWeight.SemiBold, toneFg(tone), maxLines = 1) }
}

// ------------------------------------------------------------------ surfaces

@Composable
fun GCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bg: Color = G.c.surface,
    border: Color = G.c.line,
    radius: Int = 22,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = G.c
    val shape = RoundedCornerShape(radius.dp)
    Column(
        modifier
            .then(
                if (!c.dark) Modifier.shadow(3.dp, shape, ambientColor = Color(0x2214272B), spotColor = Color(0x2214272B))
                else Modifier
            )
            .clip(shape)
            .background(bg.compositeOver(c.surface))
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(G.c.line))
}

fun Modifier.heroBackground(palette: Palette, radius: Dp = 28.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .drawBehind {
        drawRect(
            Brush.linearGradient(
                listOf(palette.teal2, palette.teal, palette.tealDeep),
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
            )
        )
        // soft gold glow, top corner
        drawRect(
            Brush.radialGradient(
                listOf(palette.gold2.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.9f, 0f),
                radius = size.width * 0.75f,
            )
        )
        // 8-point star lattice
        val cell = 52.dp.toPx()
        val side = cell * 0.56f
        val line = palette.gold2.copy(alpha = 0.10f)
        val stroke = Stroke(width = 1.dp.toPx())
        var y = cell / 2
        while (y < size.height + cell) {
            var x = cell / 2
            while (x < size.width + cell) {
                val tl = Offset(x - side / 2, y - side / 2)
                drawRect(line, tl, Size(side, side), style = stroke)
                rotate(45f, Offset(x, y)) { drawRect(line, tl, Size(side, side), style = stroke) }
                x += cell
            }
            y += cell
        }
        // fine inner border
        drawRoundRect(
            palette.gold2.copy(alpha = 0.28f),
            topLeft = Offset(0.5f, 0.5f),
            size = Size(size.width - 1f, size.height - 1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            style = Stroke(1.dp.toPx()),
        )
    }

// ------------------------------------------------------------------ progress

@Composable
fun Ring(
    progress: Float,
    size: Dp,
    stroke: Dp,
    track: Color,
    color: Color,
    content: @Composable () -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 0.004f) drawArc(color, -90f, 360f * p, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun GProgress(progress: Float, modifier: Modifier = Modifier, color: Color = G.c.ok, height: Dp = 6.dp, track: Color = G.c.surface2) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(RoundedCornerShape(50)).background(color))
    }
}

// ------------------------------------------------------------------ icons / badges

fun kindIcon(kind: Kind): ImageVector = if (kind == Kind.INST) Icons.Filled.DateRange else Icons.Filled.Person

@Composable
fun KindIcon(kind: Kind, tone: Tone, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(toneBg(tone)),
        contentAlignment = Alignment.Center,
    ) { Icon(kindIcon(kind), null, tint = toneFg(tone), modifier = Modifier.size(size * 0.5f)) }
}

fun stateTone(s: ItemState): Tone = when (s) {
    ItemState.DONE -> Tone.Ok
    ItemState.OVERDUE -> Tone.Danger
    ItemState.SOON -> Tone.Warn
    ItemState.OK -> Tone.Neutral
}

@Composable
fun StatePill(item: Item, info: Info, today: LocalDate) {
    val text = when (info.state) {
        ItemState.DONE -> "تسویه شد"
        ItemState.OVERDUE -> if (item.kind == Kind.INST) "${info.overdue.size.fa()} قسط عقب‌افتاده" else "عقب‌افتاده"
        ItemState.SOON, ItemState.OK -> info.next?.let { relLabel(daysBetween(today, it.date)) } ?: "بدون سررسید"
    }
    Pill(text, stateTone(info.state))
}

@Composable
fun relTone(diff: Long): Tone = when {
    diff < 0 -> Tone.Danger
    diff <= 3 -> Tone.Warn
    else -> Tone.Neutral
}

@Composable
fun CheckCircle(checked: Boolean, onClick: () -> Unit, size: Dp = 34.dp, contentDescription: String? = null) {
    val c = G.c
    val bg by animateColorAsState(if (checked) c.ok else Color.Transparent, tween(220), label = "chk")
    val border by animateColorAsState(if (checked) c.ok else c.muted.copy(alpha = 0.55f), tween(220), label = "chkb")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check, contentDescription,
            tint = if (checked) Color.White else c.muted.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

// ------------------------------------------------------------------ rows

@Composable
fun DueRow(due: Due, today: LocalDate, onOpen: () -> Unit, onPay: () -> Unit) {
    val c = G.c
    val diff = daysBetween(today, due.date)
    val tone = relTone(diff)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindIcon(due.item.kind, if (diff < 0) Tone.Danger else Tone.Gold)
        Column(Modifier.weight(1f)) {
            T(due.item.title, 15, FontWeight.SemiBold, maxLines = 1)
            T("${due.label} · ${fmtDate(due.date, year = false)}", 12, color = c.muted, maxLines = 1)
            T(relLabel(diff), 12, FontWeight.SemiBold, toneFg(if (tone == Tone.Neutral) Tone.Neutral else tone), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            T(fmtNum(due.amount), 15, FontWeight.SemiBold, maxLines = 1)
            T(com.kassa.ghesta.data.Store.settings.unit, 11, color = c.muted)
        }
        CheckCircle(false, onPay, 38.dp, "پرداخت شد")
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        T(title, 16, FontWeight.Bold)
        if (action != null) {
            T(action, 13, FontWeight.SemiBold, G.c.gold, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

// ------------------------------------------------------------------ buttons

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Tone? = null,
    onClick: () -> Unit,
) {
    val c = G.c
    val bg = if (tone != null) toneBg(tone) else c.primary
    val fg = if (tone != null) toneFg(tone) else c.onPrimary
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) bg else c.surface2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 16, FontWeight.Bold, if (enabled) fg else c.muted) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = G.c.ink2) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, G.c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 14, FontWeight.SemiBold, color) }
}

@Composable
fun GChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null, danger: Boolean = false) {
    val c = G.c
    val bg by animateColorAsState(if (selected) (if (danger) c.danger else c.primary) else c.surface, tween(180), label = "chip")
    val fg = if (selected) (if (danger) Color.White else c.onPrimary) else c.ink2
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        T(text, 13, FontWeight.SemiBold, fg, maxLines = 1)
        if (count != null && count > 0) {
            Box(
                Modifier.clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.25f) else if (danger) c.dangerBg else c.surface2).padding(horizontal = 6.dp),
            ) { T(count.fa(), 11, FontWeight.Bold, if (selected) fg else if (danger) c.danger else c.ink2) }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = G.c
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) c.surface else Color.Transparent, tween(180), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { T(label, 14, if (sel) FontWeight.Bold else FontWeight.Medium, if (sel) c.ink else c.muted) }
        }
    }
}

// ------------------------------------------------------------------ fields

class DigitsTransformation(private val separators: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val n = raw.length
        val sb = StringBuilder()
        val o2t = IntArray(n + 1)
        for (i in 0 until n) {
            if (separators && i > 0 && (n - i) % 3 == 0) sb.append('٬')
            o2t[i] = sb.length
            sb.append(raw[i])
        }
        o2t[n] = sb.length
        val out = sb.toString().fa()
        val t2o = IntArray(out.length + 1)
        for (t in 0..out.length) {
            var count = 0
            for (i in 0 until n) if (o2t[i] < t) count++
            t2o[t] = count
        }
        return TransformedText(
            AnnotatedString(out),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, n)]
                override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, out.length)]
            },
        )
    }
}

@Composable
fun GField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: String? = null,
    error: Boolean = false,
    hint: String? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val c = G.c
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(label, 12, FontWeight.Medium, c.ink2, Modifier.padding(horizontal = 4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface2)
                .border(1.dp, if (error) c.danger else Color.Transparent, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(fontFamily = Vazir, fontSize = 16.sp, color = c.ink, lineHeight = 24.sp),
                cursorBrush = SolidColor(c.gold),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                visualTransformation = transformation,
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) T(placeholder, 16, color = c.muted.copy(alpha = 0.8f))
                            inner()
                        }
                        if (trailing != null) T(trailing, 12, color = c.muted, modifier = Modifier.padding(start = 8.dp))
                    }
                },
            )
        }
        if (hint != null) T(hint, 12, FontWeight.Medium, if (error) c.danger else c.gold, Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun MoneyField(
    label: String,
    digits: String,
    onChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    optionalHint: String? = null,
) {
    val words = amountWords(digits.toLongOrNull() ?: 0L, unit)
    GField(
        label = label,
        value = digits,
        onValueChange = { onChange(it.digitsOnly().trimStart('0').take(13)) },
        modifier = modifier,
        placeholder = "۰",
        keyboardType = KeyboardType.Number,
        transformation = remember { DigitsTransformation(true) },
        trailing = unit,
        error = error,
        hint = if (error) "مبلغ را وارد کنید" else words.ifEmpty { optionalHint },
    )
}

@Composable
fun IntField(
    label: String,
    digits: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    error: Boolean = false,
    hint: String? = null,
    max: Int = 999,
) {
    GField(
        label = label,
        value = digits,
        onValueChange = { onChange(it.digitsOnly().trimStart('0').take(4).let { s -> if ((s.toIntOrNull() ?: 0) > max) max.toString() else s }) },
        modifier = modifier,
        placeholder = "۰",
        keyboardType = KeyboardType.Number,
        transformation = remember { DigitsTransformation(false) },
        trailing = trailing,
        error = error,
        hint = hint,
    )
}

@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "انتخاب تاریخ",
    hint: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val c = G.c
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(label, 12, FontWeight.Medium, c.ink2, Modifier.padding(horizontal = 4.dp))
        Row(
            Modifier.fillMaxWidth().clip(shape).background(c.surface2).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DateRange, null, tint = c.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            T(
                if (date != null) fmtDate(date, weekday = true) else placeholder, 16,
                color = if (date != null) c.ink else c.muted.copy(alpha = 0.8f), modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (date != null && onClear != null) {
                T("حذف", 13, FontWeight.SemiBold, c.danger, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(6.dp))
            }
        }
        if (hint != null) T(hint, 12, FontWeight.Medium, c.gold, Modifier.padding(horizontal = 4.dp))
    }
}

// ------------------------------------------------------------------ sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val c = G.c
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(50)).background(c.muted.copy(alpha = 0.4f)))
        },
    ) { content(close) }
}

// ------------------------------------------------------------------ celebration

object Celebrate {
    var tick by mutableIntStateOf(0)
        private set

    fun burst() {
        tick++
    }
}

private class Particle(
    val angle: Float, val speed: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val delay: Float,
)

@Composable
fun ConfettiOverlay() {
    val t = Celebrate.tick
    if (t == 0) return
    key(t) { ConfettiBurst() }
}

@Composable
private fun ConfettiBurst() {
    val c = G.c
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2000, easing = LinearEasing)) }
    val colors = remember(c) { listOf(c.gold2, c.gold, c.ok, c.teal2, c.danger, Color.White) }
    val particles = remember {
        List(60) {
            Particle(
                angle = Random.nextFloat() * 120f + 210f, // upward fan
                speed = Random.nextFloat() * 900f + 700f,
                spin = Random.nextFloat() * 720f - 360f,
                color = colors[Random.nextInt(colors.size)],
                w = Random.nextFloat() * 8f + 6f,
                h = Random.nextFloat() * 5f + 8f,
                delay = Random.nextFloat() * 0.08f,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val origin = Offset(size.width / 2f, size.height * 0.62f)
        for (pt in particles) {
            val tt = ((p - pt.delay) * 2.0f).coerceAtLeast(0f)
            if (tt <= 0f) continue
            val rad = Math.toRadians(pt.angle.toDouble())
            val x = origin.x + (Math.cos(rad) * pt.speed * tt).toFloat()
            val y = origin.y + (Math.sin(rad) * pt.speed * tt).toFloat() + 1500f * tt * tt
            val alpha = (1f - (p - 0.55f).coerceAtLeast(0f) / 0.45f).coerceIn(0f, 1f)
            rotate(pt.spin * tt, Offset(x, y)) {
                drawRect(pt.color.copy(alpha = alpha), Offset(x - pt.w / 2, y - pt.h / 2), Size(pt.w, pt.h))
            }
        }
    }
}
