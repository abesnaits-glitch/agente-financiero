package com.agentefinanciero.service;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);

    @Value("${twilio.whatsapp.number}")
    private String fromNumber;

    public void sendWhatsApp(String to, String text) {
        String toNorm   = toWhatsAppFormat(to);
        String fromNorm = toWhatsAppFormat(fromNumber);

        log.info("[Twilio] enviando mensaje de='{}' a='{}': '{}'", fromNorm, toNorm,
                text.length() > 100 ? text.substring(0, 100) + "..." : text);

        Message message = Message.creator(
                new PhoneNumber(toNorm),
                new PhoneNumber(fromNorm),
                text
        ).create();

        log.info("[Twilio] mensaje enviado. SID={} status={}", message.getSid(), message.getStatus());
    }

    private String toWhatsAppFormat(String number) {
        String clean = number.trim();
        if (clean.startsWith("whatsapp:")) return clean;
        if (clean.startsWith("+")) return "whatsapp:" + clean;
        return "whatsapp:+" + clean;
    }
}
