package com.massivecraft.factions.util;

/**
 * Formats a duration in milliseconds into a human-readable compact string.
 * Examples:
 *   90061000 -> "1d 1h 1m 1s"
 *   65000    -> "1m 5s"
 *   500      -> "0s"
 */
public final class TimeFormatter {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private TimeFormatter() {}

    public static String format(long millis) {
        if (millis <= 0) return "0s";

        long days = millis / DAY;
        millis -= days * DAY;
        long hours = millis / HOUR;
        millis -= hours * HOUR;
        long minutes = millis / MINUTE;
        millis -= minutes * MINUTE;
        long seconds = millis / SECOND;

        StringBuilder sb = new StringBuilder(16);
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') sb.setLength(len - 1);
        return sb.toString();
    }
}

