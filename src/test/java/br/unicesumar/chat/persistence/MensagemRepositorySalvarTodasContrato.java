package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contrato de {@link MensagemRepository#salvarTodas}: grava várias mensagens de uma vez, em ordem. */
public abstract class MensagemRepositorySalvarTodasContrato {

    private static final Usuario ANA = new Usuario("ana");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0, 0);

    /** Cria um repositório novo e vazio. */
    protected abstract MensagemRepository criarRepositorio();

    private static Mensagem msg(String texto) {
        return Mensagem.nova(ANA, "geral", texto, HORA);
    }

    @Test
    void devolveAsMensagensComIdEmOrdemCrescente() {
        MensagemRepository repo = criarRepositorio();
        List<Mensagem> salvas = repo.salvarTodas(List.of(msg("a"), msg("b"), msg("c")));
        assertEquals(List.of("a", "b", "c"), salvas.stream().map(Mensagem::conteudo).toList());
        for (int i = 0; i < salvas.size(); i++) {
            assertNotNull(salvas.get(i).id());
            if (i > 0) {
                assertTrue(salvas.get(i).id() > salvas.get(i - 1).id());
            }
        }
    }

    @Test
    void todasFicamGravadasNaMesmaOrdemDoHistorico() {
        MensagemRepository repo = criarRepositorio();
        repo.salvarTodas(List.of(msg("a"), msg("b"), msg("c")));
        assertEquals(List.of("a", "b", "c"),
                repo.buscarUltimas("geral", 10).stream().map(Mensagem::conteudo).toList());
    }

    @Test
    void listaVaziaNaoGravaNadaEDevolveVazio() {
        MensagemRepository repo = criarRepositorio();
        assertEquals(List.of(), repo.salvarTodas(List.of()));
        assertEquals(List.of(), repo.buscarUltimas("geral", 10));
    }

    @Test
    void listaNulaOuComElementoNuloEhRecusada() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(NullPointerException.class, () -> repo.salvarTodas(null));
        List<Mensagem> comNulo = new ArrayList<>();
        comNulo.add(msg("a"));
        comNulo.add(null);
        assertThrows(NullPointerException.class, () -> repo.salvarTodas(comNulo));
        assertEquals(List.of(), repo.buscarUltimas("geral", 10), "nada pode ser gravado se o lote é inválido");
    }

    @Test
    void mensagensPrivadasTambemPodemIrNoLoteESoApareceNaConversa() {
        MensagemRepository repo = criarRepositorio();
        repo.salvarTodas(List.of(msg("publica"),
                Mensagem.privada(ANA, new Usuario("bia"), "geral", "segredo", HORA)));
        assertEquals(1, repo.buscarUltimas("geral", 10).size());
        assertEquals(1, repo.buscarConversa("ana", "bia", 10).size());
    }
}
