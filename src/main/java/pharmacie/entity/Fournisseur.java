package pharmacie.entity;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@ToString
public class Fournisseur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    @NonNull
    @NotBlank
    @Size(min = 1, max = 255)
    @Column(unique = true, length = 255)
    private String nom;

    @NonNull
    @NotBlank
    @Email
    @Column(length = 255)
    private String adresseElectronique;

    @ToString.Exclude
    @ManyToMany
    @JoinTable(name = "fournisseur_categorie", joinColumns = @JoinColumn(name = "fournisseur_id"), inverseJoinColumns = @JoinColumn(name = "categorie_code"))
    @JsonIgnoreProperties({ "fournisseurs", "medicaments" })
    private List<Categorie> categories = new LinkedList<>();
}
