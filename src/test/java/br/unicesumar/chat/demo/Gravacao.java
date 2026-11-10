package br.unicesumar.chat.demo;

import br.unicesumar.chat.client.net.ConexaoSocket;
import br.unicesumar.chat.client.net.OpcoesDeConexao;
import br.unicesumar.chat.client.ui.AplicacaoGui;
import br.unicesumar.chat.client.ui.SeletorDeEmojis;
import br.unicesumar.chat.client.ui.Tema;
import br.unicesumar.chat.infra.CertificadoTls;
import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcBanimentoRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.server.Administradores;
import br.unicesumar.chat.server.AuthService;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClienteHandler;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.CommandService;
import br.unicesumar.chat.server.PreparoDeAdmin;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.ProcessadorEntrada;
import br.unicesumar.chat.server.SalaService;
import br.unicesumar.chat.server.Servidor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Roteiro automatizado da gravação do vídeo de demonstração: servidor real (com TLS e SQLite), três
 * clientes gráficos reais (Ana, Bruno e Carla), uma janela com o log do servidor e uma barra de legenda.
 * Os intervalos do heartbeat são reduzidos (PING a cada 2 s, limite de 6 s) para caber no vídeo.
 *
 * <p>Precisa de uma tela de 2160x1250 e das pausas ({@code Thread.sleep}) para o espectador acompanhar;
 * por isso fica fora dos testes. Quem grava a tela é o {@code ffmpeg}, pelo script {@code docs/video/gravar.sh}.
 * Uso: {@code Gravacao <pasta-de-trabalho>}.
 */
public final class Gravacao {

    private static final int PORTA = 5099;
    private static final String SENHA = "senha123";
    private static final int CLIENTES_DE_CARGA = 50;
    private static final int MENSAGENS_POR_CLIENTE = 20;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** A janela do chat tem largura mínima de 1080 px, por isso a tela é de 2160x1250. */
    private static final int LARGURA_DA_TELA = 2160;
    private static final Rectangle CELULA_ANA = new Rectangle(0, 90, 1080, 580);
    private static final Rectangle CELULA_BRUNO = new Rectangle(1080, 90, 1080, 580);
    private static final Rectangle CELULA_CARLA = new Rectangle(0, 670, 1080, 580);
    private static final Rectangle CELULA_LOG = new Rectangle(1080, 670, 1080, 580);

    /** Um cliente gráfico real. */
    private static final class Cliente {
        final String nome;
        final Rectangle area;
        final OpcoesDeConexao opcoes;
        volatile ConexaoSocket conexao;
        AplicacaoGui app;

        Cliente(String nome, Rectangle area, OpcoesDeConexao opcoes) {
            this.nome = nome;
            this.area = area;
            this.opcoes = opcoes;
        }
    }

    private final Path pasta;
    private final Path banco;
    private final Path certificado;
    private final OpcoesDeConexao tls;
    private ConnectionFactory fabrica;
    private Servidor servidor;
    private JTextArea textoDoLog;
    private JLabel textoDaLegenda;

    private Gravacao(Path pasta) {
        this.pasta = pasta;
        this.banco = pasta.resolve("chat.db");
        this.certificado = pasta.resolve("chat-tls.crt");
        this.tls = OpcoesDeConexao.de(new String[] {"--tls=" + certificado});
    }

    // ------------------------------------------------------------------ utilidades

