package pharmacie.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary // On utilise ce service par défaut plutôt que JavaMailSender (ou une autre
         // implémentation de MailService)
public class MailgunService implements MailService {

    @Value("${mailgun.api-key}")
    private String apiKey;

    @Value("${mailgun.domain}")
    private String domain;

    @Value("${mailgun.from-email}")
    private String fromEmail;

    @Override
    public void envoyerMail(String destinataire, String sujet, String contenu) {
        log.info("Envoi de mail via Mailgun à {} (Sujet: {})", destinataire, sujet);
        try {
            JsonNode response = sendSimpleMessage(destinataire, sujet, contenu);
            log.info("Mailgun Response: {}", response.toString());
        } catch (UnirestException e) {
            log.error("Erreur lors de l'envoi du mail via Mailgun", e);
            throw new RuntimeException("Echec de l'envoi de mail via Mailgun", e);
        }
    }

    private JsonNode sendSimpleMessage(String to, String subject, String text) throws UnirestException {
        HttpResponse<JsonNode> request = Unirest.post("https://api.mailgun.net/v3/" + domain + "/messages")
                .basicAuth("api", apiKey)
                .queryString("from", fromEmail)
                .queryString("to", to)
                .queryString("subject", subject)
                .queryString("text", text)
                .asJson();
        return request.getBody();
    }
}
