package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Busca no histórico: regras do serviço sobre o repositório em memória (sem rede nem banco). */
class ChatServiceBuscaTest {

    private static final Usuario ANA = new Usuario("ana");
    private static final Usuario BIA = new Usuario("bia");

    private final RepositorioEspiao repo = new RepositorioEspiao();
    private final ChatService chat = new ChatService(repo, new ClientRegistry(), Clock.systemDefaultZone());

    private void salvar(Usuario quem, String sala, String texto, LocalDateTime hora) {
        repo.salvar(Mensagem.nova(quem, sala, texto, hora));
    }

    private static List<String> textos(List<Mensagem> ms) {
        return ms.stream().map(Mensagem::conteudo).toList();
    }

    @Test
    void buscaPorUsuarioDevolveAsMensagensDele() {
        salvar(ANA, "geral", "a1", LocalDateTime.of(2026, 10, 1, 9, 0));
        salvar(BIA, "geral", "b1", LocalDateTime.of(2026, 10, 1, 9, 0));
        assertEquals(List.of("a1"), textos(chat.buscarPorUsuario("geral", "ana")));
    }

    @Test
    void buscaPorUsuarioIgnoraEspacosNasPontas() {
        salvar(ANA, "geral", "a1", LocalDateTime.of(2026, 10, 1, 9, 0));
        assertEquals(1, chat.buscarPorUsuario("geral", "  ana ").size());
    }

    @Test
    void buscaPorUsuarioSemNomeLancaErroComOUso() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.buscarPorUsuario("geral", "  "));
        assertTrue(e.getMessage().contains("/buscar usuario"));
    }

    @Test
    void buscaPorTextoDevolveQuemContemOTrecho() {
        salvar(ANA, "geral", "aprendendo Java", LocalDateTime.of(2026, 10, 1, 9, 0));
        salvar(BIA, "geral", "tomando café", LocalDateTime.of(2026, 10, 1, 9, 0));
        assertEquals(List.of("aprendendo Java"), textos(chat.buscarPorTexto("geral", "java")));
    }

    @Test
    void buscaPorTextoSemTrechoLancaErroComOUso() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.buscarPorTexto("geral", ""));
        assertTrue(e.getMessage().contains("/buscar texto"));
    }

    @Test
    void buscaPorPeriodoIncluiODiaInteiroDoFim() {
        salvar(ANA, "geral", "dentro", LocalDateTime.of(2026, 10, 31, 23, 59, 59));
        salvar(ANA, "geral", "primeiro", LocalDateTime.of(2026, 10, 1, 0, 0, 0));
        salvar(ANA, "geral", "fora", LocalDateTime.of(2026, 11, 1, 0, 0, 0));
        List<Mensagem> r = chat.buscarPorPeriodo("geral", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(List.of("primeiro", "dentro"), textos(r));
    }

    @Test
    void buscaPorPeriodoDeUmDiaSoFunciona() {
        salvar(ANA, "geral", "hoje", LocalDateTime.of(2026, 10, 5, 15, 0));
        LocalDate dia = LocalDate.of(2026, 10, 5);
        assertEquals(1, chat.buscarPorPeriodo("geral", dia, dia).size());
    }

    @Test
    void buscaPorPeriodoComInicioDepoisDoFimLancaErroComOUso() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.buscarPorPeriodo("geral", LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)));
        assertTrue(e.getMessage().contains("/buscar periodo"));
    }

    @Test
    void buscaPorPeriodoComDatasNulasLancaErro() {
        assertThrows(IllegalArgumentException.class,
                () -> chat.buscarPorPeriodo("geral", null, LocalDate.of(2026, 10, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> chat.buscarPorPeriodo("geral", LocalDate.of(2026, 10, 1), null));
    }

    @Test
    void asBuscasDevolvemNoMaximoOLimiteDoServicoMantendoAsMaisRecentes() {
        for (int i = 1; i <= ChatService.BUSCA_MAXIMA + 10; i++) {
            salvar(ANA, "geral", "msg " + i, LocalDateTime.of(2026, 10, 1, 9, 0));
        }
        List<Mensagem> r = chat.buscarPorUsuario("geral", "ana");
        assertEquals(ChatService.BUSCA_MAXIMA, r.size());
        assertEquals("msg 11", r.get(0).conteudo());
        assertEquals("msg " + (ChatService.BUSCA_MAXIMA + 10), r.get(r.size() - 1).conteudo());
    }

    @Test
    void asBuscasSaoPorSala() {
        salvar(ANA, "geral", "na geral", LocalDateTime.of(2026, 10, 1, 9, 0));
        salvar(ANA, "java", "no java", LocalDateTime.of(2026, 10, 1, 9, 0));
        assertEquals(List.of("no java"), textos(chat.buscarPorUsuario("java", "ana")));
    }
}
