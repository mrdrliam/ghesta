package com.kassa.ghesta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.CalcMode
import com.kassa.ghesta.data.CalcResult
import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.InstallmentCalc
import com.kassa.ghesta.data.digitsOnly
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.info
import com.kassa.ghesta.data.remaining
import com.kassa.ghesta.notify.Reminders
import com.kassa.ghesta.ui.theme.G
import java.time.LocalDate

@Composable
private fun SheetTitle(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) { T(text, 20, FontWeight.Bold) }
}

@Composable
private fun SheetFooter(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 14.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

private fun Item.firstUnpaidAmount(): Long = installments.firstOrNull { it.paidAt == null }?.amount ?: installments.lastOrNull()?.amount ?: 0

// ====================================================================== add

private val stepLabels = listOf("ماهانه" to 1, "دو ماهه" to 2, "سه‌ماهه" to 3, "شش‌ماهه" to 6, "سالانه" to 12)

/** Calculator modes: what the user knows -> what we work out. */
private class ModeDef(val label: String, val hint: String, val mode: CalcMode)

private val calcModes = listOf(
    ModeDef("مبلغ قسط + تعداد", "مبلغ هر قسط و تعداد را بدهید؛ تاریخ آخرین قسط و مجموع را حساب می‌کنیم.", CalcMode.AMOUNT_COUNT),
    ModeDef("مبلغ قسط + تاریخ پایان", "مبلغ قسط و تاریخ آخرین قسط را بدهید؛ تعداد اقساط را حساب می‌کنیم.", CalcMode.AMOUNT_END),
    ModeDef("مبلغ کل + تعداد", "مجموع بازپرداخت و تعداد را بدهید؛ مبلغ هر قسط را حساب می‌کنیم.", CalcMode.TOTAL_COUNT),
    ModeDef("مبلغ کل + مبلغ قسط", "مجموع بازپرداخت و مبلغ قسط را بدهید؛ تعداد اقساط و تاریخ پایان را حساب می‌کنیم.", CalcMode.TOTAL_AMOUNT),
    ModeDef("مبلغ کل + تاریخ پایان", "مجموع بازپرداخت و تاریخ آخرین قسط را بدهید؛ تعداد و مبلغ هر قسط را حساب می‌کنیم.", CalcMode.TOTAL_END),
    ModeDef("وام با سود بانکی", "مبلغ وام، نرخ سود سالانه و تعداد اقساط را بدهید؛ قسط ماهانه و مجموع سود را حساب می‌کنیم (تقریبی).", CalcMode.LOAN),
)

private val quickCounts = listOf(6, 12, 18, 24, 36, 48, 60)

/** Keeps digits and at most one decimal point (accepts Persian digits and the Persian decimal separator). */
private fun cleanDecimal(s: String): String {
    val sb = StringBuilder()
    var dot = false
    for (ch in s) {
        val d = ch.toString().digitsOnly()
        if (d.isNotEmpty()) sb.append(d)
        else if ((ch == '.' || ch == '٫' || ch == ',') && !dot && sb.isNotEmpty()) {
            sb.append('.')
            dot = true
        }
    }
    return sb.toString().take(5)
}

@Composable
fun AddSheet(app: AppState, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    GSheet(onDismiss) { close ->
        val c = G.c
        val unit = Store.settings.unit
        val today = Store.today

        var kind by remember { mutableIntStateOf(0) }
        var tried by remember { mutableStateOf(false) }
        var pick by remember { mutableStateOf<String?>(null) }

        // installment
        var title by remember { mutableStateOf("") }
        var party by remember { mutableStateOf("") }
        var mode by remember { mutableIntStateOf(0) }
        var amount by remember { mutableStateOf("") }
        var total by remember { mutableStateOf("") }
        var count by remember { mutableStateOf("12") }
        var rate by remember { mutableStateOf("") }
        var endDate by remember { mutableStateOf<LocalDate?>(null) }
        var paidCount by remember { mutableStateOf("0") }
        var first by remember { mutableStateOf<LocalDate>(today) }
        var step by remember { mutableIntStateOf(1) }

        // debt
        var dir by remember { mutableIntStateOf(0) }
        var dTitle by remember { mutableStateOf("") }
        var dParty by remember { mutableStateOf("") }
        var dTotal by remember { mutableStateOf("") }
        var dPaid by remember { mutableStateOf("") }
        var dDue by remember { mutableStateOf<LocalDate?>(null) }
        var dNote by remember { mutableStateOf("") }

        val m = calcModes[mode]
        val countN = count.toIntOrNull() ?: 0
        val paidN = paidCount.toIntOrNull() ?: 0
        val amountN = amount.toLongOrNull() ?: 0L
        val totalN = total.toLongOrNull() ?: 0L
        val rateD = rate.toDoubleOrNull()
        val calc = remember(mode, first, step, amountN, countN, totalN, endDate, rateD) {
            InstallmentCalc.compute(m.mode, first, step, amountN, countN, totalN, endDate, rateD)
        }
        val res = calc.result
        val instValid = title.isNotBlank() && res != null && paidN <= res.count

        val dTotalN = dTotal.toLongOrNull() ?: 0L
        val dPaidN = dPaid.toLongOrNull() ?: 0L
        val debtValid = dTitle.isNotBlank() && dTotalN > 0 && dPaidN <= dTotalN

        SheetTitle("مورد جدید")
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Segmented(listOf("قسط / وام", "بدهی / طلب"), kind, { kind = it; tried = false })
        }

        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (kind == 0) {
                GField("عنوان", title, { title = it }, placeholder = "مثلاً وام خرید خانه", error = tried && title.isBlank(), hint = if (tried && title.isBlank()) "عنوان را بنویسید" else null)
                GField("بانک / فروشگاه (اختیاری)", party, { party = it }, placeholder = "مثلاً بانک ملی")

                // ---- calculator helper
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.gold.copy(alpha = 0.10f)).padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    T("کمک‌حساب: از چه چیزی شروع کنیم؟", 14, FontWeight.Bold, c.gold, Modifier.padding(horizontal = 16.dp))
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(calcModes.size) { i -> GChip(calcModes[i].label, mode == i, { mode = i }) }
                    }
                    T(m.hint, 12, color = c.ink2, modifier = Modifier.padding(horizontal = 16.dp))
                }

                val needsAmount = m.mode == CalcMode.AMOUNT_COUNT || m.mode == CalcMode.AMOUNT_END || m.mode == CalcMode.TOTAL_AMOUNT
                val needsTotal = m.mode == CalcMode.TOTAL_COUNT || m.mode == CalcMode.TOTAL_AMOUNT || m.mode == CalcMode.TOTAL_END || m.mode == CalcMode.LOAN
                val needsCount = m.mode == CalcMode.AMOUNT_COUNT || m.mode == CalcMode.TOTAL_COUNT || m.mode == CalcMode.LOAN
                val needsEnd = m.mode == CalcMode.AMOUNT_END || m.mode == CalcMode.TOTAL_END

                if (needsTotal) {
                    MoneyField(
                        if (m.mode == CalcMode.LOAN) "مبلغ وام" else "مجموع بازپرداخت (همه‌ی اقساط با هم)",
                        total, { total = it }, unit, error = tried && totalN <= 0,
                    )
                }
                if (m.mode == CalcMode.LOAN) {
                    GField(
                        "نرخ سود سالانه", rate, { rate = cleanDecimal(it) }, placeholder = "مثلاً ۱۸",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        transformation = remember { DigitsTransformation(false) }, trailing = "٪ در سال",
                        error = tried && rateD == null, hint = if (tried && rateD == null) "نرخ سود را وارد کنید (اگر بدون سود است ۰)" else null,
                    )
                }
                if (needsAmount) MoneyField("مبلغ هر قسط", amount, { amount = it }, unit, error = tried && amountN <= 0)
                if (needsCount) {
                    IntField("تعداد اقساط", count, { count = it }, trailing = "قسط", error = tried && countN !in 1..360, max = 360)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickCounts) { n -> GChip("${n.fa()} قسط", count == n.toString(), { count = n.toString() }) }
                    }
                }

                DateField(
                    "تاریخ اولین قسط", first, { pick = "first" },
                    hint = "اگر وام را مدتی است می‌پردازید، تاریخ اولین قسط واقعی را بزنید و پایین‌تر تعداد پرداخت‌شده‌ها را وارد کنید.",
                )
                if (needsEnd) {
                    DateField(
                        "تاریخ آخرین قسط", endDate, { pick = "end" }, placeholder = "انتخاب تاریخ پایان",
                        hint = calc.error ?: if (tried && endDate == null) "تاریخ پایان را انتخاب کنید" else null,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("فاصله‌ی اقساط", 12, FontWeight.Medium, c.ink2, Modifier.padding(horizontal = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stepLabels) { (label, v) -> GChip(label, step == v, { step = v }) }
                    }
                }

                if (calc.error != null && !needsEnd) T(calc.error, 12, FontWeight.Medium, c.danger)

                if (res != null) {
                    IntField("چند قسط تا الان پرداخت شده؟", paidCount, { paidCount = it }, trailing = "از ${res.count.fa()}", error = paidN > res.count, max = 360)
                    val pastCount = res.dates.count { it.isBefore(today) }
                    if (pastCount > 0 && paidN != pastCount) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.gold.copy(alpha = 0.12f))
                                .clickable { paidCount = pastCount.toString() }.padding(14.dp),
                        ) { T("${pastCount.fa()} قسط تا امروز سررسید شده — همه را پرداخت‌شده حساب کن", 13, FontWeight.SemiBold, c.gold) }
                    }
                    CalcResultCard(m.mode, res, paidN, unit)
                } else {
                    GCard(bg = c.surface2, border = c.line, radius = 18) {
                        T("با پر کردن موارد بالا، نتیجه‌ی محاسبه همین‌جا نشان داده می‌شود.", 13, color = c.muted, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                Segmented(listOf("بدهکارم", "طلبکارم"), dir, { dir = it })
                GField(
                    "عنوان", dTitle, { dTitle = it },
                    placeholder = if (dir == 0) "مثلاً قرض از علی" else "مثلاً طلب از برادر",
                    error = tried && dTitle.isBlank(), hint = if (tried && dTitle.isBlank()) "عنوان را بنویسید" else null,
                )
                GField("نام شخص یا مرکز (اختیاری)", dParty, { dParty = it }, placeholder = "مثلاً علی رضایی")
                MoneyField("مبلغ کل", dTotal, { dTotal = it }, unit, error = tried && dTotalN <= 0)
                MoneyField(
                    if (dir == 0) "تا الان پرداخت کرده‌ام (اختیاری)" else "تا الان دریافت کرده‌ام (اختیاری)",
                    dPaid, { dPaid = it }, unit, error = tried && dPaidN > dTotalN,
                )
                DateField("سررسید (اختیاری)", dDue, { pick = "due" }, placeholder = "بدون سررسید", onClear = { dDue = null })
                GField("یادداشت (اختیاری)", dNote, { dNote = it }, singleLine = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
            }
            Spacer(Modifier.height(4.dp))
        }

        SheetFooter {
            PrimaryButton("ثبت") {
                tried = true
                val wasEmpty = Store.items.isEmpty()
                if (kind == 0 && instValid && res != null) {
                    Store.addInstallments(title, party, "", res.amount, res.count, step, first, paidN, res.lastAmount)
                    app.toast("«${title.trim()}» ثبت شد")
                } else if (kind == 1 && debtValid) {
                    Store.addDebt(dTitle, dParty, dNote, if (dir == 0) Dir.OWE else Dir.OWED, dTotalN, dPaidN, dDue)
                    app.toast("«${dTitle.trim()}» ثبت شد")
                } else return@PrimaryButton
                close()
                if (wasEmpty && !Reminders.canNotify(ctx)) app.requestNotif()
            }
        }

        if (pick != null) {
            JalaliPickerDialog(
                initial = when (pick) {
                    "first" -> first
                    "end" -> endDate ?: Jalali.schedule(first, 12, step).last()
                    else -> dDue
                },
                title = when (pick) {
                    "first" -> "تاریخ اولین قسط"
                    "end" -> "تاریخ آخرین قسط"
                    else -> "تاریخ سررسید"
                },
                onDismiss = { pick = null },
            ) {
                when (pick) {
                    "first" -> first = it
                    "end" -> endDate = it
                    else -> dDue = it
                }
                pick = null
            }
        }
    }
}

