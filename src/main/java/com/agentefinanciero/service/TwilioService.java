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
        log.info("[Twilio] enviando mensaje a '{}': '{}'", to,
                text.length() > 100 ? text.substring(0, 100) + "..." : text);

        Message message = Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(fromNumber),
                text
        ).create();

        log.info("[Twilio] mensaje enviado. SID={} status={}", message.getSid(), message.getStatus());
    }
}
