package br.unicesumar.chat;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.server.ClienteHandler;
import br.unicesumar.chat.server.ProcessadorEntrada;
import br.unicesumar.chat.server.Servidor;
import java.io.IOException;

/** Ponto de entrada do servidor. Uso: {@code ServidorMain [porta]} (padrão 5000). */
public final class ServidorMain {

    private ServidorMain() {
    }

    /** Sobe o servidor. Nesta etapa cada linha recebida é apenas confirmada ao cliente. */
    public static void main(String[] args) throws IOException, InterruptedException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        Servidor servidor = new Servidor(porta, new ProcessadorEntrada() {
            @Override
            public void aoConectar(ClienteHandler origem) {
                origem.enviar(new Evento.Sistema("Conectado ao servidor."));
            }

            @Override
            public void aoReceber(ClienteHandler origem, String linha) {
                origem.enviar(new Evento.Ok("Recebido: " + linha));
            }
        });
        servidor.iniciar();
        System.out.println("Servidor no ar na porta " + servidor.porta() + ". Ctrl+C para parar.");
        Thread.currentThread().join();
    }
}
