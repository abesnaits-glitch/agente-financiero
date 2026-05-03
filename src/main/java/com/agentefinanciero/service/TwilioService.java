package com.agentefinanciero.service;

import com.agentefinanciero.util.LogUtil;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.security.RequestValidator;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);

    @Value("${twilio.whatsapp.number}")
    private String fromNumber;

    @Value("${twilio.auth.token}")
    private String authToken;

    /**
     * Validates that an incoming request genuinely came from Twilio.
     * Uses HMAC-SHA1 over the webhook URL + sorted POST params, signed with authToken.
     */
    public boolean validarFirma(String signature, String webhookUrl, Map<String, String> params) {
        if (signature == null || signature.isBlank()) {
            log.warn("[Twilio] X-Twilio-Signature ausente");
            return false;
        }
        try {
            RequestValidator validator = new RequestValidator(authToken);
            return validator.validate(webhookUrl, params, signature);
        } catch (Exception e) {
            log.error("[Twilio] error validando firma: {}", e.getMessage());
            return false;
        }
    }

    public void sendWhatsApp(String to, String text) {
        String toNorm   = toWhatsAppFormat(to);
        String fromNorm = toWhatsAppFormat(fromNumber);

        log.info("[Twilio] enviando mensaje a='{}'", LogUtil.maskPhone(toNorm));

        Message message = Message.creator(
                new PhoneNumber(toNorm),
                new PhoneNumber(fromNorm),
                text
        ).create();

        log.info("[Twilio] mensaje enviado SID={} status={}", message.getSid(), message.getStatus());
    }

    public void sendWhatsAppWithMedia(String to, String text, String mediaUrl) {
        String toNorm   = toWhatsAppFormat(to);
        String fromNorm = toWhatsAppFormat(fromNumber);

        log.info("[Twilio] enviando imagen a='{}'", LogUtil.maskPhone(toNorm));

        Message message = Message.creator(
                new PhoneNumber(toNorm),
                new PhoneNumber(fromNorm),
                text
        ).setMediaUrl(List.of(URI.create(mediaUrl))).create();

        log.info("[Twilio] imagen enviada SID={} status={}", message.getSid(), message.getStatus());
    }

    private String toWhatsAppFormat(String number) {
        String digits = number
                .replace("whatsapp:", "")
                .replace(" ", "")
                .trim()
                .replaceAll("^\\+", "");
        return "whatsapp:+" + digits;
    }
}
