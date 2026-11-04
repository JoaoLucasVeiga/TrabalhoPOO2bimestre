package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comandos de administração: /exportar, /kick e /limpar (clientes falsos, sem rede nem banco). */
class CommandServiceAdminTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5);

    @TempDir
    Path pasta;

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, RELOGIO);
    private final ClienteFalso chefe = ClienteFalso.identificado(registry, "chefe");
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");

    private CommandService servico(Administradores admins) {
        return new CommandService(chat, registry, new SalaService(chat, registry), null,
                new ExportadorHistorico(pasta.resolve("exportacoes"), RELOGIO), admins);
    }

    private CommandService servico() {
        return servico(Administradores.de(List.of("chefe")));
    }

    private static Evento ultimo(ClienteFalso c) {
        List<Evento> e = c.eventos();
        return e.get(e.size() - 1);
    }

    private void semear(String texto) {
        repo.salvar(Mensagem.nova(new Usuario("ana"), "geral", texto, HORA));
    }

    // ---------- /exportar ----------

    @Test
    void adminExportaOHistorico() {
        semear("olá");
        servico().executar(chefe.handler, "/exportar");
        assertInstanceOf(Evento.Ok.class, ultimo(chefe));
        assertTrue(Files.exists(pasta.resolve("exportacoes").resolve("geral-20261021-153000.txt")));
    }

    @Test
    void usuarioComumNaoExportaENaoGeraArquivo() {
        semear("olá");
        servico().executar(ana.handler, "/exportar");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(erro.texto().contains("administrador"));
        assertFalse(Files.exists(pasta.resolve("exportacoes")));
    }

    @Test
    void semAdminConfiguradoNinguemExporta() {
        semear("olá");
        servico(Administradores.nenhum()).executar(chefe.handler, "/exportar");
        assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertFalse(Files.exists(pasta.resolve("exportacoes")));
    }

    @Test
    void semRestricaoTodosExportamComoAntes() {
        semear("olá");
        servico(Administradores.semRestricao()).executar(ana.handler, "/exportar");
        assertInstanceOf(Evento.Ok.class, ultimo(ana));
    }

    // ---------- /kick ----------

    @Test
    void adminRemoveUmUsuarioQueFicaDesconectadoEAvisado() {
        servico().executar(chefe.handler, "/kick ana");
        assertEquals(1, ana.fechamentos.get());
        Evento.Erro aviso = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(aviso.texto().contains("chefe"));
        Evento.Ok ok = assertInstanceOf(Evento.Ok.class, ultimo(chefe));
        assertTrue(ok.texto().contains("ana"));
    }

    @Test
    void usuarioComumNaoRemoveNinguem() {
        servico().executar(ana.handler, "/kick chefe");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertEquals(0, chefe.fechamentos.get());
    }

    @Test
    void adminNaoRemoveAsiMesmoNemOutroAdmin() {
        ClienteFalso outro = ClienteFalso.identificado(registry, "dono");
        CommandService s = servico(Administradores.de(List.of("chefe", "dono")));
        s.executar(chefe.handler, "/kick chefe");
        s.executar(chefe.handler, "/kick dono");
        assertEquals(0, chefe.fechamentos.get());
        assertEquals(0, outro.fechamentos.get());
        assertInstanceOf(Evento.Erro.class, ultimo(chefe));
    }

    @Test
    void usuarioInexistenteOuOfflineDizOQueFazer() {
        servico().executar(chefe.handler, "/kick fulano");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertTrue(erro.texto().contains("fulano"));
        assertTrue(erro.texto().contains("/users"));
    }

    @Test
    void semArgumentoMostraOUso() {
        servico().executar(chefe.handler, "/kick");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertTrue(erro.texto().contains("Uso: /kick usuario"));
    }

    // ---------- /limpar ----------

    @Test
    void adminLimpaOHistoricoDaSalaAtualEVeAQuantidade() {
        semear("um");
        semear("dois");
        servico().executar(chefe.handler, "/limpar");
        assertEquals(List.of(), repo.buscarUltimas("geral", 10));
        Evento.Ok ok = assertInstanceOf(Evento.Ok.class, ultimo(chefe));
        assertTrue(ok.texto().contains("2"));
        assertTrue(ana.eventos().stream().anyMatch(e -> e instanceof Evento.Sistema s
                && s.aviso().contains("chefe")));
    }

    @Test
    void usuarioComumNaoLimpaNada() {
        semear("um");
        servico().executar(ana.handler, "/limpar");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertEquals(1, repo.buscarUltimas("geral", 10).size());
    }

    @Test
    void limparNaoAceitaArgumentos() {
        semear("um");
        servico().executar(chefe.handler, "/limpar geral");
        assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertEquals(1, repo.buscarUltimas("geral", 10).size());
    }

    // ---------- ajuda e compatibilidade ----------

    @Test
    void ajudaMarcaOsComandosDeAdmin() {
        servico().executar(ana.handler, "/help");
        Evento.Sistema ajuda = assertInstanceOf(Evento.Sistema.class, ultimo(ana));
        assertTrue(ajuda.aviso().contains("/kick usuario"));
        assertTrue(ajuda.aviso().contains("administrador"));
    }

    @Test
    void semRestricaoOsComandosDeAdminNaoExistem() {
        servico(Administradores.semRestricao()).executar(ana.handler, "/kick chefe");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(erro.texto().contains("desconhecido"));
    }
}
