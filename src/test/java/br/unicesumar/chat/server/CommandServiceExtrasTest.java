package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Comandos /msg, /buscar, /join, /leave e /rooms (clientes falsos, sem rede). */
class CommandServiceExtrasTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final ClientRegistry registry = new ClientRegistry();
    private final RepositorioEspiao repo = new RepositorioEspiao();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final CommandService comandos = new CommandService(chat, registry);
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
    private final ClienteFalso bia = ClienteFalso.identificado(registry, "bia");

    private Evento ultimo(ClienteFalso c) {
        List<Evento> e = c.eventos();
        return e.get(e.size() - 1);
    }

    private void semear(String quem, String sala, String texto, LocalDateTime hora) {
        repo.salvar(Mensagem.nova(new Usuario(quem), sala, texto, hora));
    }

    // ---------- /help ----------

    @Test
    void helpListaOsNovosComandos() {
        comandos.executar(ana.handler, "/help");
        String aviso = ((Evento.Sistema) ultimo(ana)).aviso();
        for (String c : List.of("/msg", "/buscar", "/join", "/leave", "/rooms")) {
            assertTrue(aviso.contains(c), "falta " + c);
        }
    }

    // ---------- /msg ----------

    @Test
    void msgEnviaAoDestinatarioEConfirmaAoRemetente() {
        comandos.executar(ana.handler, "/msg bia Olá, tudo bem? | com pipe");
        Evento.Privada p = (Evento.Privada) ultimo(bia);
        assertEquals("ana", p.de());
        assertEquals("bia", p.para());
        assertEquals("Olá, tudo bem? | com pipe", p.texto());
        assertEquals(p, ultimo(ana));
    }

    @Test
    void msgPreservaEspacosInternosDoTexto() {
        comandos.executar(ana.handler, "/msg bia   muitos   espaços  ");
        assertEquals("muitos   espaços", ((Evento.Privada) ultimo(bia)).texto());
    }

    @Test
    void msgSemTextoOuSemNomeMostraOUso() {
        for (String ruim : List.of("/msg", "/msg bia", "/msg bia   ")) {
            ClienteFalso c = ClienteFalso.identificado(registry, "c" + Math.abs(ruim.hashCode() % 997));
            comandos.executar(c.handler, ruim);
            Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c), ruim);
            assertTrue(e.texto().contains("/msg usuario texto"), ruim);
        }
    }

    @Test
    void msgParaUsuarioOfflineGeraErroENaoPersiste() {
        comandos.executar(ana.handler, "/msg zeca oi");
        Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(e.texto().contains("não está online"));
        assertTrue(repo.salvas().isEmpty());
    }

    @Test
    void msgParaSiMesmoGeraErro() {
        comandos.executar(ana.handler, "/msg ana oi");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(repo.salvas().isEmpty());
    }

    @Test
    void msgComMaisDe500CaracteresGeraErroComOLimite() {
        comandos.executar(ana.handler, "/msg bia " + "x".repeat(501));
        assertTrue(((Evento.Erro) ultimo(ana)).texto().contains("500"));
    }

    @Test
    void msgNaoChegaATerceiros() {
        ClienteFalso caio = ClienteFalso.identificado(registry, "caio");
        comandos.executar(ana.handler, "/msg bia segredo");
        assertTrue(caio.eventos().isEmpty());
    }

    // ---------- /buscar ----------

    @Test
    void buscarUsuarioDevolveAsMensagensDaquelePerfilNaSalaAtual() {
        semear("bia", "geral", "oi da bia", HORA);
        semear("ana", "geral", "oi da ana", HORA);
        comandos.executar(ana.handler, "/buscar usuario BIA");
        Evento.Historico h = (Evento.Historico) ultimo(ana);
        assertEquals(List.of("oi da bia"), h.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void buscarTextoAceitaVariasPalavras() {
        semear("bia", "geral", "vamos estudar java hoje", HORA);
        semear("bia", "geral", "vamos jogar", HORA);
        comandos.executar(ana.handler, "/buscar texto estudar java");
        Evento.Historico h = (Evento.Historico) ultimo(ana);
        assertEquals(1, h.mensagens().size());
    }

    @Test
    void buscarPeriodoUsaAsDuasDatasIncluindoOsDias() {
        semear("bia", "geral", "dentro", LocalDateTime.of(2026, 10, 31, 23, 0));
        semear("bia", "geral", "fora", LocalDateTime.of(2026, 11, 2, 10, 0));
        comandos.executar(ana.handler, "/buscar periodo 2026-10-01 2026-10-31");
        Evento.Historico h = (Evento.Historico) ultimo(ana);
        assertEquals(List.of("dentro"), h.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void buscarSemResultadoAvisaQueNaoEncontrouNada() {
        comandos.executar(ana.handler, "/buscar texto inexistente");
        Evento.Sistema s = assertInstanceOf(Evento.Sistema.class, ultimo(ana));
        assertTrue(s.aviso().toLowerCase().contains("nenhuma mensagem"));
    }

    @Test
    void buscarNaoMostraMensagensDeOutraSalaNemPrivadas() {
        semear("bia", "java", "do java", HORA);
        repo.salvar(Mensagem.privada(new Usuario("bia"), new Usuario("caio"), "geral", "segredo", HORA));
        comandos.executar(ana.handler, "/buscar texto o");
        assertInstanceOf(Evento.Sistema.class, ultimo(ana)); // nada visível na sala geral
        ana.handler.definirSala("java");
        comandos.executar(ana.handler, "/buscar texto o");
        assertEquals(1, ((Evento.Historico) ultimo(ana)).mensagens().size());
    }

    @Test
    void buscarComArgumentosInvalidosMostraOUsoCompleto() {
        for (String ruim : List.of("/buscar", "/buscar usuario", "/buscar usuario a b",
                "/buscar texto", "/buscar periodo", "/buscar periodo 2026-10-01",
                "/buscar periodo 2026-10-01 ontem", "/buscar periodo 2026-13-45 2026-14-01",
                "/buscar periodo 2026-10-31 2026-10-01", "/buscar assunto java")) {
            ClienteFalso c = ClienteFalso.identificado(registry, "u" + Math.abs(ruim.hashCode() % 9973));
            comandos.executar(c.handler, ruim);
            Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c), ruim);
            assertTrue(e.texto().contains("/buscar"), ruim);
        }
    }

    @Test
    void buscarNaoEnviaNadaAosOutros() {
        semear("bia", "geral", "oi", HORA);
        comandos.executar(ana.handler, "/buscar usuario bia");
        assertTrue(bia.eventos().isEmpty());
    }

    // ---------- /join /leave /rooms ----------

    @Test
    void joinMudaDeSalaELeaveVoltaParaAGeral() {
        comandos.executar(ana.handler, "/join Java");
        assertEquals("java", ana.handler.sala());
        comandos.executar(ana.handler, "/leave");
        assertEquals("geral", ana.handler.sala());
    }

    @Test
    void joinSemNomeOuComNomeInvalidoGeraErro() {
        comandos.executar(ana.handler, "/join");
        assertTrue(((Evento.Erro) ultimo(ana)).texto().contains("/join sala"));
        comandos.executar(ana.handler, "/join minha sala");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertEquals("geral", ana.handler.sala());
    }

    @Test
    void joinNaSalaAtualGeraErro() {
        comandos.executar(ana.handler, "/join geral");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
    }

    @Test
    void leaveNaGeralGeraErroQueDizOQueFazer() {
        comandos.executar(ana.handler, "/leave");
        assertTrue(((Evento.Erro) ultimo(ana)).texto().contains("/join"));
    }

    @Test
    void roomsListaAsSalasAtivas() {
        comandos.executar(bia.handler, "/join java");
        comandos.executar(ana.handler, "/rooms");
        Evento.Salas s = (Evento.Salas) ultimo(ana);
        assertEquals(List.of("geral", "java"), s.nomes());
    }

    @Test
    void usersVemDaSalaAtualDepoisDeTrocar() {
        comandos.executar(ana.handler, "/join java");
        comandos.executar(ana.handler, "/users");
        Evento.Usuarios u = (Evento.Usuarios) ultimo(ana);
        assertEquals("java", u.sala());
        assertEquals(List.of("ana"), u.nomes());
    }

    @Test
    void historyUsaASalaNovaDepoisDeTrocar() {
        semear("bia", "java", "recado no java", HORA);
        comandos.executar(ana.handler, "/join java");
        comandos.executar(ana.handler, "/history");
        Evento.Historico h = (Evento.Historico) ultimo(ana);
        assertEquals("recado no java", h.mensagens().get(0).texto());
    }
}
