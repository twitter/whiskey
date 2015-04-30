package com.twitter.whiskey.net;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author Michael Schore
 */
public class Header extends AbstractMap.SimpleImmutableEntry<String, String> {

    private static final DateFormat DATE_FORMAT;
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    static {
        DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        DATE_FORMAT.setTimeZone(GMT);
    }

    public Header(String key, String value) {
        super(key.trim().toLowerCase(), value.trim());
    }

    public Header(String key, Date date) {
        this(key, DATE_FORMAT.format(date));
    }

    public Header(String key, Integer value) {
        this(key, value.toString());
    }

    public Header(Map.Entry<? extends String, ? extends String> entry) {
        this(entry.getKey(), entry.getValue());
    }

    public int getIntegerValue() {
        try {
            return Integer.valueOf(getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public long getLongValue() {
        try {
            return Long.valueOf(getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return getKey() + ": " + getValue();
    }
}
