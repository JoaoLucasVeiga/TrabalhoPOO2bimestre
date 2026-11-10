package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A resposta {@code /pong} só serve para provar que o cliente está vivo: não chega ao processador. */
class ClienteHandlerPongTest {

    @Test
    void pongNaoVaiParaOProcessadorMasAsOutrasLinhasVao() throws Exception {
        List<String> recebidas = Collections.synchronizedList(new ArrayList<>());
        ProcessadorEntrada processador = new ProcessadorEntrada() {
            @Override
            public void aoConectar(ClienteHandler origem) {
            }

            @Override
            public void aoReceber(ClienteHandler origem, String linha) {
                recebidas.add(linha);
            }

            @Override
            public void aoDesconectar(ClienteHandler origem) {
            }
        };
        ByteArrayInputStream entrada = new ByteArrayInputStream("oi\n/pong\n/PONG x\nfim\n".getBytes(StandardCharsets.UTF_8));
        ClienteHandler handler = ClienteHandler.assincrono(entrada, new ByteArrayOutputStream(), () -> { },
                processador, SaidaAssincrona.LIMITE_PADRAO);
        handler.run();
        assertEquals(List.of("oi", "/PONG x", "fim"), recebidas);
    }
}
