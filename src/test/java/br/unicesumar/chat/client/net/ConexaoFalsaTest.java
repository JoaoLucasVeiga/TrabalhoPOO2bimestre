package br.unicesumar.chat.client.net;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.ProtocoloException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** O adaptador falso precisa se comportar como o contrato de ConexaoChat descreve. */
class ConexaoFalsaTest {

    @Test
    void comecaFechadaEAbrirAAbre() throws IOException {
        ConexaoFalsa c = new ConexaoFalsa();
        assertFalse(c.aberta());
        c.abrir();
        assertTrue(c.aberta());
    }

    @Test
    void entregaOsEventosDoRoteiroNaOrdemEDepoisNullNoFim() throws IOException {
        ConexaoFalsa c = new ConexaoFalsa().receberEvento(new Evento.Sistema("a"))
                .receberEvento(new Evento.Ok("b")).encerrarPeloServidor();
        c.abrir();
        assertEquals(new Evento.Sistema("a"), c.receber());
        assertEquals(new Evento.Ok("b"), c.receber());
        assertNull(c.receber());
        assertNull(c.receber());
    }

    @Test
    void registraAsLinhasEnviadasNaOrdem() throws IOException {
        ConexaoFalsa c = new ConexaoFalsa();
        c.abrir();
        c.enviar("um");
        c.enviar("dois");
        assertEquals(List.of("um", "dois"), c.enviadas());
    }

    @Test
    void enviarComAConexaoFechadaLancaIOException() {
        assertThrows(IOException.class, () -> new ConexaoFalsa().enviar("x"));
    }

    @Test
    void fecharDestravaQuemEstaEsperandoReceber() throws Exception {
        ConexaoFalsa c = new ConexaoFalsa();
        c.abrir();
        Thread leitor = new Thread(() -> {
            try {
                assertNull(c.receber());
            } catch (IOException e) {
                fail(e);
            }
        });
        leitor.start();
        c.fechar();
        leitor.join(5000);
        assertFalse(leitor.isAlive());
        assertFalse(c.aberta());
    }

    @Test
    void podeSimularFalhaAoAbrirEAoEnviar() throws IOException {
        assertThrows(IOException.class,
                () -> new ConexaoFalsa().falharAoAbrirCom(new IOException("recusada")).abrir());
        ConexaoFalsa c = new ConexaoFalsa().falharAoEnviarCom(new IOException("caiu"));
        c.abrir();
        assertThrows(IOException.class, () -> c.enviar("x"));
    }

    @Test
    void linhaInvalidaDoRoteiroLancaProtocoloException() throws IOException {
        ConexaoFalsa c = new ConexaoFalsa().receberLinhaInvalida("lixo");
        c.abrir();
        assertThrows(ProtocoloException.class, c::receber);
    }
}
