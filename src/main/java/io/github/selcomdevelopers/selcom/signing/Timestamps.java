package io.github.selcomdevelopers.selcom.signing;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** Timestamp format and parsing: {@code yyyy-MM-ddTHH:mm:ss+HH:MM}, with {@code +HHMM} also accepted. */
public final class Timestamps {
    /** The zone clients use by default: no daylight saving, always {@code +03:00}. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private static final Pattern PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:?\\d{2}$");
    // Lower-case xxx prints +00:00 for UTC; upper-case XXX would print Z. DateTimeFormatter is immutable and thread-safe.
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private Timestamps() {
    }

    /**
     * Formats the clock's current instant in the colon form, truncated to whole seconds.
     *
     * @param clock the clock
     * @param zone the zone whose offset is printed
     * @return for example {@code 2026-11-21T10:30:46+03:00}
     */
    public static String now(Clock clock, ZoneId zone) {
        return OffsetDateTime.now(clock.withZone(zone)).truncatedTo(ChronoUnit.SECONDS).format(FORMAT);
    }

    /**
     * Checks a timestamp and parses it.
     *
     * @param timestamp the text
     * @return the instant with its offset
     * @throws SigningException with {@link SigningException.Code#INVALID_TIMESTAMP} if it does not match the format
     */
    public static OffsetDateTime parse(String timestamp) {
        if (timestamp == null || !PATTERN.matcher(timestamp).matches()) {
            throw invalid();
        }
        String colonForm = timestamp.charAt(timestamp.length() - 3) == ':'
                ? timestamp
                : timestamp.substring(0, timestamp.length() - 2) + ":" + timestamp.substring(timestamp.length() - 2);
        try {
            return OffsetDateTime.parse(colonForm);
        } catch (DateTimeException e) {
            throw invalid();
        }
    }

    /**
     * Checks a timestamp without returning it.
     *
     * @param timestamp the text
     * @throws SigningException if it does not match the format
     */
    public static void check(String timestamp) {
        parse(timestamp);
    }

    private static SigningException invalid() {
        return new SigningException(SigningException.Code.INVALID_TIMESTAMP,
                "timestamp is not yyyy-MM-ddTHH:mm:ss+HH:MM or +HHMM");
    }
}
