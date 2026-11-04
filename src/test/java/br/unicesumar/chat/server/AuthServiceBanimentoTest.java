package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.BanimentoRepositoryEmMemoria;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Banimento no serviço de login: quem foi banido não cria conta nem entra. */
class AuthServiceBanimentoTest {

    private final AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000,
            new BanimentoRepositoryEmMemoria());

    @Test
    void banidoComSenhaCertaNaoEntraEAMensagemDizPorque() {
        auth.registrar("ana", "senha123");
        assertTrue(auth.banir("ana"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", "senha123"));
        assertTrue(e.getMessage().contains("banida"));
    }

    @Test
    void senhaErradaDeBanidoDaAMesmaMensagemGenericaDeSempre() {
        auth.registrar("ana", "senha123");
        auth.banir("ana");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", "errada123"));
        assertFalse(e.getMessage().contains("banida"));
    }

    @Test
    void banidoNaoConsegueCriarContaComEsseNomeInclusiveSemConta() {
        auth.banir("intruso");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.registrar("Intruso", "senha123"));
        assertTrue(e.getMessage().contains("não pode ser usado"));
    }

    @Test
    void desbanirDevolveOAcesso() {
        auth.registrar("ana", "senha123");
        auth.banir("ana");
        assertTrue(auth.desbanir("ana"));
        assertEquals("ana", auth.autenticar("ana", "senha123").nome());
    }

    @Test
    void banirDeNovoDevolveFalseEDesbanirQuemNaoEstaDevolveFalse() {
        assertTrue(auth.banir("ana"));
        assertFalse(auth.banir("ANA"));
        assertFalse(auth.desbanir("bia"));
    }

    @Test
    void estaBanidoEListarBanidos() {
        auth.banir("caio");
        auth.banir("ana");
        assertTrue(auth.estaBanido("CAIO"));
        assertFalse(auth.estaBanido("bia"));
        assertEquals(List.of("ana", "caio"), auth.listarBanidos());
    }

    @Test
    void nomeInvalidoNaoPodeSerBanido() {
        assertThrows(IllegalArgumentException.class, () -> auth.banir("a"));
        assertThrows(IllegalArgumentException.class, () -> auth.banir("com espaço"));
    }

    @Test
    void semRepositorioDeBanimentoNaoHaSuporte() {
        AuthService simples = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);
        assertFalse(simples.suportaBanimento());
        assertTrue(auth.suportaBanimento());
        assertThrows(UnsupportedOperationException.class, () -> simples.banir("ana"));
        assertFalse(simples.estaBanido("ana"));
    }
}
