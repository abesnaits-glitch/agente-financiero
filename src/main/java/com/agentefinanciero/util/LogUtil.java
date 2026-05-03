package com.agentefinanciero.util;

public final class LogUtil {

    private LogUtil() {}

    /** Returns last 4 digits of a phone number, masking the rest. */
    public static String maskPhone(String phone) {
        if (phone == null) return "null";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) return "***";
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /** Returns first char + *** + domain. */
    public static String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
