package org.example.weathercastbot.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeFormatUtil {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");

    /**
     * Converts "2026-03-27T18:00:00+08:00" to "03/27(星期五) 18:00"
     */
    public static String formatWithDayOfWeek(String isoTimeStr) {
        if (isoTimeStr == null || isoTimeStr.length() < 16) return "";
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(isoTimeStr);
            String dow = DateTimeFormatter.ofPattern("EEEE", Locale.TRADITIONAL_CHINESE).format(zdt);
            return DateTimeFormatter.ofPattern("MM/dd").format(zdt) + "(" + dow + ") " + DateTimeFormatter.ofPattern("HH:mm").format(zdt);
        } catch (Exception e) {
            // Fallback to original substring logic if parsing fails
            return isoTimeStr.substring(5, 16).replace("T", " ");
        }
    }

    /**
     * Replaces the "MM/dd" date part with "本日" or "明天" if the date matches today or tomorrow.
     * Example: "03/27(星期五) 18:00" -> "本日(星期五) 18:00"
     */
    public static String convertToRelativeDay(String formattedTime) {
        if (formattedTime == null) return "";
        try {
            LocalDate today = LocalDate.now(TAIPEI_ZONE);
            LocalDate tomorrow = today.plusDays(1);

            String todayStr = DateTimeFormatter.ofPattern("MM/dd").format(today);
            String tomorrowStr = DateTimeFormatter.ofPattern("MM/dd").format(tomorrow);

            if (formattedTime.startsWith(todayStr)) {
                return formattedTime.replaceFirst(todayStr, "本日");
            } else if (formattedTime.startsWith(tomorrowStr)) {
                return formattedTime.replaceFirst(tomorrowStr, "明天");
            }
            return formattedTime;
        } catch (Exception e) {
            return formattedTime;
        }
    }
    /**
     * Checks if current Asia/Taipei time falls within the quiet hours [start, end).
     * @param startHour e.g. "23:00"
     * @param endHour e.g. "07:00"
     */
    public static boolean isQuietHour(String startHour, String endHour) {
        if (startHour == null || endHour == null) return false;
        try {
            java.time.LocalTime start = java.time.LocalTime.parse(startHour);
            java.time.LocalTime end = java.time.LocalTime.parse(endHour);
            java.time.LocalTime now = java.time.LocalTime.now(TAIPEI_ZONE);

            if (start.isBefore(end)) {
                // e.g. 09:00 to 17:00 -> [09:00, 17:00)
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                // e.g. 23:00 to 07:00 -> [23:00, 23:59:59] or [00:00, 07:00)
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
