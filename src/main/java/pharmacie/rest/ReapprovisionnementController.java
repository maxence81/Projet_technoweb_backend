package pharmacie.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import pharmacie.service.ReapprovisionnementService;

@Slf4j
@RestController
@RequestMapping(path = "/api/services/reapprovisionnement")
public class ReapprovisionnementController {

    private final ReapprovisionnementService reapprovisionnementService;

    public ReapprovisionnementController(ReapprovisionnementService reapprovisionnementService) {
        this.reapprovisionnementService = reapprovisionnementService;
    }

    /**
     * Endpoint REST pour lancer la demande de devis de réapprovisionnement.
     * Identifie les médicaments à réapprovisionner et envoie un mail
     * à chaque fournisseur concerné.
     *
     * @return la liste des mails envoyés
     */
    @PostMapping("demanderDevis")
    public ResponseEntity<List<String>> demanderDevis() {
        log.info("Contrôleur : demande de devis de réapprovisionnement");
        List<String> resultats = reapprovisionnementService.demanderDevis();
        return ResponseEntity.ok(resultats);
    }
}
