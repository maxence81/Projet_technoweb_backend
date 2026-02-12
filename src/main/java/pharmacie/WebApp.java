// src/main/java/pharmacie/WebApp.java
package pharmacie;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Medicament;

@SpringBootApplication
public class WebApp {

    private final MedicamentRepository medicamentRepository;

    public WebApp(MedicamentRepository medicamentRepository) {
        this.medicamentRepository = medicamentRepository;
    }

    public static void main(String[] args) {
        SpringApplication.run(WebApp.class, args);
    }

    @Bean
    ModelMapper modelMapper() {
        return new ModelMapper();
    }

    public List<Medicament> aReaprovisionner() {
        return medicamentRepository.aReaprovisionner();
    }

}
