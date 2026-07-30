package ir.example.digikalapricealert

import java.util.Calendar
import java.util.Date

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) بدون نیاز به کتابخانه‌ی خارجی.
 * الگوریتم تبدیل، الگوریتم استاندارد و شناخته‌شده‌ی تقویم جلالی است.
 */
object JalaliDateUtil {

    private val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    private fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): Triple<Int, Int, Int> {
        val gy = gYear - 1600
        val gm = gMonth - 1
        val gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) gDayNo += gDaysInMonth[i]
        if (gm > 1 && isGregorianLeap(gYear)) gDayNo += 1
        gDayNo += gd

        var jDayNo = gDayNo - 79

        val jNp = Math.floorDiv(jDayNo, 12053)
        jDayNo = Math.floorMod(jDayNo, 12053)

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var i = 0
        while (i < 11 && jDayNo >= jDaysInMonth[i]) {
            jDayNo -= jDaysInMonth[i]
            i++
        }
        val jm = i + 1
        val jd = jDayNo + 1

        return Triple(jy, jm, jd)
    }

    /** تاریخ و ساعتِ داده‌شده را به‌صورت "yyyy/MM/dd HH:mm" شمسی برمی‌گرداند. */
    fun format(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        // Locale.US عمداً صریح مشخص شده: اگر زبان گوشی فارسی باشد، فرمت‌کننده‌ی
        // جاوا خودش بی‌سروصدا ارقام را به ارقام فارسیِ محلیِ سیستم تبدیل می‌کند
        // (قبل از اینکه منطق تبدیل خودمان اجرا شود) و همان چیزی می‌شود که باعث
        // به‌هم‌ریختگی فونت اعداد می‌شد. با Locale.US همیشه از ارقام لاتین شروع
        // می‌کنیم و خودمان با PersianNumberUtils تبدیل را کنترل می‌کنیم.
        return String.format(java.util.Locale.US, "%04d/%02d/%02d %02d:%02d", jy, jm, jd, hour, minute)
    }

    fun nowFormatted(): String = format(Date())
}