    private static void pausa(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void edt(Runnable acao) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                acao.run();
            } else {
                SwingUtilities.invokeAndWait(acao);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esperar(String o_que, BooleanSupplier condicao, long limiteMs) {
        long fim = System.currentTimeMillis() + limiteMs;
        while (!condicao.getAsBoolean()) {
            if (System.currentTimeMillis() > fim) {
                throw new IllegalStateException("Tempo esgotado esperando: " + o_que);
            }
            pausa(50);
        }
    }

    private void legenda(String texto) {
        edt(() -> textoDaLegenda.setText("<html><div style='text-align:center'>" + texto + "</div></html>"));
        System.out.println("[legenda] " + texto);
    }

    private void log(String texto) {
        String linha = LocalTime.now().format(HORA) + "  " + texto + "\n";
        SwingUtilities.invokeLater(() -> {
            textoDoLog.append(linha);
            textoDoLog.setCaretPosition(textoDoLog.getDocument().getLength());
        });
    }

    // ------------------------------------------------------------------ janelas de apoio

    private void montarJanelasDeApoio() {
        edt(() -> {
            JWindow barra = new JWindow();
            textoDaLegenda = new JLabel("", SwingConstants.CENTER);
            textoDaLegenda.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            textoDaLegenda.setForeground(Color.WHITE);
            textoDaLegenda.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
            barra.getContentPane().setBackground(new Color(0x1F2933));
            barra.getContentPane().add(textoDaLegenda);
            barra.setBounds(0, 0, LARGURA_DA_TELA, 90);
            barra.setAlwaysOnTop(true);
            barra.setVisible(true);

            JFrame janelaLog = new JFrame("Servidor");
            janelaLog.setUndecorated(true);
            textoDoLog = new JTextArea();
            textoDoLog.setEditable(false);
            textoDoLog.setBackground(new Color(0x111111));
            textoDoLog.setForeground(new Color(0xD8D8D8));
            textoDoLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
            textoDoLog.setMargin(new java.awt.Insets(10, 12, 10, 12));
            JLabel titulo = new JLabel("  SERVIDOR (log)");
            titulo.setOpaque(true);
            titulo.setBackground(new Color(0x2B2B2B));
            titulo.setForeground(new Color(0xBBBBBB));
            titulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            janelaLog.getContentPane().add(titulo, java.awt.BorderLayout.NORTH);
            janelaLog.getContentPane().add(new JScrollPane(textoDoLog), java.awt.BorderLayout.CENTER);
            janelaLog.setBounds(CELULA_LOG);
            janelaLog.setVisible(true);
        });
        Logger raiz = Logger.getLogger("");
        raiz.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.INFO.intValue() && r.getLoggerName() != null
                        && r.getLoggerName().startsWith("br.unicesumar.chat.server")) {
                    log(new java.util.logging.SimpleFormatter().formatMessage(r));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    // ------------------------------------------------------------------ servidor

    private void subirServidor() throws Exception {
        fabrica = ConnectionFactory.arquivo(banco);
        fabrica.criarTabelas();
        ClientRegistry registry = new ClientRegistry();
        AuthService auth = new AuthService(new JdbcUsuarioRepository(fabrica), AuthService.ITERACOES_PADRAO,
                new JdbcBanimentoRepository(fabrica));
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), registry,
                Clock.systemDefaultZone(), auth::contaExiste);
        Administradores admins = PreparoDeAdmin.preparar(Optional.empty(), auth, aviso -> { });
        CommandService comandos = new CommandService(chat, registry, new SalaService(chat, registry), auth,
                new ExportadorHistorico(pasta.resolve("exportacoes"), Clock.systemDefaultZone()), admins);
        ProcessadorChat real = new ProcessadorChat(chat, comandos);
        ProcessadorEntrada observado = new ProcessadorEntrada() {
            @Override
            public void aoConectar(ClienteHandler origem) {
                real.aoConectar(origem);
            }

            @Override
            public void aoReceber(ClienteHandler origem, String linha) {
                real.aoReceber(origem, linha);
            }

            @Override
            public void aoDesconectar(ClienteHandler origem) {
                real.aoDesconectar(origem);
                log("conexão encerrada: " + origem.usuario().map(u -> u.nome()).orElse("(sem login)"));
            }
        };
        char[] senhaTls = "senha-da-demonstracao".toCharArray();
        CertificadoTls.garantir(pasta.resolve("chat-tls.p12"), certificado, senhaTls);
        servidor = new Servidor(PORTA, registry, observado, Duration.ofSeconds(2), Duration.ofSeconds(6))
                .comTls(CertificadoTls.contextoDoServidor(pasta.resolve("chat-tls.p12"), senhaTls)
                        .getServerSocketFactory());
        servidor.iniciar();
        log("Servidor no ar na porta " + PORTA + " (SQLite, com TLS; PING a cada 2 s, limite 6 s só no vídeo)");
    }

