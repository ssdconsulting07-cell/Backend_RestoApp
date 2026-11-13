package sn.chezketchup.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.security.Role;

class ManagerInitialTest {

    private final StaffUserRepository comptes = mock(StaffUserRepository.class);
    private final PasswordEncoder encodeur = mock(PasswordEncoder.class);
    private final JournalActions journal = mock(JournalActions.class);

    private ManagerInitial avec(String identifiant, String motDePasse) {
        return new ManagerInitial(comptes, encodeur, journal, identifiant, motDePasse);
    }

    @Test
    void neFaitRienSansVariables() {
        avec("", "").run(null);
        verifyNoInteractions(comptes, encodeur, journal);
    }

    @Test
    void creeLePremierManagerAvecMotDePasseAChanger() {
        when(comptes.existsByRole(Role.MANAGER)).thenReturn(false);
        when(comptes.findByUsernameIgnoreCase("direction")).thenReturn(Optional.empty());
        when(encodeur.encode("Temporaire-2026!")).thenReturn("hash");
        when(comptes.save(any(StaffUser.class))).thenAnswer(i -> i.getArgument(0));

        avec(" direction ", "Temporaire-2026!").run(null);

        ArgumentCaptor<StaffUser> cree = ArgumentCaptor.forClass(StaffUser.class);
        verify(comptes).save(cree.capture());
        assertThat(cree.getValue().getUsername()).isEqualTo("direction");
        assertThat(cree.getValue().getRole()).isEqualTo(Role.MANAGER);
        assertThat(cree.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(cree.getValue().mustChangePassword()).isTrue();
        verify(journal).enregistrerEnTantQue(eq("systeme"), eq(JournalActions.Domaine.PERSONNEL), eq("CREATION"), any(), anyString());
    }

    @Test
    void neRecreePasUnManagerExistant() {
        when(comptes.existsByRole(Role.MANAGER)).thenReturn(true);

        avec("direction", "Temporaire-2026!").run(null);

        verify(comptes, never()).save(any());
        verifyNoInteractions(journal);
    }

    @Test
    void refuseUneConfigurationIncompleteOuFaible() {
        assertThatThrownBy(() -> avec("direction", "").run(null)).hasMessageContaining("ensemble");
        assertThatThrownBy(() -> avec("", "Temporaire-2026!").run(null)).hasMessageContaining("ensemble");
        assertThatThrownBy(() -> avec("direction", "court").run(null)).hasMessageContaining("12 caracteres");
    }

    @Test
    void refuseUnIdentifiantDejaPris() {
        when(comptes.existsByRole(Role.MANAGER)).thenReturn(false);
        when(comptes.findByUsernameIgnoreCase("gerant")).thenReturn(Optional.of(new StaffUser("gerant", "h", Role.GERANT)));

        assertThatThrownBy(() -> avec("gerant", "Temporaire-2026!").run(null)).hasMessageContaining("deja utilise");
        verify(comptes, never()).save(any());
    }
}
