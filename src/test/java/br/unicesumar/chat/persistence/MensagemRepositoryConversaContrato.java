package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contrato das conversas privadas de {@link MensagemRepository}: cada adaptador (memória e JDBC)
 * estende esta classe. Uma conversa é o conjunto de privadas entre dois usuários, nos dois sentidos.
 */
public abstract class MensagemRepositoryConversaContrato {

    private static final Usuario ANA = new Usuario("ana");
    private static final Usuario BIA = new Usuario("bia");
    private static final Usuario CAIO = new Usuario("caio");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0, 0);

    /** Cria um repositório novo e vazio. */
    protected abstract MensagemRepository criarRepositorio();

    private static void privada(MensagemRepository repo, Usuario de, Usuario para, String texto) {
        repo.salvar(Mensagem.privada(de, para, "geral", texto, HORA));
    }

    private static List<String> textos(List<Mensagem> ms) {
        return ms.stream().map(Mensagem::conteudo).toList();
    }

    // ---------- buscarConversa ----------

    @Test
    void conversaTemOsDoisSentidosEmOrdemDeEnvio() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, BIA, "oi bia");
        privada(repo, BIA, ANA, "oi ana");
        privada(repo, ANA, BIA, "tudo bem?");
        assertEquals(List.of("oi bia", "oi ana", "tudo bem?"), textos(repo.buscarConversa("ana", "bia", 50)));
    }

    @Test
    void conversaEIgualNaOrdemDosNomes() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, BIA, "um");
        privada(repo, BIA, ANA, "dois");
        assertEquals(textos(repo.buscarConversa("ana", "bia", 50)), textos(repo.buscarConversa("bia", "ana", 50)));
    }

    @Test
    void conversaIgnoraMaiusculasNosNomes() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, BIA, "um");
        assertEquals(1, repo.buscarConversa("ANA", "Bia", 50).size());
    }

    @Test
    void conversaNaoMisturaMensagensPublicasNemDeOutrosPares() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(Mensagem.nova(ANA, "geral", "publica", HORA));
        privada(repo, ANA, CAIO, "so com caio");
        privada(repo, CAIO, BIA, "entre caio e bia");
        privada(repo, ANA, BIA, "so ana e bia");
        assertEquals(List.of("so ana e bia"), textos(repo.buscarConversa("ana", "bia", 50)));
    }

    @Test
    void conversaDevolveAsUltimasQuandoHaMaisQueOLimite() {
        MensagemRepository repo = criarRepositorio();
        for (int i = 1; i <= 5; i++) {
            privada(repo, i % 2 == 0 ? ANA : BIA, i % 2 == 0 ? BIA : ANA, "m" + i);
        }
        assertEquals(List.of("m4", "m5"), textos(repo.buscarConversa("ana", "bia", 2)));
    }

    @Test
    void conversaSemMensagensOuComQuantidadeZeroDevolveVazio() {
        MensagemRepository repo = criarRepositorio();
        assertEquals(List.of(), repo.buscarConversa("ana", "bia", 10));
        privada(repo, ANA, BIA, "um");
        assertEquals(List.of(), repo.buscarConversa("ana", "bia", 0));
    }

    @Test
    void conversaRecusaArgumentosInvalidos() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.buscarConversa("ana", "bia", -1));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarConversa(null, "bia", 10));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarConversa("ana", " ", 10));
    }

    // ---------- listarConversas ----------

    @Test
    void listaOsInterlocutoresComAMaisRecenteNaFrente() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, BIA, "1");
        privada(repo, CAIO, ANA, "2");
        privada(repo, BIA, ANA, "3");
        assertEquals(List.of("bia", "caio"), repo.listarConversas("ana"));
    }

    @Test
    void cadaInterlocutorApareceUmaSoVezNosDoisSentidos() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, BIA, "1");
        privada(repo, BIA, ANA, "2");
        privada(repo, ANA, BIA, "3");
        assertEquals(List.of("bia"), repo.listarConversas("ana"));
    }

    @Test
    void listaSoConversasDoUsuarioEIgnoraPublicas() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(Mensagem.nova(ANA, "geral", "publica", HORA));
        privada(repo, BIA, CAIO, "nao envolve a ana");
        assertEquals(List.of(), repo.listarConversas("ana"));
        assertEquals(List.of("caio"), repo.listarConversas("bia"));
    }

    @Test
    void listaIgnoraMaiusculasNoNomeDoUsuarioEUsaOsNomesGravados() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, new Usuario("Bia"), "1");
        assertEquals(List.of("Bia"), repo.listarConversas("ANA"));
    }

    @Test
    void listaRecusaNomeNuloOuEmBranco() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.listarConversas(null));
        assertThrows(IllegalArgumentException.class, () -> repo.listarConversas("  "));
    }

    @Test
    void limparASalaNaoApagaConversasPrivadas() {
        MensagemRepository repo = criarRepositorio();
        privada(repo, ANA, BIA, "segredo");
        repo.apagarSala("geral");
        assertEquals(List.of("segredo"), textos(repo.buscarConversa("ana", "bia", 10)));
    }
}
