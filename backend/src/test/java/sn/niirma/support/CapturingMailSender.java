package sn.niirma.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Remplace l'envoi SMTP dans les tests : les messages sont conservés pour vérification.
 */
public class CapturingMailSender extends JavaMailSenderImpl {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage... messages) {
        sent.addAll(List.of(messages));
    }

    @Override
    public void send(SimpleMailMessage message) {
        sent.add(message);
    }

    public List<SimpleMailMessage> sentTo(String email) {
        return sent.stream().filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
