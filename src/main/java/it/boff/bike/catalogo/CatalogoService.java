package it.boff.bike.catalogo;

import it.boff.bike.common.ErroreDominio;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Catalogo degli interventi. Le voci non si cancellano (sono usate nei lavori passati):
 * si disattivano, e non vengono più proposte.
 */
@Service
@Validated
@Transactional
public class CatalogoService {

    private final TipoInterventoRepository repository;

    public CatalogoService(TipoInterventoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TipoIntervento> tutti() {
        return repository.findAllByOrderByOrdineAscNomeAsc();
    }

    @Transactional(readOnly = true)
    public List<TipoIntervento> attivi() {
        return repository.findByAttivoTrueOrderByOrdineAscNomeAsc();
    }

    public TipoIntervento salva(@Valid TipoIntervento tipo) {
        tipo.setNome(tipo.getNome().trim());
        boolean duplicato = tipo.getId() == null
                ? repository.existsByNomeIgnoreCase(tipo.getNome())
                : repository.existsByNomeIgnoreCaseAndIdNot(tipo.getNome(), tipo.getId());
        if (duplicato) {
            throw new ErroreDominio("Esiste già un intervento con questo nome");
        }
        if (tipo.getId() == null && tipo.getOrdine() == 0) {
            tipo.setOrdine(repository.findAll().stream().mapToInt(TipoIntervento::getOrdine).max().orElse(0) + 10);
        }
        return repository.save(tipo);
    }
}
