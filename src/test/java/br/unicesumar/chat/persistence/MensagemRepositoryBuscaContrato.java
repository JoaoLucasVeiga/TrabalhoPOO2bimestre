package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contrato das buscas de {@link MensagemRepository}: cada adaptador (memória e JDBC)
 * estende esta classe. Toda busca é de mensagens públicas de uma sala.
 */
public abstract class MensagemRepositoryBuscaContrato {

    protected static final Usuario ANA = new Usuario("ana");
    protected static final Usuario BIA = new Usuario("bia");
    protected static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 9, 0, 0);

    /** Cria um repositório novo e vazio. */
    protected abstract MensagemRepository criarRepositorio();

    private static void salvar(MensagemRepository repo, Usuario quem, String sala, String texto,
            LocalDateTime hora) {
        repo.salvar(Mensagem.nova(quem, sala, texto, hora));
    }

    private static List<String> textos(List<Mensagem> ms) {
        return ms.stream().map(Mensagem::conteudo).toList();
    }

    // ---------- por usuário ----------

    @Test
    void porUsuarioDevolveSoAsMensagensDeQuemFoiPedidoEmOrdem() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "a1", BASE);
        salvar(repo, BIA, "geral", "b1", BASE);
        salvar(repo, ANA, "geral", "a2", BASE);
        assertEquals(List.of("a1", "a2"), textos(repo.buscarPorUsuario("geral", "ana", 50)));
    }

    @Test
    void porUsuarioIgnoraMaiusculasNoNome() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, new Usuario("Ana"), "geral", "oi", BASE);
        assertEquals(1, repo.buscarPorUsuario("geral", "ANA", 50).size());
    }

    @Test
    void porUsuarioExigeONomeInteiro() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "oi", BASE);
        assertTrue(repo.buscarPorUsuario("geral", "an", 50).isEmpty());
        assertTrue(repo.buscarPorUsuario("geral", "ana2", 50).isEmpty());
    }

    @Test
    void porUsuarioFiltraPelaSala() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "na geral", BASE);
        salvar(repo, ANA, "java", "no java", BASE);
        assertEquals(List.of("no java"), textos(repo.buscarPorUsuario("java", "ana", 50)));
    }

    @Test
    void porUsuarioNuncaDevolveMensagemPrivadaDele() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "publica", BASE);
        repo.salvar(Mensagem.privada(ANA, BIA, "geral", "segredo", BASE));
        repo.salvar(Mensagem.privada(BIA, ANA, "geral", "resposta secreta", BASE));
        assertEquals(List.of("publica"), textos(repo.buscarPorUsuario("geral", "ana", 50)));
        assertEquals(List.of(), textos(repo.buscarPorUsuario("geral", "bia", 50)));
    }

    @Test
    void porUsuarioRespeitaOLimiteMantendoAsMaisRecentes() {
        MensagemRepository repo = criarRepositorio();
        for (int i = 1; i <= 5; i++) {
            salvar(repo, ANA, "geral", "m" + i, BASE);
        }
        assertEquals(List.of("m4", "m5"), textos(repo.buscarPorUsuario("geral", "ana", 2)));
    }

    @Test
    void porUsuarioSemResultadoDevolveListaVazia() {
        assertTrue(criarRepositorio().buscarPorUsuario("geral", "ninguem", 50).isEmpty());
    }

    @Test
    void porUsuarioComArgumentosInvalidosLancaExcecao() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorUsuario("geral", null, 50));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorUsuario("geral", "  ", 50));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorUsuario("geral", "ana", 0));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorUsuario("geral", "ana", -1));
    }

    // ---------- por texto ----------

    @Test
    void porTextoEncontraQuemContemOTrechoSemDiferenciarMaiusculasAscii() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "Java é legal", BASE);
        salvar(repo, BIA, "geral", "JAVA!", BASE);
        salvar(repo, ANA, "geral", "python", BASE);
        assertEquals(List.of("Java é legal", "JAVA!"), textos(repo.buscarPorTexto("geral", "java", 50)));
    }

    @Test
    void porTextoEncontraNoMeioDaFrase() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "eu gosto de pizza", BASE);
        assertEquals(1, repo.buscarPorTexto("geral", "gosto de", 50).size());
    }

    @Test
    void porTextoTrataPorcentoEUnderlineComoCaracteresComuns() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "100% certo", BASE);
        salvar(repo, ANA, "geral", "a_b", BASE);
        salvar(repo, ANA, "geral", "axb", BASE);
        assertEquals(List.of("100% certo"), textos(repo.buscarPorTexto("geral", "%", 50)));
        assertEquals(List.of("a_b"), textos(repo.buscarPorTexto("geral", "a_b", 50)));
    }

    @Test
    void porTextoComAspasENaoQuebraNemInjetaSql() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "it's ok", BASE);
        assertEquals(1, repo.buscarPorTexto("geral", "it's", 50).size());
        assertTrue(repo.buscarPorTexto("geral", "x' OR '1'='1", 50).isEmpty());
        assertEquals(1, repo.buscarPorTexto("geral", "it's", 50).size()); // tabela segue intacta
    }

    @Test
    void porTextoEncontraAcentosComAMesmaCaixa() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "coração e ação", BASE);
        assertEquals(1, repo.buscarPorTexto("geral", "ação", 50).size());
    }

    @Test
    void porTextoNuncaDevolveMensagemPrivada() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "senha do java", BASE);
        repo.salvar(Mensagem.privada(ANA, BIA, "geral", "segredo java", BASE));
        assertEquals(List.of("senha do java"), textos(repo.buscarPorTexto("geral", "java", 50)));
    }

    @Test
    void porTextoFiltraPelaSalaERespeitaOLimite() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "x1", BASE);
        salvar(repo, ANA, "java", "x2", BASE);
        salvar(repo, ANA, "geral", "x3", BASE);
        salvar(repo, ANA, "geral", "x4", BASE);
        assertEquals(List.of("x3", "x4"), textos(repo.buscarPorTexto("geral", "x", 2)));
        assertEquals(List.of("x2"), textos(repo.buscarPorTexto("java", "x", 50)));
    }

    @Test
    void porTextoComArgumentosInvalidosLancaExcecao() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorTexto("geral", null, 50));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorTexto("geral", "", 50));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorTexto("geral", "  ", 50));
        assertThrows(IllegalArgumentException.class, () -> repo.buscarPorTexto("geral", "x", 0));
    }

    // ---------- por período ----------

    private MensagemRepository comMensagensDeOutubro() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "antes", LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        salvar(repo, ANA, "geral", "inicio", LocalDateTime.of(2026, 10, 1, 0, 0, 0));
        salvar(repo, ANA, "geral", "meio", LocalDateTime.of(2026, 10, 15, 12, 0, 0));
        salvar(repo, ANA, "geral", "fim", LocalDateTime.of(2026, 10, 31, 23, 59, 59));
        salvar(repo, ANA, "geral", "depois", LocalDateTime.of(2026, 11, 1, 0, 0, 0));
        return repo;
    }

    @Test
    void porPeriodoDevolveSoOQueEstaDentroDasDatasIncluindoAsBordas() {
        MensagemRepository repo = comMensagensDeOutubro();
        List<Mensagem> r = repo.buscarPorPeriodo("geral", LocalDateTime.of(2026, 10, 1, 0, 0),
                fimDoDia(2026, 10, 31), 50);
        assertEquals(List.of("inicio", "meio", "fim"), textos(r));
    }

    private static LocalDateTime fimDoDia(int ano, int mes, int dia) {
        return java.time.LocalDate.of(ano, mes, dia).atTime(LocalTime.MAX);
    }

    @Test
    void porPeriodoIncluiExatamenteOHorarioDeInicioEDeFim() {
        MensagemRepository repo = comMensagensDeOutubro();
        LocalDateTime meio = LocalDateTime.of(2026, 10, 15, 12, 0, 0);
        assertEquals(List.of("meio"), textos(repo.buscarPorPeriodo("geral", meio, meio, 50)));
    }

    @Test
    void porPeriodoSemMensagensDevolveListaVazia() {
        MensagemRepository repo = comMensagensDeOutubro();
        assertTrue(repo.buscarPorPeriodo("geral", LocalDateTime.of(2027, 1, 1, 0, 0),
                LocalDateTime.of(2027, 1, 2, 0, 0), 50).isEmpty());
    }

    @Test
    void porPeriodoNuncaDevolvePrivadasEFiltraPelaSala() {
        MensagemRepository repo = criarRepositorio();
        salvar(repo, ANA, "geral", "publica", BASE);
        salvar(repo, ANA, "java", "outra sala", BASE);
        repo.salvar(Mensagem.privada(ANA, BIA, "geral", "segredo", BASE));
        List<Mensagem> r = repo.buscarPorPeriodo("geral", BASE.minusDays(1), BASE.plusDays(1), 50);
        assertEquals(List.of("publica"), textos(r));
    }

    @Test
    void porPeriodoRespeitaOLimiteMantendoAsMaisRecentes() {
        MensagemRepository repo = comMensagensDeOutubro();
        List<Mensagem> r = repo.buscarPorPeriodo("geral", LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 0, 0), 2);
        assertEquals(List.of("fim", "depois"), textos(r));
    }

    @Test
    void porPeriodoComArgumentosInvalidosLancaExcecao() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class,
                () -> repo.buscarPorPeriodo("geral", null, BASE, 50));
        assertThrows(IllegalArgumentException.class,
                () -> repo.buscarPorPeriodo("geral", BASE, null, 50));
        assertThrows(IllegalArgumentException.class,
                () -> repo.buscarPorPeriodo("geral", BASE.plusDays(1), BASE, 50));
        assertThrows(IllegalArgumentException.class,
                () -> repo.buscarPorPeriodo("geral", BASE, BASE.plusDays(1), 0));
    }
}
