package sn.chezketchup.backend.annonces.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "annonce_slides")
public class AnnonceSlide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "annonce_id", nullable = false)
    private Annonce annonce;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 80)
    private String titre;

    @Column(length = 300)
    private String message;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    protected AnnonceSlide() {
    }

    AnnonceSlide(Annonce annonce, int position, String titre, String message, String imageUrl) {
        this.annonce = annonce;
        this.position = position;
        this.titre = titre.trim();
        this.message = message == null || message.isBlank() ? null : message.trim();
        this.imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getTitre() {
        return titre;
    }

    public String getMessage() {
        return message;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
