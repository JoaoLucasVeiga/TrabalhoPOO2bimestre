package br.unicesumar.chat;

import br.unicesumar.chat.infra.ConfiguracaoServidor;
import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.infra.LogArquivo;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.server.Administradores;
import br.unicesumar.chat.server.AuthService;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.CommandService;
import br.unicesumar.chat.server.PreparoDeAdmin;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.ProcessadorComLog;
import br.unicesumar.chat.server.SalaService;
import br.unicesumar.chat.server.Servidor;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Ponto de entrada do servidor. Uso: {@code ServidorMain [porta] [arquivo.db] [--sem-autenticacao]}
 * (padrão: porta 5000, banco {@code chat.db} e login obrigatório com /register e /login). A conta do
 * administrador vem de {@code servidor.properties} ou das variáveis CHAT_ADMIN_USUARIO e
 * CHAT_ADMIN_SENHA; sem ela, os comandos de administração ficam bloqueados.
 * É aqui que as implementações JDBC são injetadas: o restante do código só conhece
 * as interfaces dos repositórios.
 */
public final class ServidorMain {

    private static final String SEM_AUTENTICACAO = "--sem-autenticacao";
    private static final String ARQUIVO_DE_CONFIGURACAO = "servidor.properties";

    private ServidorMain() {
    }

    /** Monta as dependências e sobe o servidor. */
    public static void main(String[] args) throws IOException, InterruptedException {
        boolean comAutenticacao = true;
        String porta = "5000";
        String arquivo = "chat.db";
        int posicional = 0;
        for (String arg : args) {
            if (SEM_AUTENTICACAO.equals(arg)) {
                comAutenticacao = false;
            } else if (posicional++ == 0) {
                porta = arg;
            } else {
                arquivo = arg;
            }
        }

        Path banco = Path.of(arquivo);
        ConnectionFactory fabrica = ConnectionFactory.arquivo(banco);
        fabrica.criarTabelas();
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), registry,
                Clock.systemDefaultZone());
        AuthService auth = comAutenticacao ? new AuthService(new JdbcUsuarioRepository(fabrica)) : null;
        ExportadorHistorico exportador = new ExportadorHistorico(Path.of("exportacoes"), Clock.systemDefaultZone());
        LogArquivo log = new LogArquivo(Path.of("logs", "servidor.log"), Clock.systemDefaultZone());
        Administradores admins;
        try {
            ConfiguracaoServidor config = ConfiguracaoServidor.carregar(Path.of(ARQUIVO_DE_CONFIGURACAO),
                    System.getenv());
            admins = PreparoDeAdmin.preparar(config.admin(), auth, aviso -> {
                log.info(aviso);
                System.out.println(aviso);
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Não foi possível iniciar o servidor: " + e.getMessage());
            System.exit(1);
            return;
        }
        CommandService comandos = new CommandService(chat, registry, new SalaService(chat, registry), auth,
                exportador, admins);
        Servidor servidor = new Servidor(Integer.parseInt(porta), registry,
                new ProcessadorComLog(new ProcessadorChat(chat, comandos), log));
        servidor.iniciar();
        log.info("Servidor iniciado na porta " + servidor.porta());
        System.out.println("Servidor no ar na porta " + servidor.porta() + " (banco: "
                + banco.toAbsolutePath() + ", " + (comAutenticacao ? "com" : "sem")
                + " autenticação). Ctrl+C para parar.");
        Thread.currentThread().join();
    }
}
