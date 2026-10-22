package br.unicesumar.chat.server;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Adaptador falso de um cliente conectado: um {@link ClienteHandler} sobre streams em memória. */
final class ClienteFalso {

    final ClienteHandler handler;
    final ByteArrayOutputStream saida;
    final AtomicInteger fechamentos = new AtomicInteger();

    private ClienteFalso(OutputStream destino, ByteArrayOutputStream saida) {
        this.saida = saida;
        this.handler = new ClienteHandler(new ByteArrayInputStream(new byte[0]), destino,
                fechamentos::incrementAndGet, (origem, linha) -> { });
    }

    /** Cliente comum, cujas escritas ficam guardadas em memória. */
    static ClienteFalso novo() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        return new ClienteFalso(saida, saida);
    }

    /** Cliente cuja escrita sempre falha, como uma conexão que caiu. */
    static ClienteFalso quebrado() {
        OutputStream quebrado = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("conexão caiu");
            }
        };
        return new ClienteFalso(quebrado, new ByteArrayOutputStream());
    }

    /** Cliente já registrado e identificado com o nome informado. */
    static ClienteFalso identificado(ClientRegistry registry, String nome) {
        ClienteFalso c = novo();
        registry.adicionar(c.handler);
        if (!registry.identificar(c.handler, new br.unicesumar.chat.model.Usuario(nome))) {
            throw new IllegalStateException("nome já em uso no teste: " + nome);
        }
        return c;
    }

    /** Eventos que o cliente recebeu até agora. */
    List<Evento> eventos() {
        List<Evento> lista = new ArrayList<>();
        for (String linha : saida.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!linha.isEmpty()) {
                lista.add(Protocolo.decodificar(linha));
            }
        }
        return lista;
    }

    /** Textos das mensagens públicas recebidas, na ordem. */
    List<String> textos() {
        return eventos().stream().filter(e -> e instanceof Evento.Msg)
                .map(e -> ((Evento.Msg) e).texto()).toList();
    }
}
