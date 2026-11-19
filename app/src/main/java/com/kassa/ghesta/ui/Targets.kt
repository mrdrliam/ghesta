package com.kassa.ghesta.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Banks
import com.kassa.ghesta.data.PayTarget
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.TargetType
import com.kassa.ghesta.data.digitsOnly
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.ui.theme.G
import kotlinx.coroutines.delay
import java.util.UUID

// ====================================================================== draft (editing state)

/** Editable copy of a [PayTarget]. [number] holds only what the user types: card digits, Sheba digits after "IR", or account text. */
data class TargetDraft(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: TargetType = TargetType.CARD,
    val number: String = "",
    val holder: String = "",
    val amount: String = "",
)

private val typeLabels = listOf("کارت", "شبا", "حساب")
private val types = listOf(TargetType.CARD, TargetType.SHEBA, TargetType.ACCOUNT)

private fun typeName(t: TargetType) = typeLabels[types.indexOf(t)]

/** Keeps only the characters that make sense for [type] (accepts Persian digits and a pasted "IR" prefix). */
fun cleanNumber(type: TargetType, raw: String): String = when (type) {
    TargetType.CARD -> raw.digitsOnly().take(16)
    TargetType.SHEBA -> raw.trim().removePrefix("IR").removePrefix("ir").removePrefix("Ir").removePrefix("iR").digitsOnly().take(24)
    TargetType.ACCOUNT -> buildString { for (ch in raw) if (ch == '-') append(ch) else append(ch.toString().digitsOnly()) }.take(24)
}

/** Message that blocks saving, or null. */
fun TargetDraft.error(): String? {
    if (number.isBlank()) return null
    return when (type) {
        TargetType.CARD -> if (number.length != 16) "شماره کارت باید ۱۶ رقم باشد" else null
        TargetType.SHEBA -> if (number.length != 24) "شماره شبا باید ۲۴ رقم بعد از IR باشد" else null
        TargetType.ACCOUNT -> if (number.count { it.isDigit() } < 5) "شماره حساب کوتاه است" else null
    }
}

/** Gentle hint shown while typing (never blocks saving). */
private fun TargetDraft.hint(): Pair<String, Boolean>? = when {
    number.isBlank() -> null
    type == TargetType.CARD && number.length == 16 && !Banks.luhnValid(number) ->
        "این شماره کارت درست به نظر نمی‌رسد؛ دوباره بررسی کنید" to true
    type == TargetType.CARD -> Banks.cardBank(number)?.let { it to false }
    type == TargetType.SHEBA && number.length == 24 && !Banks.shebaValid("IR$number") ->
        "این شماره شبا درست به نظر نمی‌رسد؛ دوباره بررسی کنید" to true
    type == TargetType.SHEBA -> Banks.shebaBank(number)?.let { it to false }
    else -> null
}

fun List<TargetDraft>.firstError(): String? = firstNotNullOfOrNull { it.error() }

fun List<TargetDraft>.toTargets(): List<PayTarget> = filter { it.number.isNotBlank() }.map {
    PayTarget(
        id = it.id, type = it.type,
        number = if (it.type == TargetType.SHEBA) "IR${it.number}" else it.number,
        holder = it.holder.trim(), amount = it.amount.toLongOrNull() ?: 0L,
    )
}

fun List<PayTarget>.toDrafts(): List<TargetDraft> = map {
    TargetDraft(
        id = it.id, type = it.type,
        number = if (it.type == TargetType.SHEBA) it.number.removePrefix("IR") else it.number,
        holder = it.holder, amount = if (it.amount > 0) it.amount.toString() else "",
    )
}

// ====================================================================== formatting

/** 6037997000000001 -> "6037 9970 0000 0001"; IR + 24 digits -> "IR12 3456 …". */
fun groupNumber(type: TargetType, number: String): String = when (type) {
    TargetType.CARD -> number.chunked(4).joinToString(" ")
    TargetType.SHEBA -> {
        val d = number.removePrefix("IR")
        "IR" + d.take(2) + (if (d.length > 2) " " + d.drop(2).chunked(4).joinToString(" ") else "")
    }
    TargetType.ACCOUNT -> number
}

private class NumberTransformation(private val type: TargetType) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val n = raw.length
        val sb = StringBuilder()
        val o2t = IntArray(n + 1)
        if (type == TargetType.SHEBA) sb.append("IR")
        for (i in 0 until n) {
            val sep = when (type) {
                TargetType.CARD -> i > 0 && i % 4 == 0
                TargetType.SHEBA -> i == 2 || (i > 2 && (i - 2) % 4 == 0)
                TargetType.ACCOUNT -> false
            }
            if (sep) sb.append(' ')
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

fun copyText(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun CopyIcon(tint: Color, size: Int = 18) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.minDimension
        val w = s * 0.09f
        val r = CornerRadius(s * 0.14f)
        val box = Size(s * 0.58f, s * 0.64f)
        drawRoundRect(tint, Offset(s * 0.30f, s * 0.04f), box, r, style = Stroke(w))
        drawRoundRect(tint, Offset(s * 0.10f, s * 0.30f), box, r, style = Stroke(w))
    }
}

// ====================================================================== editor

