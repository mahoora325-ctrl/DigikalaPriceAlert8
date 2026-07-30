package ir.example.digikalapricealert

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) بدون نیاز به کتابخانه‌ی خارجی.
 * الگوریتم تبدیل، الگوریتم استاندارد و شناخته‌شده‌ی تقویم جلالی است.
 */
object JalaliDateUtil {

    private val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    // اعداد فارسی برای تبدیل
    private val persianDigits = arrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

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

    /**
     * تبدیل اعداد لاتین به فارسی
     */
    private fun toPersianDigits(text: String): String {
        var result = text
        for (i in 0..9) {
            result = result.replace(i.toString(), persianDigits[i].toString())
        }
        return result
    }

    /**
     * تاریخ و ساعتِ داده‌شده را به‌صورت "yyyy/MM/dd - HH:mm" شمسی برمی‌گرداند.
     */
    fun format(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        
        // استخراج سال، ماه، روز
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        
        // استخراج ساعت و دقیقه (با بررسی معتبر بودن)
        var hour = cal.get(Calendar.HOUR_OF_DAY)
        var minute = cal.get(Calendar.MINUTE)
        
        // اگر دقیقه نامعتبر بود (بیشتر از ۵۹)، تصحیح کن
        if (minute > 59) {
            minute = 0
        }
        
        // ساخت تاریخ با اعداد لاتین
        val latinDate = String.format(Locale.US, "%04d/%02d/%02d - %02d:%02d", jy, jm, jd, hour, minute)
        
        // تبدیل اعداد به فارسی
        val persianDate = toPersianDigits(latinDate)
        
        // اضافه کردن LRM برای کنترل RTL
        return "\u200E$persianDate"
    }

    /**
     * فقط تاریخ را به صورت "yyyy/MM/dd" برمی‌گرداند
     */
    fun formatDateOnly(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        
        val latinDate = String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
        val persianDate = toPersianDigits(latinDate)
        
        return "\u200E$persianDate"
    }

    /**
     * فقط ساعت را به صورت "HH:mm" برمی‌گرداند
     */
    fun formatTimeOnly(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        var hour = cal.get(Calendar.HOUR_OF_DAY)
        var minute = cal.get(Calendar.MINUTE)
        
        // تصحیح دقیقه اگر نامعتبر بود
        if (minute > 59) {
            minute = 0
        }
        
        val latinTime = String.format(Locale.US, "%02d:%02d", hour, minute)
        val persianTime = toPersianDigits(latinTime)
        
        return "\u200E$persianTime"
    }

    fun nowFormatted(): String = format(Date())
}