    private void pararServidor() {
        servidor.parar();
        log("Servidor parado.");
    }

    private long contarMensagens(String sala) throws Exception {
        try (Connection c = fabrica.abrir();
                var ps = c.prepareStatement("SELECT COUNT(*) FROM mensagem WHERE sala = ?")) {
            ps.setString(1, sala);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ------------------------------------------------------------------ clientes gráficos

    private Cliente criarCliente(String nome, Rectangle area) {
        Cliente c = new Cliente(nome, area, tls);
        edt(() -> {
            c.app = new AplicacaoGui(new Tema(Tema.Modo.CLARO), (host, porta) -> {
                ConexaoSocket conexao = c.opcoes.conexao(host, porta);
                c.conexao = conexao;
                return conexao;
            }, () -> { });
            c.app.iniciar();
            c.app.chat().setBounds(area);
            posicionarLogin(c);
        });
        return c;
    }

    private static void posicionarLogin(Cliente c) {
        var login = c.app.login();
        login.setLocation(c.area.x + (c.area.width - login.getWidth()) / 2,
                c.area.y + (c.area.height - login.getHeight()) / 2);
    }

    private void digitarNoLogin(Cliente c, boolean criarConta) {
        edt(() -> {
            var p = c.app.login().painel();
            p.campoServidor().setText("localhost");
            p.campoPorta().setText(String.valueOf(PORTA));
            p.campoUsuario().setText(c.nome);
            p.campoSenha().setText(SENHA);
        });
        pausa(1200);
        edt(() -> {
            var p = c.app.login().painel();
            (criarConta ? p.botaoCriarConta() : p.botaoEntrar()).doClick();
        });
        esperar("chat de " + c.nome, () -> c.app.chat().isVisible(), 20_000);
        edt(() -> c.app.chat().setBounds(c.area));
        pausa(800);
    }

    /** Digita devagar na caixa de mensagem e envia. */
    private void digitar(Cliente c, String texto) {
        var entrada = c.app.chat().painel().entrada();
        StringBuilder parcial = new StringBuilder();
        texto.codePoints().forEach(cp -> {
            parcial.appendCodePoint(cp);
            String atual = parcial.toString();
            edt(() -> entrada.campo().setText(atual));
            pausa(35);
        });
        pausa(400);
        edt(entrada::acionarEnvio);
        pausa(600);
    }

    /** Envia sem animar (para o disparo simultâneo). */
    private void enviarJa(Cliente c, String texto) {
        edt(() -> {
            var entrada = c.app.chat().painel().entrada();
            entrada.campo().setText(texto);
            entrada.acionarEnvio();
        });
    }

    private void escolherEmoji(Cliente c, String emoji) {
        var entrada = c.app.chat().painel().entrada();
        edt(() -> entrada.botaoEmojis().doClick());
        pausa(1800);
        edt(() -> {
            SeletorDeEmojis seletor = null;
            for (java.awt.Window janela : java.awt.Window.getWindows()) { // o popup pode ser uma janela própria
                if (janela.isShowing() && seletor == null) {
                    seletor = achar(janela, SeletorDeEmojis.class);
                }
            }
            if (seletor == null) {
                throw new IllegalStateException("Seletor de emojis não apareceu.");
            }
            for (Component botao : seletor.getComponents()) {
                if (botao instanceof javax.swing.JButton b && b.getText().equals(emoji)) {
                    b.doClick();
                    return;
                }
            }
            throw new IllegalStateException("Emoji não encontrado: " + emoji);
        });
        pausa(900);
    }

    private static <T> T achar(Container raiz, Class<T> tipo) {
        for (Component filho : raiz.getComponents()) {
            if (tipo.isInstance(filho)) {
                return tipo.cast(filho);
            }
            if (filho instanceof Container sub) {
                T achado = achar(sub, tipo);
                if (achado != null) {
                    return achado;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ clientes de carga e zumbi

    private ConexaoSocket abrirSocket() throws Exception {
        ConexaoSocket c = tls.conexao("localhost", PORTA);
        c.abrir();
        return c;
    }

    private static void esperarOk(ConexaoSocket c) throws Exception {
        for (int i = 0; i < 50; i++) {
            Evento e = c.receber();
            if (e instanceof Evento.Ok) {
                return;
            }
            if (e instanceof Evento.Erro erro) {
                throw new IllegalStateException(erro.texto());
            }
            if (e == null) {
                throw new IllegalStateException("conexão fechada");
            }
        }
        throw new IllegalStateException("resposta Ok não chegou");
    }

    private void cargaDe50Clientes() throws Exception {
        long antes = contarMensagens("carga");
        CountDownLatch prontos = new CountDownLatch(CLIENTES_DE_CARGA);
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<ConexaoSocket> sockets = new ArrayList<>();
        for (int i = 1; i <= CLIENTES_DE_CARGA; i++) {
            final int n = i;
            Thread t = new Thread(() -> {
                try {
                    ConexaoSocket c = abrirSocket();
                    synchronized (sockets) {
                        sockets.add(c);
                    }
                    c.enviar("/register robo" + n + " " + SENHA);
                    esperarOk(c);
                    c.enviar("/join carga");
                    esperarOk(c);
                    Thread leitor = new Thread(() -> {
                        try {
                            while (c.receber() != null) {
                                // só esvazia o que o servidor transmite
                            }
                        } catch (Exception ignorado) {
                            // a conexão foi fechada no fim
                        }
                    }, "leitor-" + n);
                    leitor.setDaemon(true);
                    leitor.start();
                    prontos.countDown();
                    largada.await();
                    for (int k = 1; k <= MENSAGENS_POR_CLIENTE; k++) {
                        c.enviar("robô " + n + " · mensagem " + k + " de " + MENSAGENS_POR_CLIENTE);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("cliente de carga " + n + ": " + e.getMessage(), e);
                }
            }, "robo-" + n);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        if (!prontos.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("clientes de carga não ficaram prontos");
        }
        log(CLIENTES_DE_CARGA + " clientes conectados na sala carga; largada!");
        long inicio = System.nanoTime();
        largada.countDown();
        long total = (long) CLIENTES_DE_CARGA * MENSAGENS_POR_CLIENTE;
        esperar("gravar as mensagens de carga", () -> {
            try {
                return contarMensagens("carga") - antes >= total;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, 60_000);
        double segundos = (System.nanoTime() - inicio) / 1e9;
        long gravadas = contarMensagens("carga") - antes;
        String resumo = String.format("%d mensagens de %d clientes: %d gravadas no banco em %.1f s, nenhuma perdida",
                total, CLIENTES_DE_CARGA, gravadas, segundos);
        log(resumo);
        legenda("<span style='color:#9be39b'>" + resumo + "</span>");
        pausa(5000);
        for (ConexaoSocket c : sockets) {
            c.fechar();
        }
    }

    // ------------------------------------------------------------------ roteiro

    private void roteiro() throws Exception {
        montarJanelasDeApoio();
        legenda("Chat multiusuário em Java<br><span style='font-size:18px;font-weight:normal'>servidor com sockets, "
                + "uma thread por cliente, histórico em SQLite (JDBC)</span>");
        subirServidor();
        pausa(4500);

        Cliente ana = criarCliente("ana", CELULA_ANA);
        Cliente bruno = criarCliente("bruno", CELULA_BRUNO);
        Cliente carla = criarCliente("carla", CELULA_CARLA);

        legenda("1. Servidor com vários clientes<br><span style='font-size:18px;font-weight:normal'>"
                + "três clientes gráficos criam conta e entram, com a conexão criptografada (TLS)</span>");
        digitarNoLogin(ana, true);
        digitarNoLogin(bruno, true);
        digitarNoLogin(carla, true);
        pausa(2500);

        legenda("2. Broadcast sem perda<br><span style='font-size:18px;font-weight:normal'>"
                + "os três enviam ao mesmo tempo e todos veem a mesma ordem</span>");
        for (int rodada = 1; rodada <= 3; rodada++) {
            final int r = rodada;
            CountDownLatch largada = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (Cliente c : List.of(ana, bruno, carla)) {
                Thread t = new Thread(() -> {
                    try {
                        largada.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    enviarJa(c, c.nome + ": mensagem " + r + " (enviada ao mesmo tempo)");
                });
                t.start();
                threads.add(t);
            }
            largada.countDown();
            for (Thread t : threads) {
                t.join();
            }
            pausa(1800);
        }
        pausa(1500);

        legenda("Emojis e figurinhas<br><span style='font-size:18px;font-weight:normal'>"
                + "só na interface: para o servidor, emoji é texto comum</span>");
        digitar(ana, "Bora testar os emojis!");
        escolherEmoji(ana, "🎉");
        edt(() -> ana.app.chat().painel().entrada().acionarEnvio());
        pausa(1800);
        digitar(bruno, "Muito bom");
        escolherEmoji(bruno, "🔥");
        edt(() -> bruno.app.chat().painel().entrada().acionarEnvio());
        pausa(2500);

        legenda("Conversa privada<br><span style='font-size:18px;font-weight:normal'>"
                + "só Ana e Bruno veem; a Carla não recebe nada</span>");
        digitar(ana, "/msg bruno Oi Bruno, esta mensagem é só para você");
        pausa(1200);
        digitar(bruno, "/msg ana Recebi! A Carla não está vendo, certo?");
        pausa(1500);
        ana.app.controlador().abrirConversa("bruno");
        pausa(3500);
        ana.app.controlador().voltarParaSala();
        pausa(1500);

        legenda("Cliente cai de repente<br><span style='font-size:18px;font-weight:normal'>"
                + "a conexão da Carla é cortada sem aviso: o servidor continua e os outros recebem o aviso</span>");
        pausa(2500);
        carla.conexao.fechar();
        pausa(4500);

        legenda("Heartbeat: PING a cada 2 s (15 s no uso normal)<br><span style='font-size:18px;"
                + "font-weight:normal'>um cliente 'zumbi' entra e para de responder; passado o limite o servidor remove</span>");
        ConexaoSocket zumbi = abrirSocket();
        zumbi.enviar("/register zumbi " + SENHA);
        esperarOk(zumbi); // e nunca mais lê: não responde aos PING
        log("zumbi entrou e parou de responder");
        pausa(10_000);
        zumbi.fechar();

        legenda("Produtor-consumidor com gravação em lote<br><span style='font-size:18px;font-weight:normal'>"
                + "50 clientes reais por socket (TLS) enviam 20 mensagens cada, todos ao mesmo tempo</span>");
        ana.app.controlador().entrarSala("carga");
        pausa(2000);
        cargaDe50Clientes();
        ana.app.controlador().entrarSala("geral");
        pausa(1500);

        legenda("3. Persistência JDBC<br><span style='font-size:18px;font-weight:normal'>"
                + "o servidor é parado e iniciado de novo; o histórico continua no SQLite</span>");
        pausa(2500);
        pararServidor();
        pausa(5000);
        subirServidor();
        pausa(2000);
        for (Cliente c : List.of(ana, bruno)) {
            esperar("login de " + c.nome, () -> c.app.login().isVisible(), 20_000);
            edt(() -> posicionarLogin(c));
        }
        digitarNoLogin(ana, false);
        digitarNoLogin(bruno, false);
        pausa(7000);

        legenda("Pacotes separados, 1204 testes automatizados (unitários, concorrência e integração)<br>"
                + "<span style='font-size:18px;font-weight:normal'>código, script SQL, README, relatório e diagramas "
                + "estão no repositório</span>");
        pausa(7000);
    }

    /** Ponto de entrada. */
    public static void main(String[] args) throws Exception {
        Path pasta = Path.of(args.length > 0 ? args[0] : "gravacao-demo");
        Files.createDirectories(pasta);
        Files.deleteIfExists(pasta.resolve("chat.db"));
        Files.deleteIfExists(pasta.resolve("chat.db-wal"));
        Files.deleteIfExists(pasta.resolve("chat.db-shm"));
        int saida = 0;
        try {
            new Gravacao(pasta).roteiro();
            System.out.println("Roteiro concluído.");
        } catch (Throwable erro) {
            erro.printStackTrace();
            saida = 1;
        } finally {
            System.exit(saida);
        }
    }
}