/** "Pay to" section of the forms: any number of cards / Sheba / account numbers, each with an optional amount. */
@Composable
fun TargetsEditor(drafts: SnapshotStateList<TargetDraft>, unit: String, tried: Boolean) {
    val c = G.c
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T("پرداخت به (اختیاری)", 16, FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            T(
                "شماره کارت، شبا یا حساب را بنویسید تا موقع پرداخت همین‌جا ببینید و کپی کنید. اگر چند نفر باید پول بگیرند، چند شماره اضافه کنید.",
                12, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        drafts.forEachIndexed { i, d ->
            key(d.id) { TargetEditCard(d, tried, unit, onChange = { drafts[i] = it }, onDelete = { drafts.removeAt(i) }) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable { drafts.add(TargetDraft()) }.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = c.gold, modifier = Modifier.size(20.dp))
            T(if (drafts.isEmpty()) "افزودن شماره کارت، شبا یا حساب" else "افزودن شماره‌ی دیگر", 14, FontWeight.SemiBold, c.gold, Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun TargetEditCard(d: TargetDraft, tried: Boolean, unit: String, onChange: (TargetDraft) -> Unit, onDelete: () -> Unit) {
    val c = G.c
    val err = d.error()
    val hint = d.hint()
    GCard(bg = c.surface, border = c.line, radius = 18) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Segmented(typeLabels, types.indexOf(d.type), { idx ->
                        val t = types[idx]
                        onChange(d.copy(type = t, number = cleanNumber(t, d.number)))
                    })
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.dangerBg).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, "حذف", tint = c.danger, modifier = Modifier.size(20.dp))
                }
            }
            GField(
                label = when (d.type) {
                    TargetType.CARD -> "شماره کارت"
                    TargetType.SHEBA -> "شماره شبا"
                    TargetType.ACCOUNT -> "شماره حساب"
                },
                value = d.number,
                onValueChange = { onChange(d.copy(number = cleanNumber(d.type, it))) },
                placeholder = when (d.type) {
                    TargetType.CARD -> "۶۰۳۷ ۹۹۱۲ ۳۴۵۶ ۷۸۹۰"
                    TargetType.SHEBA -> "IR۱۲ ۳۴۵۶ …"
                    TargetType.ACCOUNT -> "شماره حساب"
                },
                keyboardType = if (d.type == TargetType.ACCOUNT) KeyboardType.Phone else KeyboardType.Number,
                transformation = NumberTransformation(d.type),
                ltr = true,
                error = tried && err != null || hint?.second == true,
                hint = if (tried && err != null) err else hint?.first,
            )
            GField(
                "نام صاحب حساب (اختیاری)", d.holder, { onChange(d.copy(holder = it)) },
                placeholder = "مثلاً علی رضایی",
            )
            MoneyField(
                "مبلغ واریز به این شماره (اختیاری)", d.amount,
                { onChange(d.copy(amount = it)) }, unit,
            )
        }
    }
}

// ====================================================================== display

/** The saved destinations of an item, each with copy buttons. */
@Composable
fun TargetsList(targets: List<PayTarget>, onCopied: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = G.c
    val ctx = LocalContext.current
    val unit = Store.settings.unit
    // brief "copied" feedback right on the row (a toast would hide behind a bottom sheet)
    var flash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(1400)
            flash = null
        }
    }
    Column(modifier) {
        targets.forEachIndexed { i, t ->
            if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
            val bank = when (t.type) {
                TargetType.CARD -> Banks.cardBank(t.number)
                TargetType.SHEBA -> Banks.shebaBank(t.number.removePrefix("IR"))
                TargetType.ACCOUNT -> null
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.gold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AccountBox, null, tint = c.gold, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    T(
                        t.holder.ifBlank { bank ?: typeName(t.type) }, 14, FontWeight.SemiBold, maxLines = 1,
                    )
                    val sub = listOfNotNull(if (t.holder.isNotBlank()) bank else null, typeName(t.type)).joinToString(" · ")
                    T(sub, 11, color = c.muted, maxLines = 1)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val grouped = groupNumber(t.type, t.number)
                        // a Sheba is too long for one line: break it after the 4th group so it never leaves a stray last digit
                        val shown = if (t.type == TargetType.SHEBA) grouped.split(" ").let { it.take(4).joinToString(" ") + "\n" + it.drop(4).joinToString(" ") } else grouped
                        T(shown.trimEnd().fa(), 13, FontWeight.Medium, c.ink2, maxLines = if (t.type == TargetType.SHEBA) 2 else 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (t.amount > 0) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                copyText(ctx, "amount", t.amount.toString())
                                flash = t.id + "a"
                                onCopied("مبلغ ${fmtNum(t.amount)} کپی شد")
                            }.padding(horizontal = 4.dp),
                        ) {
                            T(fmtNum(t.amount), 15, FontWeight.Bold, if (flash == t.id + "a") c.ok else c.ink, maxLines = 1)
                            T(if (flash == t.id + "a") "کپی شد" else unit, 10, FontWeight.Medium, if (flash == t.id + "a") c.ok else c.muted)
                        }
                    }
                }
                val copied = flash == t.id + "n"
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (copied) c.okBg else Color.Transparent)
                        .border(1.dp, if (copied) c.ok.copy(alpha = 0.5f) else c.line, RoundedCornerShape(12.dp)).clickable {
                            copyText(ctx, "number", t.number)
                            flash = t.id + "n"
                            onCopied("${typeName(t.type)} کپی شد")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (copied) Icon(Icons.Filled.Check, "کپی شد", tint = c.ok, modifier = Modifier.size(20.dp)) else CopyIcon(c.ink2)
                }
            }
        }
    }
}

/** Section card used on the detail page. */
@Composable
fun TargetsCard(targets: List<PayTarget>, title: String, subtitle: String?, onCopied: (String) -> Unit) {
    val c = G.c
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            T(title, 16, FontWeight.Bold)
            if (subtitle != null) T(subtitle, 12, color = c.muted)
        }
        GCard { TargetsList(targets, onCopied) }
    }
}
