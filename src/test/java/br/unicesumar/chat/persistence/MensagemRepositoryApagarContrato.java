package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contrato de {@link MensagemRepository#apagarSala}: cada adaptador (memória e JDBC) estende esta classe. */
public abstract class MensagemRepositoryApagarContrato {

    private static final Usuario ANA = new Usuario("ana");
    private static final Usuario BIA = new Usuario("bia");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0, 0);

    /** Cria um repositório novo e vazio. */
    protected abstract MensagemRepository criarRepositorio();

    private static List<String> textos(List<Mensagem> ms) {
        return ms.stream().map(Mensagem::conteudo).toList();
    }

    @Test
    void apagaSoAsMensagensPublicasDaSalaEDevolveQuantas() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(Mensagem.nova(ANA, "geral", "a", HORA));
        repo.salvar(Mensagem.nova(BIA, "geral", "b", HORA));
        repo.salvar(Mensagem.nova(ANA, "java", "c", HORA));
        assertEquals(2, repo.apagarSala("geral"));
        assertEquals(List.of(), repo.buscarUltimas("geral", 10));
        assertEquals(List.of("c"), textos(repo.buscarUltimas("java", 10)));
    }

    @Test
    void naoApagaMensagensPrivadasDaSala() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(Mensagem.nova(ANA, "geral", "publica", HORA));
        repo.salvar(Mensagem.privada(ANA, BIA, "geral", "segredo", HORA));
        assertEquals(1, repo.apagarSala("geral"));
        assertEquals(0, repo.apagarSala("geral"));
    }

    @Test
    void salaSemMensagensDevolveZero() {
        assertEquals(0, criarRepositorio().apagarSala("vazia"));
    }

    @Test
    void depoisDeApagarAsNovasMensagensFuncionamNormalmente() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(Mensagem.nova(ANA, "geral", "velha", HORA));
        repo.apagarSala("geral");
        repo.salvar(Mensagem.nova(ANA, "geral", "nova", HORA));
        assertEquals(List.of("nova"), textos(repo.buscarUltimas("geral", 10)));
    }

    @Test
    void salaNulaOuEmBrancoEhRecusada() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.apagarSala(null));
        assertThrows(IllegalArgumentException.class, () -> repo.apagarSala("  "));
    }
}
