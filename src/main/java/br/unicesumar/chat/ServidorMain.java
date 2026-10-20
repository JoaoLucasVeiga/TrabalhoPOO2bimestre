package br.unicesumar.chat;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.Servidor;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Ponto de entrada do servidor. Uso: {@code ServidorMain [porta] [arquivo.db]}
 * (padrão: porta 5000 e banco {@code chat.db}). É aqui que a implementação JDBC
 * é injetada: o restante do código só conhece a interface do repositório.
 */
public final class ServidorMain {

    private ServidorMain() {
    }

    /** Monta as dependências e sobe o servidor. */
    public static void main(String[] args) throws IOException, InterruptedException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        Path banco = Path.of(args.length > 1 ? args[1] : "chat.db");

        ConnectionFactory fabrica = ConnectionFactory.arquivo(banco);
        fabrica.criarTabelas();
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), registry,
                Clock.systemDefaultZone());
        Servidor servidor = new Servidor(porta, registry, new ProcessadorChat(chat, registry));
        servidor.iniciar();
        System.out.println("Servidor no ar na porta " + servidor.porta() + " (banco: "
                + banco.toAbsolutePath() + "). Ctrl+C para parar.");
        Thread.currentThread().join();
    }
}
