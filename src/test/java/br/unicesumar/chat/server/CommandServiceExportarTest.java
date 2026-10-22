package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /exportar: comando + arquivo em pasta temporária (clientes falsos, sem rede nem banco). */
class CommandServiceExportarTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5);

    @TempDir
    Path pasta;

    private final ClientRegistry registry = new ClientRegistry();
    private final RepositorioEspiao repo = new RepositorioEspiao();
    private final ChatService chat = new ChatService(repo, registry, RELOGIO);
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");

    private CommandService comExportador() {
        return new CommandService(chat, registry, new SalaService(chat, registry), null,
                new ExportadorHistorico(pasta.resolve("exportacoes"), RELOGIO));
    }

    private Evento ultimo() {
        List<Evento> e = ana.eventos();
        return e.get(e.size() - 1);
    }

    private void semear(String quem, String sala, String texto) {
        repo.salvar(Mensagem.nova(new Usuario(quem), sala, texto, HORA));
    }

    @Test
    void exportaOHistoricoDaSalaAtualNoFormatoPadraoEAvisaOArquivo() throws IOException {
        semear("ana", "geral", "olá");
        semear("bia", "geral", "oi, ana");
        comExportador().executar(ana.handler, "/exportar");
        Evento.Ok ok = assertInstanceOf(Evento.Ok.class, ultimo());
        Path arquivo = pasta.resolve("exportacoes").resolve("geral-20261021-153000.txt");
        assertTrue(ok.texto().contains("geral-20261021-153000.txt"));
        assertEquals("Histórico da sala geral\nExportado em 2026-10-21 15:30:00\n\n"
                + "[09:05] ana: olá\n[09:05] bia: oi, ana\n", Files.readString(arquivo));
    }

    @Test
    void exportaSoAsMensagensDaSalaAtual() throws IOException {
        semear("ana", "geral", "na geral");
        semear("ana", "java", "no java");
        ana.handler.definirSala("java");
        comExportador().executar(ana.handler, "/exportar");
        String conteudo = Files.readString(pasta.resolve("exportacoes").resolve("java-20261021-153000.txt"));
        assertTrue(conteudo.contains("no java"));
        assertFalse(conteudo.contains("na geral"));
    }

    @Test
    void nuncaExportaMensagensPrivadas() throws IOException {
        semear("ana", "geral", "publica");
        repo.salvar(Mensagem.privada(new Usuario("ana"), new Usuario("bia"), "geral", "segredo", HORA));
        comExportador().executar(ana.handler, "/exportar");
        String conteudo = Files.readString(pasta.resolve("exportacoes").resolve("geral-20261021-153000.txt"));
        assertTrue(conteudo.contains("publica"));
        assertFalse(conteudo.contains("segredo"));
    }

    @Test
    void salaSemMensagensGeraErroENaoCriaArquivo() throws IOException {
        comExportador().executar(ana.handler, "/exportar");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo());
        assertTrue(erro.texto().contains("Não há mensagens"));
        assertFalse(Files.exists(pasta.resolve("exportacoes")));
    }

    @Test
    void exportaNoMaximoAsUltimasMilMensagensEAvisa() throws IOException {
        for (int i = 1; i <= CommandService.EXPORTACAO_MAXIMA + 5; i++) {
            semear("ana", "geral", "m" + i);
        }
        comExportador().executar(ana.handler, "/exportar");
        Evento.Ok ok = assertInstanceOf(Evento.Ok.class, ultimo());
        assertTrue(ok.texto().contains(String.valueOf(CommandService.EXPORTACAO_MAXIMA)));
        List<String> linhas = Files.readAllLines(pasta.resolve("exportacoes").resolve("geral-20261021-153000.txt"));
        assertEquals(3 + CommandService.EXPORTACAO_MAXIMA, linhas.size());
        assertTrue(linhas.get(3).endsWith("m6"));
    }

    @Test
    void falhaDeGravacaoViraErroAoClienteSemDerrubarNada() throws IOException {
        semear("ana", "geral", "oi");
        Path arquivoNoLugarDaPasta = Files.writeString(pasta.resolve("exportacoes"), "sou um arquivo");
        assertTrue(Files.isRegularFile(arquivoNoLugarDaPasta));
        comExportador().executar(ana.handler, "/exportar");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo());
        assertTrue(erro.texto().contains("Não foi possível gravar"));
        assertTrue(ana.handler.ativo());
    }

    @Test
    void exportarComArgumentosMostraOUso() {
        comExportador().executar(ana.handler, "/exportar tudo");
        assertTrue(((Evento.Erro) ultimo()).texto().contains("Uso: /exportar"));
    }

    @Test
    void semExportadorConfiguradoOComandoNaoExiste() {
        new CommandService(chat, registry).executar(ana.handler, "/exportar");
        assertTrue(((Evento.Erro) ultimo()).texto().contains("Comando desconhecido"));
    }

    @Test
    void helpListaOExportarQuandoConfigurado() {
        comExportador().executar(ana.handler, "/help");
        assertTrue(((Evento.Sistema) ultimo()).aviso().contains("/exportar"));
    }

    @Test
    void noModoComLoginExportarExigeTerEntrado() {
        CommandService comLogin = new CommandService(chat, registry, new SalaService(chat, registry),
                new AuthService(new UsuarioRepositoryEmMemoria(), 1000),
                new ExportadorHistorico(pasta.resolve("exportacoes"), RELOGIO));
        ClienteFalso anonimo = ClienteFalso.novo();
        registry.adicionar(anonimo.handler);
        comLogin.executar(anonimo.handler, "/exportar");
        assertTrue(((Evento.Erro) anonimo.eventos().get(0)).texto().contains("/login"));
        assertFalse(Files.exists(pasta.resolve("exportacoes")));
    }

    @Test
    void doisClientesExportandoNaMesmaHoraGeramArquivosDiferentes() throws IOException {
        semear("ana", "geral", "oi");
        CommandService comandos = comExportador();
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        comandos.executar(ana.handler, "/exportar");
        comandos.executar(bia.handler, "/exportar");
        try (Stream<Path> arquivos = Files.list(pasta.resolve("exportacoes"))) {
            assertEquals(2, arquivos.count());
        }
    }
}
