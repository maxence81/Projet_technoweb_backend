package pharmacie.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

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
import pharmacie.service.MailService;

@SpringBootTest
@Transactional
public class ReaprovisionementTest {

        @Autowired
        private MedicamentRepository medicamentRepository;

        @Autowired
        private CategorieRepository categorieRepository;

        @Autowired
        private ReapprovisionnementService reapprovisionnementService;

        @MockitoBean
        private MailService mailService;

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
        void testEnvoiMail() {
                // GIVEN
                // On s'assure que le médicament 1 est à réapprovisionner (stock 5 <= seuil 10)
                Medicament med = medicamentRepository.findById(1).orElseThrow();
                med.setUnitesEnStock(5);
                medicamentRepository.save(med);

                // WHEN
                List<String> resultats = reapprovisionnementService.demanderDevis();

                // THEN
                assertThat(resultats).hasSize(2);
                assertThat(resultats.get(0)).contains("Mail envoyé à");

                // On vérifie que le mock a été appelé 2 fois (pour les 2 fournisseurs)
                verify(mailService, times(2)).envoyerMail(anyString(), anyString(), anyString());
        }

}
