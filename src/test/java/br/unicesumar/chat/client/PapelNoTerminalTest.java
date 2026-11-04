package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.protocol.Evento;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** O aviso de papel é para a interface gráfica: o terminal não imprime nada por ele. */
class PapelNoTerminalTest {

    @Test
    void formatadorDevolveTextoVazioParaPapel() {
        assertEquals("", FormatadorTerminal.formatar(new Evento.Papel(true)));
    }

    @Test
    void clienteDeTerminalNaoImprimeLinhaEmBrancoNemTextoParaPapel() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream tela = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ConexaoFalsa conexao = new ConexaoFalsa()
                .receberEvento(new Evento.Sistema("antes"))
                .receberEvento(new Evento.Papel(false))
                .receberEvento(new Evento.Sistema("depois"))
                .encerrarPeloServidor();
        new ClienteChat(conexao, new BufferedReader(new StringReader("")), tela, 200).executar();
        String saida = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(saida.contains("* antes\n") || saida.contains("* antes\r\n"));
        assertFalse(saida.matches("(?s).*\\* antes\\R\\R.*"), "não pode haver linha em branco entre os avisos");
        assertTrue(saida.contains("* depois"));
    }
}
