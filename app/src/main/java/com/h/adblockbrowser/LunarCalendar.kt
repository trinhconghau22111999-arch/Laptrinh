package com.h.adblockbrowser

import kotlin.math.floor
import kotlin.math.sin

/** Thuật toán chuyển dương lịch -> âm lịch (múi giờ Việt Nam, UTC+7), dựa theo thuật toán công
 *  khai phổ biến của Hồ Ngọc Đức, được nhiều app lịch âm mã nguồn mở sử dụng. Kết quả là tính
 *  toán thiên văn gần đúng - đủ chính xác cho việc hiển thị ngày âm hàng ngày. */
object LunarCalendar {

    private const val TZ = 7.0

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun newMoon(k: Int): Double {
        val T = k / 1236.85
        val T2 = T * T
        val T3 = T2 * T
        val dr = Math.PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3
        jd1 += 0.00033 * sin((166.56 + 132.87 * T - 0.009173 * T2) * dr)
        val M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3
        val Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3
        val F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3
        var c1 = (0.1734 - 0.000393 * T) * sin(M * dr) + 0.0021 * sin(2 * dr * M)
        c1 -= 0.4068 * sin(Mpr * dr) + 0.0161 * sin(dr * 2 * Mpr)
        c1 -= 0.0004 * sin(dr * 3 * Mpr)
        c1 += 0.0104 * sin(dr * 2 * F) - 0.0051 * sin(dr * (M + Mpr))
        c1 -= 0.0074 * sin(dr * (M - Mpr)) + 0.0004 * sin(dr * (2 * F + M))
        c1 -= 0.0004 * sin(dr * (2 * F - M)) - 0.0006 * sin(dr * (2 * F + Mpr))
        c1 += 0.0010 * sin(dr * (2 * F - Mpr)) + 0.0005 * sin(dr * (2 * Mpr + M))
        val deltaT = if (T < -11)
            0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
        else
            -0.000278 + 0.000265 * T + 0.000262 * T2
        return jd1 + c1 - deltaT
    }

    private fun sunLongitude(jdn: Double): Double {
        val T = (jdn - 2451545.0) / 36525
        val T2 = T * T
        val dr = Math.PI / 180
        val M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2
        val L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2
        var dl = (1.914600 - 0.004817 * T - 0.000014 * T2) * sin(dr * M)
        dl += (0.019993 - 0.000101 * T) * sin(dr * 2 * M) + 0.000290 * sin(dr * 3 * M)
        var l = (L0 + dl) * dr
        l -= Math.PI * 2 * floor(l / (Math.PI * 2))
        return l
    }

    private fun getSunLongitude(dayNumber: Int): Int =
        floor(sunLongitude(dayNumber - 0.5 - TZ / 24) / Math.PI * 6).toInt()

    private fun getNewMoonDay(k: Int): Int =
        floor(newMoon(k) + 0.5 + TZ / 24).toInt()

    private fun getLunarMonth11(yy: Int): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k)
        val sunLong = getSunLongitude(nm)
        if (sunLong >= 9) nm = getNewMoonDay(k - 1)
        return nm
    }

    private fun getLeapMonthOffset(a11: Int): Int {
        val k = floor(0.5 + (a11 - 2415021.076998695) / 29.530588853).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i))
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }

    /** Trả về [ngày âm, tháng âm, năm âm, có phải tháng nhuận không (1/0)]. */
    fun solarToLunar(dd: Int, mm: Int, yy: Int): IntArray {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = getNewMoonDay(k)
        var a11 = getLunarMonth11(yy)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = 0
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) lunarLeap = 1
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return intArrayOf(lunarDay, lunarMonth, lunarYear, lunarLeap)
    }
}
