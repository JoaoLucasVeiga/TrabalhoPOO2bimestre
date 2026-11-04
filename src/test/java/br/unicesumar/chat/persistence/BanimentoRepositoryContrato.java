package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Contrato que toda implementação de {@link BanimentoRepository} deve cumprir. */
public abstract class BanimentoRepositoryContrato {

    /** Cria um repositório novo e vazio. */
    protected abstract BanimentoRepository criarRepositorio();

    @Test
    void adicionarNomeNovoDevolveTrueERepetidoDevolveFalse() {
        BanimentoRepository repo = criarRepositorio();
        assertTrue(repo.adicionar("ana"));
        assertFalse(repo.adicionar("ana"));
    }

    @Test
    void repeticaoIgnoraMaiusculas() {
        BanimentoRepository repo = criarRepositorio();
        repo.adicionar("Ana");
        assertFalse(repo.adicionar("ANA"));
        assertTrue(repo.existe("ana"));
    }

    @Test
    void existeSoDepoisDeAdicionarEDeixaDeExistirDepoisDeRemover() {
        BanimentoRepository repo = criarRepositorio();
        assertFalse(repo.existe("ana"));
        repo.adicionar("ana");
        assertTrue(repo.existe("ana"));
        assertTrue(repo.remover("ana"));
        assertFalse(repo.existe("ana"));
    }

    @Test
    void removerQuemNaoEstaBanidoDevolveFalse() {
        assertFalse(criarRepositorio().remover("ana"));
    }

    @Test
    void removerIgnoraMaiusculas() {
        BanimentoRepository repo = criarRepositorio();
        repo.adicionar("Ana");
        assertTrue(repo.remover("ANA"));
    }

    @Test
    void listarDevolveEmOrdemAlfabeticaSemDiferenciarMaiusculas() {
        BanimentoRepository repo = criarRepositorio();
        repo.adicionar("caio");
        repo.adicionar("Ana");
        repo.adicionar("bia");
        assertEquals(List.of("Ana", "bia", "caio"), repo.listar());
    }

    @Test
    void listarVazioDevolveListaVazia() {
        assertEquals(List.of(), criarRepositorio().listar());
    }

    @Test
    void nomeNuloOuEmBrancoEhRecusadoAoAdicionarEExisteDevolveFalse() {
        BanimentoRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.adicionar(null));
        assertThrows(IllegalArgumentException.class, () -> repo.adicionar("  "));
        assertFalse(repo.existe(null));
        assertFalse(repo.existe("  "));
    }
}
