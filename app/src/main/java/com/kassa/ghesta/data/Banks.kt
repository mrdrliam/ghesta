package com.kassa.ghesta.data

import java.math.BigInteger

/** Helpers for Iranian payment identifiers: bank names from card prefixes / Sheba codes, and checksums. */
object Banks {
    private val cardBins = mapOf(
        "603799" to "بانک ملی", "589210" to "بانک سپه", "627648" to "توسعه صادرات", "627961" to "صنعت و معدن",
        "603770" to "بانک کشاورزی", "639217" to "بانک کشاورزی", "628023" to "بانک مسکن", "627760" to "پست بانک",
        "502908" to "توسعه تعاون", "627412" to "اقتصاد نوین", "622106" to "بانک پارسیان", "639194" to "بانک پارسیان",
        "502229" to "بانک پاسارگاد", "639347" to "بانک پاسارگاد", "627488" to "بانک کارآفرین", "621986" to "بانک سامان",
        "639346" to "بانک سینا", "639607" to "بانک سرمایه", "636214" to "بانک آینده", "502806" to "بانک شهر",
        "504706" to "بانک شهر", "502938" to "بانک دی", "603769" to "بانک صادرات", "610433" to "بانک ملت",
        "991975" to "بانک ملت", "589463" to "رفاه کارگران", "627353" to "بانک تجارت", "585983" to "بانک تجارت",
        "639370" to "مهر اقتصاد", "606373" to "قرض‌الحسنه مهر", "627381" to "بانک انصار", "505785" to "ایران زمین",
        "636949" to "حکمت ایرانیان", "505416" to "بانک گردشگری", "636795" to "بانک مرکزی", "628157" to "اعتباری توسعه",
        "505801" to "موسسه کوثر", "639599" to "قوامین", "606256" to "موسسه ملل", "504172" to "بانک رسالت",
        "507677" to "موسسه نور", "585947" to "بانک خاورمیانه",
    )

    private val shebaCodes = mapOf(
        "010" to "بانک مرکزی", "011" to "صنعت و معدن", "012" to "بانک ملت", "013" to "رفاه کارگران",
        "014" to "بانک مسکن", "015" to "بانک سپه", "016" to "بانک کشاورزی", "017" to "بانک ملی",
        "018" to "بانک تجارت", "019" to "بانک صادرات", "020" to "توسعه صادرات", "021" to "پست بانک",
        "022" to "توسعه تعاون", "051" to "موسسه توسعه", "052" to "بانک قوامین", "053" to "بانک کارآفرین",
        "054" to "بانک پارسیان", "055" to "اقتصاد نوین", "056" to "بانک سامان", "057" to "بانک پاسارگاد",
        "058" to "بانک سرمایه", "059" to "بانک سینا", "060" to "قرض‌الحسنه مهر", "061" to "بانک شهر",
        "062" to "بانک آینده", "063" to "بانک انصار", "064" to "بانک گردشگری", "065" to "حکمت ایرانیان",
        "066" to "بانک دی", "069" to "ایران زمین", "070" to "بانک رسالت", "075" to "موسسه ملل",
        "078" to "بانک خاورمیانه", "080" to "موسسه نور", "090" to "موسسه کوثر",
    )

    /** [digits] are the card digits typed so far (Latin). Needs at least the 6-digit prefix. */
    fun cardBank(digits: String): String? = if (digits.length >= 6) cardBins[digits.take(6)] else null

    /** [digits] are the digits after "IR": 2 check digits, then the 3-digit bank code. */
    fun shebaBank(digits: String): String? = if (digits.length >= 5) shebaCodes[digits.substring(2, 5)] else null

    fun luhnValid(card: String): Boolean {
        if (card.length != 16 || !card.all { it.isDigit() }) return false
        var sum = 0
        for ((i, ch) in card.withIndex()) {
            var d = ch - '0'
            if (i % 2 == 0) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    /** ISO 7064 mod 97 check for "IR" + 24 digits. */
    fun shebaValid(sheba: String): Boolean {
        if (sheba.length != 26 || !sheba.startsWith("IR") || !sheba.drop(2).all { it.isDigit() }) return false
        val rearranged = sheba.drop(4) + "1827" + sheba.substring(2, 4)
        return BigInteger(rearranged).mod(BigInteger.valueOf(97)) == BigInteger.ONE
    }
}
