package pharmacie.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import pharmacie.entity.Categorie;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;
import pharmacie.service.ReapprovisionnementService;

@SpringBootTest
@Transactional
public class ReaprovisionementTest {

        @Autowired
        private MedicamentRepository medicamentRepository;

        @Autowired
        private CategorieRepository categorieRepository;

        @Autowired
        private ReapprovisionnementService reapprovisionnementService;

        @Autowired
        private JavaMailSender mailSender;

        @Test
        void testAReaprovisionner() {
                // Créer une catégorie (obligatoire pour Medicament)
                Categorie c = new Categorie("TestCategorie");
                categorieRepository.save(c);

                // Médicament avec stock > seuil => PAS à réapprovisionner
                Medicament stockOk = new Medicament();
                stockOk.setNom("StockOK");
                stockOk.setCategorie(c);
                stockOk.setUnitesEnStock(100);
                stockOk.setNiveauDeReappro(10);
                medicamentRepository.save(stockOk);

                // Médicament avec stock == seuil => À réapprovisionner
                Medicament stockEgal = new Medicament();
                stockEgal.setNom("StockEgal");
                stockEgal.setCategorie(c);
                stockEgal.setUnitesEnStock(5);
                stockEgal.setNiveauDeReappro(5);
                medicamentRepository.save(stockEgal);

                // Médicament avec stock < seuil => À réapprovisionner
                Medicament stockBas = new Medicament();
                stockBas.setNom("StockBas");
                stockBas.setCategorie(c);
                stockBas.setUnitesEnStock(2);
                stockBas.setNiveauDeReappro(10);
                medicamentRepository.save(stockBas);

                // Médicament indisponible avec stock bas => PAS dans le résultat
                Medicament indisponible = new Medicament();
                indisponible.setNom("Indisponible");
                indisponible.setCategorie(c);
                indisponible.setUnitesEnStock(0);
                indisponible.setNiveauDeReappro(10);
                indisponible.setIndisponible(true);
                medicamentRepository.save(indisponible);

                // Exécuter la requête
                List<Medicament> aReappro = medicamentRepository.aReaprovisionner();
                List<String> noms = aReappro.stream().map(Medicament::getNom).toList();

                assertTrue(noms.contains("StockEgal"), "Un médicament avec stock == seuil doit être réapprovisionné");
                assertTrue(noms.contains("StockBas"), "Un médicament avec stock < seuil doit être réapprovisionné");
                assertFalse(noms.contains("StockOK"),
                                "Un médicament avec stock > seuil ne doit pas être réapprovisionné");
                assertFalse(noms.contains("Indisponible"),
                                "Un médicament indisponible ne doit pas être réapprovisionné");
        }

        @Test
        void testFournisseursPourMedicaments() {
                // Les données de test_data.sql contiennent :
                // - Catégorie 98 avec médicaments 93-99
                // - Fournisseurs 1 (PharmaDistrib), 2 (MediFrance), 3 (SantéPlus) fournissent
                // catégorie 98
                // - Fournisseur 1 (PharmaDistrib) et 4 (BioMedic) fournissent catégorie 99

                // Chercher les fournisseurs pour le médicament 93 (catégorie 98)
                List<Fournisseur> fournisseurs = medicamentRepository.fournisseursPourMedicaments(List.of(93));
                List<String> noms = fournisseurs.stream().map(Fournisseur::getNom).toList();

                assertEquals(3, fournisseurs.size(), "Le médicament 93 (catégorie 98) doit avoir 3 fournisseurs");
                assertTrue(noms.contains("PharmaDistrib"));
                assertTrue(noms.contains("MediFrance"));
                assertTrue(noms.contains("SantéPlus"));
                assertFalse(noms.contains("BioMedic"), "BioMedic ne fournit pas la catégorie 98");
        }

        @Test
        void testChaqueCategorieAuMoinsDeuxFournisseurs() {
                // Vérifier que chaque catégorie a au moins 2 fournisseurs
                List<Categorie> categories = categorieRepository.findAll();
                for (Categorie cat : categories) {
                        assertTrue(cat.getFournisseurs().size() >= 2,
                                        "La catégorie '" + cat.getLibelle() + "' doit avoir au moins 2 fournisseurs, " +
                                                        "mais en a " + cat.getFournisseurs().size());
                }
        }

        @Test
        void testEnvoiMail() throws Exception {
                // Utiliser les données existantes de test_data.sql
                // Le médicament 93 (catégorie 98) a stock=100, seuil=10 => PAS à
                // réapprovisionner
                // On le modifie pour qu'il soit à réapprovisionner
                Medicament m = medicamentRepository.findById(93).orElseThrow();
                m.setUnitesEnStock(5); // Passe en dessous du seuil de 10
                medicamentRepository.saveAndFlush(m);

                // Appeler le service de réapprovisionnement
                // Cela va VRAIMENT envoyer les mails en utilisant la configuration SMTP
                List<String> resultats = reapprovisionnementService.demanderDevis();

                // Vérifier qu'au moins un mail a été envoyé
                assertFalse(resultats.isEmpty(), "Il doit y avoir au moins un résultat");

                // On vérifie que les résultats contiennent les confirmations d'envoi
                // Le médicament 93 est dans la catégorie 98
                // Les fournisseurs de la catégorie 98 sont PharmaDistrib, MediFrance, SantéPlus

                boolean mailPharmaDistrib = resultats.stream()
                                .anyMatch(s -> s.contains("maxence.dabrowski81+pharmadistrib@gmail.com"));
                boolean mailMediFrance = resultats.stream()
                                .anyMatch(s -> s.contains("maxence.dabrowski81+medifrance@gmail.com"));
                boolean mailSantePlus = resultats.stream()
                                .anyMatch(s -> s.contains("maxence.dabrowski81+santeplus@gmail.com"));

                assertTrue(mailPharmaDistrib, "Un mail doit avoir été envoyé à PharmaDistrib");
                assertTrue(mailMediFrance, "Un mail doit avoir été envoyé à MediFrance");
                assertTrue(mailSantePlus, "Un mail doit avoir été envoyé à SantéPlus");
        }

}
