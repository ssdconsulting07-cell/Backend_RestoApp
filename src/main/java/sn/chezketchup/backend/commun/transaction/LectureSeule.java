package sn.chezketchup.backend.commun.transaction;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction en lecture seule ouverte a la demande.
 *
 * Pour les lectures en cache : un @Transactional pose sur la classe ouvre la
 * transaction AVANT de consulter le cache, donc emprunte une connexion et
 * envoie SET autocommit / SET SESSION TRANSACTION READ ONLY / COMMIT a MySQL
 * meme quand le cache repond (constate par load-tests/lecture-menu.js). Ici,
 * la transaction n'est ouverte que si la base est vraiment lue.
 */
@Component
public class LectureSeule {

    private final TransactionTemplate modele;

    public LectureSeule(PlatformTransactionManager transactions) {
        this.modele = new TransactionTemplate(transactions);
        this.modele.setReadOnly(true);
    }

    public <T> T executer(Supplier<T> lecture) {
        return modele.execute(statut -> lecture.get());
    }
}
