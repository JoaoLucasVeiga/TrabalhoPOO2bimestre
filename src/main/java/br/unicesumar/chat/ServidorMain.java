package br.unicesumar.chat;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.Servidor;
import java.io.IOException;
import java.time.Clock;

/** Ponto de entrada do servidor. Uso: {@code ServidorMain [porta]} (padrão 5000). */
public final class ServidorMain {

    private ServidorMain() {
    }

    /**
     * Monta as dependências e sobe o servidor. Nesta etapa o histórico ainda é
     * em memória; a persistência JDBC é ligada na etapa seguinte.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        MensagemRepository repositorio = new MensagemRepositoryEmMemoria();
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(repositorio, registry, Clock.systemDefaultZone());
        Servidor servidor = new Servidor(porta, registry, new ProcessadorChat(chat, registry));
        servidor.iniciar();
        System.out.println("Servidor no ar na porta " + servidor.porta() + ". Ctrl+C para parar.");
        Thread.currentThread().join();
    }
}
