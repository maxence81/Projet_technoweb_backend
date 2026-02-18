package pharmacie.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import pharmacie.dao.FournisseurRepository;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Categorie;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;

@Slf4j
@Service
public class ReapprovisionnementService {

    private final MedicamentRepository medicamentDao;
    private final FournisseurRepository fournisseurDao;
    private final MailService mailService;

    public ReapprovisionnementService(MedicamentRepository medicamentDao,
            FournisseurRepository fournisseurDao,
            MailService mailService) {
        this.medicamentDao = medicamentDao;
        this.fournisseurDao = fournisseurDao;
        this.mailService = mailService;
    }

    /**
     * Service métier : Demande de devis de réapprovisionnement.
     * 1. Détermine les médicaments à réapprovisionner (unitesEnStock <=
     * niveauDeReappro)
     * 2. Trouve les fournisseurs susceptibles de fournir ces médicaments (via les
     * catégories)
     * 3. Envoie un mail personnalisé à chaque fournisseur, récapitulant catégorie
     * par catégorie
     * les médicaments à réapprovisionner
     *
     * @return un résumé des mails envoyés
     */
    @Transactional(readOnly = true)
    public List<String> demanderDevis() {
        // 1. Trouver les médicaments à réapprovisionner
        List<Medicament> medicamentsAReappro = medicamentDao.aReaprovisionner();

        if (medicamentsAReappro.isEmpty()) {
            log.info("Aucun médicament à réapprovisionner");
            return List.of("Aucun médicament à réapprovisionner");
        }

        log.info("{} médicament(s) à réapprovisionner", medicamentsAReappro.size());

        // Grouper les médicaments par catégorie
        Map<Categorie, List<Medicament>> parCategorie = medicamentsAReappro.stream()
                .collect(Collectors.groupingBy(Medicament::getCategorie));

        // Récupérer les références des médicaments à réapprovisionner
        List<Integer> references = medicamentsAReappro.stream()
                .map(Medicament::getReference)
                .toList();

        // 2. Trouver les fournisseurs concernés (avec leurs catégories pré-chargées)
        List<Fournisseur> fournisseurs = fournisseurDao.fournisseursAvecCategoriesPourMedicaments(references);

        if (fournisseurs.isEmpty()) {
            log.warn("Aucun fournisseur trouvé pour les médicaments à réapprovisionner");
            return List.of("Aucun fournisseur trouvé pour les médicaments à réapprovisionner");
        }

        // 3. Envoyer un mail à chaque fournisseur
        List<String> resultats = new ArrayList<>();
        for (Fournisseur fournisseur : fournisseurs) {
            String contenuMail = construireMail(fournisseur, parCategorie);
            envoyerMail(fournisseur, contenuMail);
            resultats.add("Mail envoyé à " + fournisseur.getNom() + " (" + fournisseur.getAdresseElectronique() + ")");
            log.info("Mail envoyé à {} ({})", fournisseur.getNom(), fournisseur.getAdresseElectronique());
        }

        return resultats;
    }

    /**
     * Construit le contenu du mail pour un fournisseur donné.
     * Récapitule catégorie par catégorie les médicaments à réapprovisionner
     * que ce fournisseur est susceptible de fournir.
     */
    private String construireMail(Fournisseur fournisseur, Map<Categorie, List<Medicament>> parCategorie) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bonjour ").append(fournisseur.getNom()).append(",\n\n");
        sb.append("Nous vous contactons pour vous demander un devis de réapprovisionnement ");
        sb.append("pour les médicaments suivants :\n\n");

        // Pour chaque catégorie que ce fournisseur peut fournir
        for (Categorie categorie : fournisseur.getCategories()) {
            List<Medicament> medicaments = parCategorie.get(categorie);
            if (medicaments != null && !medicaments.isEmpty()) {
                sb.append("=== Catégorie : ").append(categorie.getLibelle()).append(" ===\n");
                for (Medicament m : medicaments) {
                    sb.append("  - ").append(m.getNom())
                            .append(" (stock actuel: ").append(m.getUnitesEnStock())
                            .append(", seuil: ").append(m.getNiveauDeReappro())
                            .append(")\n");
                }
                sb.append("\n");
            }
        }

        sb.append("Merci de nous transmettre votre devis dans les meilleurs délais.\n\n");
        sb.append("Cordialement,\n");
        sb.append("La Pharmacie");

        return sb.toString();
    }

    /**
     * Envoie un mail à un fournisseur via MailService.
     */
    private void envoyerMail(Fournisseur fournisseur, String contenu) {
        mailService.envoyerMail(fournisseur.getAdresseElectronique(), "Demande de devis de réapprovisionnement",
                contenu);
    }
}