@Composable
private fun CalcResultCard(mode: CalcMode, r: CalcResult, paid: Int, unit: String) {
    val c = G.c
    val headline = when (mode) {
        CalcMode.AMOUNT_COUNT -> "آخرین قسط: ${fmtDate(r.last)}"
        CalcMode.AMOUNT_END, CalcMode.TOTAL_AMOUNT -> "${r.count.fa()} قسط"
        CalcMode.TOTAL_COUNT, CalcMode.LOAN -> "هر قسط: ${fmtNum(r.amount)} $unit"
        CalcMode.TOTAL_END -> "${r.count.fa()} قسط × ${fmtNum(r.amount)} $unit"
    }
    Column(
        Modifier.fillMaxWidth().heroBackground(c, 22.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("نتیجه‌ی محاسبه", 12, FontWeight.Medium, Color.White.copy(alpha = 0.65f))
        T(headline, 20, FontWeight.Bold, c.gold2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CalcLine("تعداد اقساط", "${r.count.fa()} قسط")
            CalcLine("مبلغ هر قسط", "${fmtNum(r.amount)} $unit")
            if (r.lastAmount != r.amount) CalcLine("قسط آخر (برای رسیدن به مجموع)", "${fmtNum(r.lastAmount)} $unit")
            CalcLine("مجموع بازپرداخت", "${fmtNum(r.total)} $unit")
            if (r.interest > 0) CalcLine("مجموع سود (تقریبی)", "${fmtNum(r.interest)} $unit")
            CalcLine("اولین قسط", fmtDate(r.first))
            CalcLine("آخرین قسط", fmtDate(r.last))
            if (paid > 0 && paid <= r.count) {
                val remaining = r.total - r.dates.indices.filter { it < paid }.sumOf { if (it == r.count - 1) r.lastAmount else r.amount }
                CalcLine("باقی‌مانده بعد از ${paid.fa()} قسط پرداخت‌شده", "${fmtNum(remaining)} $unit")
            }
        }
        if (r.interest > 0) {
            T("محاسبه با فرمول اقساط مساوی است؛ کارمزد و جریمه‌ی بانک جدا حساب می‌شود.", 11, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun CalcLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        T(label, 13, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
        T(value, 13, FontWeight.Bold, Color.White)
    }
}

// ====================================================================== edit item

@Composable
fun EditItemSheet(item: Item, onDismiss: () -> Unit) {
    GSheet(onDismiss) { close ->
        val unit = Store.settings.unit
        var title by remember { mutableStateOf(item.title) }
        var party by remember { mutableStateOf(item.party) }
        var note by remember { mutableStateOf(item.note) }
        var amount by remember { mutableStateOf(item.firstUnpaidAmount().toString()) }
        var total by remember { mutableStateOf(item.total.toString()) }
        var due by remember { mutableStateOf(item.due) }
        var dir by remember { mutableIntStateOf(if (item.dir == Dir.OWE) 0 else 1) }
        var pick by remember { mutableStateOf(false) }
        var tried by remember { mutableStateOf(false) }

        SheetTitle("ویرایش")
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GField("عنوان", title, { title = it }, error = tried && title.isBlank())
            GField("طرف حساب", party, { party = it })
            if (item.kind == Kind.INST) {
                if (item.installments.any { it.paidAt == null }) {
                    MoneyField("مبلغ قسط‌های پرداخت‌نشده", amount, { amount = it }, unit, error = tried && (amount.toLongOrNull() ?: 0) <= 0)
                    T("قسط‌های پرداخت‌شده بدون تغییر می‌مانند. برای تغییر تاریخ یا مبلغ یک قسط خاص، روی همان قسط بزنید.", 12, color = G.c.muted)
                }
            } else {
                Segmented(listOf("بدهکارم", "طلبکارم"), dir, { dir = it })
                MoneyField("مبلغ کل", total, { total = it }, unit, error = tried && (total.toLongOrNull() ?: 0) <= 0)
                DateField("سررسید", due, { pick = true }, placeholder = "بدون سررسید", onClear = { due = null })
            }
            GField("یادداشت", note, { note = it }, singleLine = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
            Spacer(Modifier.height(4.dp))
        }
        SheetFooter {
            PrimaryButton("ذخیره") {
                tried = true
                if (title.isBlank()) return@PrimaryButton
                if (item.kind == Kind.INST) {
                    val a = amount.toLongOrNull() ?: 0L
                    if (item.installments.any { it.paidAt == null } && a <= 0) return@PrimaryButton
                    Store.updateItem(item.id) { it.copy(title = title.trim(), party = party.trim(), note = note.trim()) }
                    if (item.installments.any { it.paidAt == null } && a != item.firstUnpaidAmount()) Store.setUnpaidAmount(item.id, a)
                } else {
                    val t = total.toLongOrNull() ?: 0L
                    if (t <= 0) return@PrimaryButton
                    Store.updateItem(item.id) {
                        it.copy(title = title.trim(), party = party.trim(), note = note.trim(), total = t, due = due, dir = if (dir == 0) Dir.OWE else Dir.OWED)
                    }
                }
                close()
            }
        }
        if (pick) {
            JalaliPickerDialog(due, "تاریخ سررسید", { pick = false }) { due = it; pick = false }
        }
    }
}

// ====================================================================== edit installment

@Composable
fun InstallmentSheet(item: Item, instId: String, onDismiss: () -> Unit) {
    val inst = item.installments.firstOrNull { it.id == instId }
    if (inst == null) {
        onDismiss()
        return
    }
    val n = item.installments.indexOf(inst) + 1
    GSheet(onDismiss) { close ->
        val unit = Store.settings.unit
        var due by remember { mutableStateOf(inst.due) }
        var amount by remember { mutableStateOf(inst.amount.toString()) }
        var paid by remember { mutableIntStateOf(if (inst.paidAt != null) 1 else 0) }
        var paidAt by remember { mutableStateOf(inst.paidAt ?: Store.today) }
        var pick by remember { mutableStateOf<String?>(null) }
        var confirmDelete by remember { mutableStateOf(false) }
        var tried by remember { mutableStateOf(false) }

        SheetTitle("قسط ${n.fa()} از ${item.installments.size.fa()}")
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DateField("سررسید", due, { pick = "due" })
            MoneyField("مبلغ", amount, { amount = it }, unit, error = tried && (amount.toLongOrNull() ?: 0) <= 0)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("وضعیت", 12, FontWeight.Medium, G.c.ink2, Modifier.padding(horizontal = 4.dp))
                Segmented(listOf("پرداخت‌نشده", "پرداخت شد"), paid, { paid = it })
            }
            if (paid == 1) DateField("تاریخ پرداخت", paidAt, { pick = "paid" })
            Spacer(Modifier.height(4.dp))
        }
        SheetFooter {
            PrimaryButton("ذخیره") {
                tried = true
                val a = amount.toLongOrNull() ?: 0L
                if (a <= 0) return@PrimaryButton
                Store.updateInstallment(item.id, inst.id, due, a, if (paid == 1) paidAt else null)
                close()
            }
            if (item.installments.size > 1) {
                PrimaryButton("حذف این قسط", tone = Tone.Danger) { confirmDelete = true }
            }
        }
        if (pick != null) {
            JalaliPickerDialog(if (pick == "due") due else paidAt, if (pick == "due") "سررسید" else "تاریخ پرداخت", { pick = null }) {
                if (pick == "due") due = it else paidAt = it
                pick = null
            }
        }
        if (confirmDelete) {
            ConfirmDialog("حذف قسط ${n.fa()}؟", "این قسط از برنامه حذف می‌شود.", "حذف کن", { confirmDelete = false }) {
                confirmDelete = false
                Store.deleteInstallment(item.id, inst.id)
                close()
            }
        }
    }
}

// ====================================================================== payment (debts)

@Composable
fun PaymentSheet(app: AppState, item: Item, onDismiss: () -> Unit) {
    GSheet(onDismiss) { close ->
        val unit = Store.settings.unit
        val remaining = item.remaining
        var amount by remember { mutableStateOf(remaining.toString()) }
        var date by remember { mutableStateOf(Store.today) }
        var pick by remember { mutableStateOf(false) }
        var tried by remember { mutableStateOf(false) }
        val a = amount.toLongOrNull() ?: 0L
        val owe = item.dir == Dir.OWE

        SheetTitle(if (owe) "ثبت پرداخت" else "ثبت دریافت")
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T("«${item.title}» · مانده: ${fmtNum(remaining)} $unit", 13, FontWeight.Medium, G.c.ink2)
            MoneyField(if (owe) "مبلغ پرداختی" else "مبلغ دریافتی", amount, { amount = it }, unit, error = tried && (a <= 0 || a > remaining))
            if (tried && a > remaining) T("مبلغ از مانده‌ی بدهی بیشتر است.", 12, FontWeight.Medium, G.c.danger)
            DateField("تاریخ", date, { pick = true })
            Spacer(Modifier.height(4.dp))
        }
        SheetFooter {
            PrimaryButton("ثبت") {
                tried = true
                if (a <= 0 || a > remaining) return@PrimaryButton
                val pid = Store.addPayment(item.id, a, date)
                val done = Store.item(item.id)?.info(Store.today)?.done == true
                close()
                if (done) {
                    Celebrate.burst()
                    app.toast("«${item.title}» کامل تسویه شد 🎉")
                } else {
                    app.toast("ثبت شد: ${fmtNum(a)} $unit", "بازگردانی") { Store.removePayment(item.id, pid) }
                }
            }
        }
        if (pick) JalaliPickerDialog(date, "تاریخ", { pick = false }) { date = it; pick = false }
    }
}

// ====================================================================== pay an installment (confirmation)

@Composable
fun PayInstallmentSheet(app: AppState, item: Item, instId: String, onDismiss: () -> Unit) {
    val inst = item.installments.firstOrNull { it.id == instId }
    if (inst == null) {
        onDismiss()
        return
    }
    val n = item.installments.indexOf(inst) + 1
    GSheet(onDismiss) { close ->
        val c = G.c
        val unit = Store.settings.unit
        val today = Store.today
        var date by remember { mutableStateOf(today) }
        var pick by remember { mutableStateOf(false) }
        val overdueDays = daysBetween(inst.due, today)

        SheetTitle("پرداخت قسط ${n.fa()} از ${item.installments.size.fa()}؟")
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GCard(bg = c.surface2, border = c.line, radius = 18) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(item.title, 16, FontWeight.Bold)
                    if (item.party.isNotBlank()) T(item.party, 12, color = c.muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        T("مبلغ قسط", 13, color = c.ink2)
                        T("${fmtNum(inst.amount)} $unit", 18, FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        T("سررسید", 13, color = c.ink2)
                        T(fmtDate(inst.due), 14, FontWeight.SemiBold)
                    }
                    if (overdueDays > 0) Pill("${overdueDays.toInt().fa()} روز تأخیر", Tone.Danger)
                }
            }
            DateField("تاریخ پرداخت", date, { pick = true }, hint = "اگر قسط را روز دیگری پرداخته‌اید، تاریخ واقعی را بزنید.")
            Spacer(Modifier.height(4.dp))
        }
        SheetFooter {
            PrimaryButton("بله، پرداخت شد") {
                app.confirmPay(item.id, instId, date)
                close()
            }
            GhostButton("انصراف", { close() }, Modifier.fillMaxWidth())
        }
        if (pick) JalaliPickerDialog(date, "تاریخ پرداخت", { pick = false }) { date = it; pick = false }
    }
}

// ====================================================================== confirm

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = G.c
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(24.dp),
        title = { T(title, 18, FontWeight.Bold) },
        text = { T(text, 14, color = c.ink2) },
        confirmButton = {
            T(confirm, 15, FontWeight.Bold, c.danger, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onConfirm).padding(horizontal = 14.dp, vertical = 10.dp))
        },
        dismissButton = {
            T("انصراف", 15, FontWeight.SemiBold, c.ink2, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp))
        },
    )
}
