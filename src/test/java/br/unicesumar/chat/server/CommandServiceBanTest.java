package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.BanimentoRepositoryEmMemoria;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** /ban, /unban e /bans (clientes falsos, sem rede nem banco). */
class CommandServiceBanTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
            Clock.systemDefaultZone());
    private final AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000,
            new BanimentoRepositoryEmMemoria());
    private final CommandService comandos = new CommandService(chat, registry, new SalaService(chat, registry),
            auth, null, Administradores.de(List.of("chefe")));
    private final ClienteFalso chefe = ClienteFalso.identificado(registry, "chefe");
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");

    private static Evento ultimo(ClienteFalso c) {
        List<Evento> e = c.eventos();
        return e.get(e.size() - 1);
    }

    private ClienteFalso visitante() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        return c;
    }

    @Test
    void banirQuemEstaOnlineBaneEDesconecta() {
        comandos.executar(chefe.handler, "/ban ana");
        assertTrue(auth.estaBanido("ana"));
        assertEquals(1, ana.fechamentos.get());
        Evento.Erro aviso = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(aviso.texto().contains("banido"));
        Evento.Ok ok = assertInstanceOf(Evento.Ok.class, ultimo(chefe));
        assertTrue(ok.texto().contains("ana"));
    }

    @Test
    void banirQuemEstaOfflineSoRegistraOBanimento() {
        comandos.executar(chefe.handler, "/ban fulano");
        assertTrue(auth.estaBanido("fulano"));
        assertInstanceOf(Evento.Ok.class, ultimo(chefe));
    }

    @Test
    void usuarioComumNaoBane() {
        comandos.executar(ana.handler, "/ban chefe");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertFalse(auth.estaBanido("chefe"));
    }

    @Test
    void adminNaoBaneASiMesmoNemOutroAdmin() {
        comandos.executar(chefe.handler, "/ban chefe");
        assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertFalse(auth.estaBanido("chefe"));
    }

    @Test
    void banirDeNovoDizQueJaEstaBanido() {
        comandos.executar(chefe.handler, "/ban fulano");
        comandos.executar(chefe.handler, "/ban fulano");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertTrue(erro.texto().contains("já está banido"));
    }

    @Test
    void semArgumentoOuNomeInvalidoMostraOUso() {
        comandos.executar(chefe.handler, "/ban");
        assertTrue(assertInstanceOf(Evento.Erro.class, ultimo(chefe)).texto().contains("Uso: /ban usuario"));
        comandos.executar(chefe.handler, "/ban a");
        assertInstanceOf(Evento.Erro.class, ultimo(chefe));
    }

    @Test
    void desbanirDevolveOAcessoEAvisaQuandoNaoEstavaBanido() {
        comandos.executar(chefe.handler, "/ban fulano");
        comandos.executar(chefe.handler, "/unban fulano");
        assertFalse(auth.estaBanido("fulano"));
        assertInstanceOf(Evento.Ok.class, ultimo(chefe));
        comandos.executar(chefe.handler, "/unban fulano");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(chefe));
        assertTrue(erro.texto().contains("/bans"));
    }

    @Test
    void usuarioComumNaoDesbaneNemListaBanidos() {
        auth.banir("fulano");
        comandos.executar(ana.handler, "/unban fulano");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(auth.estaBanido("fulano"));
        comandos.executar(ana.handler, "/bans");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
    }

    @Test
    void bansListaOsBanidosOuDizQueNaoHa() {
        comandos.executar(chefe.handler, "/bans");
        assertInstanceOf(Evento.Sistema.class, ultimo(chefe));
        assertTrue(((Evento.Sistema) ultimo(chefe)).aviso().contains("Nenhum"));
        auth.banir("caio");
        auth.banir("bia");
        comandos.executar(chefe.handler, "/bans");
        String aviso = ((Evento.Sistema) ultimo(chefe)).aviso();
        assertTrue(aviso.contains("bia, caio"));
    }

    @Test
    void banidoNaoConsegueEntrarNemCriarContaPelosComandos() {
        auth.registrar("zeca", "senha123");
        auth.banir("zeca");
        ClienteFalso v1 = visitante();
        comandos.executar(v1.handler, "/login zeca senha123");
        assertTrue(assertInstanceOf(Evento.Erro.class, ultimo(v1)).texto().contains("banida"));
        assertTrue(v1.handler.usuario().isEmpty());
        ClienteFalso v2 = visitante();
        comandos.executar(v2.handler, "/register zeca senha123");
        assertInstanceOf(Evento.Erro.class, ultimo(v2));
        assertTrue(v2.handler.usuario().isEmpty());
    }

    @Test
    void ajudaListaOsComandosDeBanimento() {
        comandos.executar(ana.handler, "/help");
        String aviso = ((Evento.Sistema) ultimo(ana)).aviso();
        assertTrue(aviso.contains("/ban usuario"));
        assertTrue(aviso.contains("/unban usuario"));
        assertTrue(aviso.contains("/bans"));
    }

    @Test
    void semSuporteAoBanimentoOsComandosNaoExistem() {
        AuthService semBan = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);
        CommandService s = new CommandService(chat, registry, new SalaService(chat, registry), semBan, null,
                Administradores.de(List.of("chefe")));
        s.executar(chefe.handler, "/ban ana");
        assertTrue(assertInstanceOf(Evento.Erro.class, ultimo(chefe)).texto().contains("desconhecido"));
    }
}
