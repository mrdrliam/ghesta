package com.kassa.ghesta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.History
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.PayTarget
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.ui.theme.G
import java.time.LocalDate

/** Editable state of the "payment" form, shared by the add sheet and the edit sheet. */
class ExpenseDraft(item: Item?, today: LocalDate) {
    var payee by mutableStateOf(item?.party ?: "")
    var title by mutableStateOf(item?.takeIf { it.title != it.party }?.title ?: "")
    var amount by mutableStateOf(item?.total?.takeIf { it > 0 }?.toString() ?: "")
    var date by mutableStateOf(item?.payments?.firstOrNull()?.date ?: today)
    var tag by mutableStateOf(item?.tag ?: "")
    var target by mutableStateOf<TargetDraft?>(item?.targets?.firstOrNull()?.let { listOf(it).toDrafts().first() })
    var note by mutableStateOf(item?.note ?: "")

    /** Explains an automatic fill so it never feels like magic. */
    var autoNote by mutableStateOf<String?>(null)

    val amountN: Long get() = amount.toLongOrNull() ?: 0L
    val hasWho: Boolean get() = payee.isNotBlank() || title.isNotBlank()
    val valid: Boolean get() = amountN > 0 && hasWho && target?.error() == null

    fun targetValue(): PayTarget? = target?.takeIf { it.number.isNotBlank() }?.let { listOf(it).toTargets().firstOrNull() }
}

/** The fields of the form. Picking a payee you have paid before fills in the card and the tag you used last time. */
@Composable
fun ExpenseFields(d: ExpenseDraft, tried: Boolean, onPickDate: () -> Unit) {
    val c = G.c
    val unit = Store.settings.unit
    val items = Store.items
    val payees = remember(items) { History.payees(items) }
    val tags = remember(items) { (History.tags(items) + History.defaultTags).distinct().take(8) }

    fun choose(p: History.Payee) {
        d.payee = p.name
        var filled = false
        if (d.target == null && p.lastTarget != null) {
            d.target = listOf(p.lastTarget).toDrafts().first().copy(amount = "")
            filled = true
        }
        if (d.tag.isBlank() && p.lastTag.isNotBlank()) {
            d.tag = p.lastTag
            filled = true
        }
        d.autoNote = if (filled) "شماره و برچسب آخرین پرداخت به «${p.name}» پر شد؛ اگر لازم نیست، تغییرش دهید." else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GField(
            "به چه کسی؟", d.payee,
            { v ->
                d.payee = v
                // typing a name you have paid before (exactly) behaves like tapping its chip
                payees.firstOrNull { History.normalize(it.name) == History.normalize(v) }?.let { if (d.target == null && d.tag.isBlank()) choose(it) }
            },
            placeholder = "مثلاً علی رضایی",
            error = tried && !d.hasWho,
            hint = if (tried && !d.hasWho) "نام شخص یا بابت پرداخت را بنویسید" else null,
        )
        val typed = History.normalize(d.payee)
        val shown = payees.filter { typed.isEmpty() || (typed in History.normalize(it.name) && History.normalize(it.name) != typed) }.take(8)
        if (shown.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { p -> GChip(p.name, false, { choose(p) }) }
            }
        }
        GField("بابت (اختیاری)", d.title, { d.title = it }, placeholder = "مثلاً برق‌کار، سیم‌کشی")
        MoneyField("مبلغ", d.amount, { d.amount = it }, unit, error = tried && d.amountN <= 0)
        DateField("تاریخ پرداخت", d.date, onPickDate)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GField("برچسب (اختیاری)", d.tag, { d.tag = it }, placeholder = "مثلاً ساختمان")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags) { t -> GChip(t, d.tag.trim() == t, { d.tag = if (d.tag.trim() == t) "" else t }) }
            }
            T("برچسب‌ها برای گزارش‌گیری‌اند؛ مثلاً همه‌ی هزینه‌های «ساختمان» را یک‌جا جمع می‌زنید.", 11, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }

        DestinationEditor(d.target, { d.target = it }, tried, holderDefault = d.payee)
        d.autoNote?.let { T(it, 12, FontWeight.Medium, c.gold, Modifier.padding(horizontal = 4.dp)) }

        GField("یادداشت (اختیاری)", d.note, { d.note = it }, singleLine = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
    }
}

/** Edit (or delete) a payment that was recorded earlier. */
@Composable
fun ExpenseEditSheet(app: AppState, item: Item, onDismiss: () -> Unit) {
    GSheet(onDismiss) { close ->
        val d = remember(item.id) { ExpenseDraft(item, Store.today) }
        var tried by remember { mutableStateOf(false) }
        var pick by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) { T("ویرایش پرداخت", 20, FontWeight.Bold) }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExpenseFields(d, tried) { pick = true }
            Spacer(Modifier.height(4.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 14.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton("ذخیره") {
                tried = true
                if (!d.valid) return@PrimaryButton
                Store.updateExpense(item.id, d.title, d.payee, d.note, d.amountN, d.date, d.tag, d.targetValue())
                app.toast("پرداخت ویرایش شد")
                close()
            }
            PrimaryButton("حذف این پرداخت", tone = Tone.Danger) { confirmDelete = true }
        }
        if (pick) JalaliPickerDialog(d.date, "تاریخ پرداخت", { pick = false }) { d.date = it; pick = false }
        if (confirmDelete) {
            ConfirmDialog("حذف این پرداخت؟", "از تاریخچه‌ی پرداخت‌ها پاک می‌شود.", "حذف کن", { confirmDelete = false }) {
                confirmDelete = false
                val removed = item
                Store.delete(item.id)
                app.toast("پرداخت حذف شد", "بازگردانی") { Store.restoreItem(removed) }
                close()
            }
        }
    }
}
