package com.baracel.portal.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends plain mail through Mailpit locally. Failures are logged, never thrown, so business actions still succeed. */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${portal.mail.enabled:true}") boolean enabled,
                       @Value("${portal.mail.from:no-reply@baracel.example}") String from) {
        this.sender = sender;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender s = sender.getIfAvailable();
        if (!enabled || s == null || to == null) return;
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            s.send(m);
        } catch (Exception e) {
            log.warn("Could not send mail to {}: {}", to, e.getMessage());
        }
    }
}
