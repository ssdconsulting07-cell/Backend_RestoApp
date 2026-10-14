package sn.chezketchup.backend.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.chezketchup.backend.security.Role;

@Entity
@Table(name = "staff_users")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String firstName;

    private String lastName;

    private String address;

    private String cni;

    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    // JdbcTypeCode(VARCHAR) force une colonne VARCHAR plutot que le type ENUM
    // natif MySQL que Hibernate 6 choisit par defaut pour un enum STRING sur ce
    // dialecte : un ENUM natif fige la liste de valeurs a la creation de la
    // table, et ddl-auto=update ne la met jamais a jour -> ajouter un role
    // (ex. VENDEUR) plantait l'appli sur toute base deja existante avec
    // "Data truncated for column 'role'". Les bases creees avant ce correctif
    // doivent migrer une fois : ALTER TABLE staff_users MODIFY role VARCHAR(20);
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Role role;

    private boolean active = true;

    private boolean mustChangePassword;

    protected StaffUser() {
    }

    public StaffUser(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public StaffUser(String username, String passwordHash, Role role, boolean mustChangePassword) {
        this(username, passwordHash, role);
        this.mustChangePassword = mustChangePassword;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAddress() {
        return address;
    }

    public String getCni() {
        return cni;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setCni(String cni) {
        this.cni = cni;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}