package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Depois de entrar, o cliente recebe PAPEL (só em servidor com restrição de administradores). */
class CommandServicePapelTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
            Clock.systemDefaultZone());
    private final AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);

    private CommandService servico(Administradores admins) {
        return new CommandService(chat, registry, new SalaService(chat, registry), auth, null, admins);
    }

    private ClienteFalso cliente() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        return c;
    }

    @Test
    void usuarioComumRecebePapelUsuarioDepoisDoOk() {
        ClienteFalso ana = cliente();
        servico(Administradores.de(List.of("chefe"))).executar(ana.handler, "/register ana senha123");
        List<Evento> eventos = ana.eventos();
        assertEquals(new Evento.Papel(false), eventos.get(eventos.size() - 1));
        assertTrue(eventos.stream().anyMatch(e -> e instanceof Evento.Ok));
    }

    @Test
    void administradorRecebePapelAdmin() {
        auth.garantirConta("chefe", "senha123");
        ClienteFalso chefe = cliente();
        servico(Administradores.de(List.of("chefe"))).executar(chefe.handler, "/login chefe senha123");
        List<Evento> eventos = chefe.eventos();
        assertEquals(new Evento.Papel(true), eventos.get(eventos.size() - 1));
    }

    @Test
    void semRestricaoNaoEnviaPapel() {
        ClienteFalso ana = cliente();
        servico(Administradores.semRestricao()).executar(ana.handler, "/register ana senha123");
        assertTrue(ana.eventos().stream().noneMatch(e -> e instanceof Evento.Papel));
    }

    @Test
    void loginComSenhaErradaNaoEnviaPapel() {
        auth.garantirConta("chefe", "senha123");
        ClienteFalso c = cliente();
        servico(Administradores.de(List.of("chefe"))).executar(c.handler, "/login chefe errada1");
        assertTrue(c.eventos().stream().noneMatch(e -> e instanceof Evento.Papel));
    }
}
