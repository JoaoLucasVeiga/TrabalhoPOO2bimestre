package br.unicesumar.chat.client.controller;

import br.unicesumar.chat.client.net.ConexaoChat;
import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import br.unicesumar.chat.protocol.ProtocoloException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lógica do cliente gráfico: entra no chat, manda e recebe, e traduz os eventos do
 * servidor em chamadas à {@link ChatView}. Não importa nada de Swing e só fala com o
 * servidor através de {@link ConexaoChat}, então é testável sem tela e sem rede. Os métodos
 * de ação bloqueiam na rede (quem os chama a partir da interface usa uma thread de fundo);
 * os eventos recebidos chegam pela thread de leitura criada em {@link #conectar}.
 */
public class ChatController {

    /** Quanto esperar a resposta do servidor ao entrar, em milissegundos. */
    public static final int TEMPO_DO_LOGIN_MS = 10_000;

    private static final Logger LOG = Logger.getLogger(ChatController.class.getName());
    private static final String SALA_PADRAO = "geral";
    private static final int HISTORICO_ANTERIOR = 100;
    private static final String DESCONECTADO =
            "Você está desconectado do servidor. Entre novamente para continuar.";

    /** O que o controlador espera como resposta do servidor, na ordem em que foi pedido. */
    private enum Pedido { ANTERIORES, BUSCA, SALA }

    private final ChatView view;
    private final FabricaConexao fabrica;
    private final Clock relogio;
    private final boolean iniciarLeitor;

    private final Object lock = new Object();
    private final Deque<Pedido> pendentes = new ArrayDeque<>();
    private final List<MensagemTela> publicas = new ArrayList<>();
    private List<MensagemTela> historicoDaTroca = new ArrayList<>();

    private volatile ConexaoChat conexao;
    private volatile String usuario;
    private volatile String salaAtual = SALA_PADRAO;
    private volatile EstadoConexao estado = EstadoConexao.DESCONECTADO;
    private volatile boolean encerrando;
    private volatile Thread leitor;

    /** Cria o controlador com o relógio do sistema. */
    public ChatController(ChatView view, FabricaConexao fabrica) {
        this(view, fabrica, Clock.systemDefaultZone());
    }

    /** Cria o controlador com o relógio informado (usado nos avisos do sistema). */
    public ChatController(ChatView view, FabricaConexao fabrica, Clock relogio) {
        this(view, fabrica, relogio, true);
    }

    ChatController(ChatView view, FabricaConexao fabrica, Clock relogio, boolean iniciarLeitor) {
        this.view = Objects.requireNonNull(view, "view");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.iniciarLeitor = iniciarLeitor;
    }

    // ------------------------------------------------------------------ estado

    /** Nome do usuário (com as maiúsculas do cadastro); {@code null} antes de entrar. */
    public String usuario() {
        return usuario;
    }

    /** Sala em que o usuário está. */
    public String salaAtual() {
        return salaAtual;
    }

    /** Indica se há uma sessão aberta. */
    public boolean conectado() {
        return estado == EstadoConexao.CONECTADO;
    }

    // ------------------------------------------------------------------ entrar

    /**
     * Conecta, entra com {@code /login} (ou cria a conta com {@code /register}) e começa a
     * ouvir o servidor. Bloqueia até a resposta; chame fora da thread da interface.
     *
     * @param porta texto digitado pelo usuário (1 a 65535)
     * @return sucesso ou a mensagem do que houve e do que fazer
     */
    public ResultadoConexao conectar(String host, String porta, String nome, String senha, boolean criarConta) {
        if (conectado()) {
            return ResultadoConexao.falha("Você já está conectado. Saia antes de entrar com outra conta.");
        }
        String erro = validarEntrada(host, porta, nome, senha);
        if (erro != null) {
            return ResultadoConexao.falha(erro);
        }
        String servidor = host.strip();
        String nomeLimpo = nome.strip();
        ConexaoChat nova = fabrica.criar(servidor, Integer.parseInt(porta.strip()));
        try {
            nova.abrir();
        } catch (IOException e) {
            return ResultadoConexao.falha(e.getMessage());
        }
        List<MensagemTela> historico = new ArrayList<>();
        Evento.Ok ok;
        try {
            nova.definirTempoDeEspera(TEMPO_DO_LOGIN_MS);
            nova.enviar((criarConta ? "/register " : "/login ") + nomeLimpo + " " + senha);
            ok = aguardarConfirmacao(nova, nomeLimpo, historico);
            nova.definirTempoDeEspera(0);
        } catch (SocketTimeoutException e) {
            nova.fechar();
            return ResultadoConexao.falha("O servidor não respondeu a tempo. "
                    + "Verifique se o endereço e a porta são os do chat e tente de novo.");
        } catch (IOException e) {
            nova.fechar();
            return ResultadoConexao.falha(e.getMessage());
        } catch (ProtocoloException e) {
            nova.fechar();
            return ResultadoConexao.falha("Resposta inesperada do servidor. "
                    + "Verifique se o endereço e a porta são os do chat.");
        } catch (RecusaDoServidor e) {
            nova.fechar();
            return ResultadoConexao.falha(e.getMessage());
        }
        Mensagens.Entrada entrada = Mensagens.lerEntrada(ok.texto())
                .orElse(new Mensagens.Entrada(nomeLimpo, SALA_PADRAO));
        iniciarSessao(nova, entrada, historico);
        return ResultadoConexao.ok(entrada.nome());
    }

    /** Servidor respondeu ERRO ao login. */
    private static final class RecusaDoServidor extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RecusaDoServidor(String mensagem) {
            super(mensagem);
        }
    }

    private Evento.Ok aguardarConfirmacao(ConexaoChat nova, String nome, List<MensagemTela> historico)
            throws IOException {
        while (true) {
            Evento e = nova.receber();
            if (e == null) {
                throw new RecusaDoServidor("O servidor encerrou a conexão antes de concluir a entrada. "
                        + "Tente novamente.");
            }
            if (e instanceof Evento.Erro erro) {
                throw new RecusaDoServidor(erro.texto());
            }
            if (e instanceof Evento.Ok ok) {
                return ok;
            }
            if (e instanceof Evento.Historico h) {
                h.mensagens().forEach(m -> historico.add(MensagemTela.publica(m, nome)));
            }
        }
    }

    private String validarEntrada(String host, String porta, String nome, String senha) {
        if (host == null || host.isBlank()) {
            return "Informe o endereço do servidor (por exemplo, localhost).";
        }
        if (porta == null || !porta.strip().matches("\\d{1,5}") || Integer.parseInt(porta.strip()) < 1
                || Integer.parseInt(porta.strip()) > 65535) {
            return "Informe a porta do servidor: um número de 1 a 65535 (por exemplo, 5000).";
        }
        if (nome == null || nome.isBlank()) {
            return "Informe o seu nome de usuário.";
        }
        if (senha == null || senha.isEmpty()) {
            return "Informe a sua senha.";
        }
        return null;
    }

    private void iniciarSessao(ConexaoChat nova, Mensagens.Entrada entrada, List<MensagemTela> historico) {
        List<MensagemTela> comMarcacao = new ArrayList<>();
        for (MensagemTela m : historico) {
            comMarcacao.add(new MensagemTela(m.tipo(), m.remetente(), m.destinatario(), m.horario(), m.texto(),
                    m.remetente().equalsIgnoreCase(entrada.nome())));
        }
        conexao = nova;
        usuario = entrada.nome();
        salaAtual = entrada.sala();
        encerrando = false;
        estado = EstadoConexao.CONECTADO;
        synchronized (lock) {
            pendentes.clear();
            publicas.clear();
            publicas.addAll(comMarcacao);
        }
        view.definirUsuario(entrada.nome());
        view.mostrarConexao(EstadoConexao.CONECTADO);
        view.substituirHistorico(comMarcacao, entrada.sala());
        if (iniciarLeitor) {
            Thread t = new Thread(this::ler, "chat-leitor");
            t.setDaemon(true);
            leitor = t;
            t.start();
        }
        atualizarListas();
    }

    // ------------------------------------------------------------------ leitura

    private void ler() {
        ConexaoChat c = conexao;
        String motivo = "A conexão com o servidor foi encerrada. Entre novamente para continuar.";
        try {
            Evento e;
            while ((e = receberSemParar(c)) != null) {
                try {
                    tratar(e);
                } catch (RuntimeException falha) {
                    LOG.log(Level.SEVERE, "Erro ao tratar um evento do servidor: " + falha.getMessage(), falha);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "Leitura encerrada: {0}", e.getMessage());
        }
        if (!encerrando && estado == EstadoConexao.CONECTADO) {
            estado = EstadoConexao.DESCONECTADO;
            c.fechar();
            view.mostrarConexao(EstadoConexao.DESCONECTADO);
            view.sessaoEncerrada(motivo);
        }
    }

    /** Lê o próximo evento; linha fora do protocolo vira aviso e a leitura continua. */
    private Evento receberSemParar(ConexaoChat c) throws IOException {
        while (true) {
            try {
                return c.receber();
            } catch (ProtocoloException e) {
                view.mostrarAviso("O servidor enviou algo que não foi entendido: " + e.getMessage());
            }
        }
    }

    /** Aguarda a thread de leitura terminar (usado nos testes). */
    boolean aguardarFimDaLeitura(long milissegundos) throws InterruptedException {
        Thread t = leitor;
        if (t == null) {
            return true;
        }
        t.join(milissegundos);
        return !t.isAlive();
    }

    /** Traduz um evento do servidor em chamadas à view. Chamado pela thread de leitura. */
    void tratar(Evento evento) {
        String eu = usuario == null ? "" : usuario;
        switch (evento) {
            case Evento.Msg m -> {
                MensagemTela tela = MensagemTela.publica(m, eu);
                synchronized (lock) {
                    publicas.add(tela);
                }
                view.adicionar(tela);
            }
            case Evento.Privada p -> view.adicionar(MensagemTela.privada(p, eu));
            case Evento.Sistema s -> tratarAviso(s.aviso());
            case Evento.Usuarios u -> view.atualizarUsuarios(u.sala(), u.nomes());
            case Evento.Salas s -> view.atualizarSalas(s.nomes(), salaAtual);
            case Evento.Historico h -> tratarHistorico(h, eu);
            case Evento.Ok o -> tratarOk(o.texto());
            case Evento.Erro e -> tratarErro(e.texto());
        }
    }

    private void tratarAviso(String aviso) {
        if (aviso.equals(Mensagens.SEM_RESULTADO_DE_BUSCA) && consumirSe(Pedido.BUSCA)) {
            view.mostrarResultadoBusca(List.of());
        } else if (aviso.equals(Mensagens.SEM_HISTORICO) && consumirSe(Pedido.ANTERIORES)) {
            view.mostrarAviso("Não há mensagens mais antigas.");
        } else {
            view.adicionar(MensagemTela.sistema(aviso, LocalDateTime.now(relogio)));
            if (Mensagens.ehAvisoDePresenca(aviso)) {
                enviarSilencioso("/users");
            }
        }
    }

    private void tratarHistorico(Evento.Historico h, String eu) {
        List<MensagemTela> mensagens = new ArrayList<>();
        h.mensagens().forEach(m -> mensagens.add(MensagemTela.publica(m, eu)));
        Pedido pedido;
        synchronized (lock) {
            pedido = pendentes.peek();
            if (pedido == Pedido.SALA) {
                historicoDaTroca = mensagens; // só vale quando o OK da troca de sala chegar
                return;
            }
            if (pedido != null) {
                pendentes.poll();
            }
        }
        if (pedido == Pedido.BUSCA) {
            view.mostrarResultadoBusca(mensagens);
        } else {
            inserirAnteriores(mensagens);
        }
    }

    private void inserirAnteriores(List<MensagemTela> recebidas) {
        List<MensagemTela> novas = new ArrayList<>();
        synchronized (lock) {
            Set<MensagemTela> jaExibidas = new HashSet<>(publicas);
            LocalDateTime maisAntiga = publicas.isEmpty() ? null : publicas.get(0).horario();
            for (MensagemTela m : recebidas) {
                boolean maisVelha = maisAntiga == null || !m.horario().isAfter(maisAntiga);
                if (maisVelha && !jaExibidas.contains(m)) {
                    novas.add(m);
                }
            }
            publicas.addAll(0, novas);
        }
        if (novas.isEmpty()) {
            view.mostrarAviso("Não há mensagens mais antigas.");
        } else {
            view.inserirAnteriores(novas);
        }
    }

    private void tratarOk(String texto) {
        java.util.Optional<String> sala = Mensagens.lerMudancaDeSala(texto);
        if (sala.isEmpty()) {
            view.mostrarAviso(texto);
            return;
        }
        List<MensagemTela> historico;
        synchronized (lock) {
            if (pendentes.peek() == Pedido.SALA) {
                pendentes.poll();
            }
            historico = historicoDaTroca;
            historicoDaTroca = new ArrayList<>();
            publicas.clear();
            publicas.addAll(historico);
        }
        salaAtual = sala.get();
        view.substituirHistorico(historico, salaAtual);
        atualizarListas();
    }

    private void tratarErro(String texto) {
        Pedido pedido;
        synchronized (lock) {
            pedido = pendentes.poll();
            if (pedido == Pedido.SALA) {
                historicoDaTroca = new ArrayList<>();
            }
        }
        if (pedido == Pedido.BUSCA) {
            view.mostrarErroDeBusca(texto);
        } else {
            view.mostrarErro(texto);
        }
    }

    private boolean consumirSe(Pedido esperado) {
        synchronized (lock) {
            if (pendentes.peek() == esperado) {
                pendentes.poll();
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ ações

    /**
     * Envia o que o usuário digitou. Comandos de sala e histórico passam pelo caminho
     * próprio (para o controlador saber a que resposta esperar); o resto vai como está.
     */
    public void enviar(String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        String limpo = texto.strip();
        if (limpo.length() > Mensagem.LIMITE_CONTEUDO) {
            view.mostrarErro("A mensagem tem " + limpo.length() + " caracteres e o limite é "
                    + Mensagem.LIMITE_CONTEUDO + ". Encurte o texto.");
            return;
        }
        if (limpo.startsWith("/")) {
            enviarComando(limpo);
        } else {
            transmitir(limpo, null);
        }
    }

    private void enviarComando(String comando) {
        String[] partes = comando.split("\\s+", 2);
        String nome = partes[0].toLowerCase(Locale.ROOT);
        String argumentos = partes.length > 1 ? partes[1].strip() : "";
        switch (nome) {
            case "/join" -> entrarSala(argumentos);
            case "/leave" -> sairDaSala();
            case "/history" -> carregarHistoricoAnterior();
            case "/quit" -> sair();
            case "/buscar" -> transmitir(comando, Pedido.BUSCA);
            default -> transmitir(comando, null);
        }
    }

    /** Pede as últimas 100 mensagens; só as mais antigas que as da tela serão inseridas. */
    public void carregarHistoricoAnterior() {
        transmitir("/history " + HISTORICO_ANTERIOR, Pedido.ANTERIORES);
    }

    /** Busca mensagens de um usuário na sala atual. */
    public void buscarPorUsuario(String nome) {
        if (nome == null || nome.isBlank()) {
            view.mostrarErroDeBusca("Informe o nome do usuário.");
            return;
        }
        buscar("/buscar usuario " + nome.strip());
    }

    /** Busca mensagens que contêm o texto na sala atual. */
    public void buscarPorTexto(String trecho) {
        if (trecho == null || trecho.isBlank()) {
            view.mostrarErroDeBusca("Informe o texto a buscar.");
            return;
        }
        buscar("/buscar texto " + trecho.strip());
    }

    /** Busca mensagens entre duas datas ({@code AAAA-MM-DD}), inclusive. */
    public void buscarPorPeriodo(String inicio, String fim) {
        try {
            LocalDate de = LocalDate.parse(inicio == null ? "" : inicio.strip());
            LocalDate ate = LocalDate.parse(fim == null ? "" : fim.strip());
            if (de.isAfter(ate)) {
                view.mostrarErroDeBusca("A data inicial não pode ser depois da final.");
                return;
            }
            buscar("/buscar periodo " + de + " " + ate);
        } catch (DateTimeParseException e) {
            view.mostrarErroDeBusca("Use datas no formato AAAA-MM-DD, por exemplo 2026-10-31.");
        }
    }

    private void buscar(String comando) {
        if (!conectado()) {
            view.mostrarErroDeBusca(DESCONECTADO);
            return;
        }
        transmitir(comando, Pedido.BUSCA);
    }

    /** Entra em uma sala (criada se estiver vazia). */
    public void entrarSala(String nome) {
        if (nome == null || nome.isBlank()) {
            view.mostrarErro("Informe o nome da sala. Exemplo: java");
            return;
        }
        String sala = nome.strip();
        if (sala.equalsIgnoreCase(salaAtual)) {
            view.mostrarErro("Você já está na sala " + salaAtual + ".");
            return;
        }
        transmitir("/join " + sala, Pedido.SALA);
    }

    /** Sai da sala atual e volta para a geral. */
    public void sairDaSala() {
        if (salaAtual.equals(SALA_PADRAO)) {
            view.mostrarErro("Você já está na sala geral. Use o botão Nova sala ou /join para entrar em outra.");
            return;
        }
        transmitir("/leave", Pedido.SALA);
    }

    /** Pede de novo as listas de usuários e de salas. */
    public void atualizarListas() {
        enviarSilencioso("/users");
        enviarSilencioso("/rooms");
    }

    /** Sai do chat: avisa o servidor e fecha a conexão sem mostrar "sessão encerrada". */
    public void sair() {
        ConexaoChat c = conexao;
        if (c == null || estado == EstadoConexao.DESCONECTADO) {
            return;
        }
        encerrando = true;
        estado = EstadoConexao.DESCONECTADO;
        try {
            c.enviar("/quit");
        } catch (IOException e) {
            LOG.log(Level.INFO, "Não foi possível avisar a saída ao servidor: {0}", e.getMessage());
        }
        c.fechar();
        view.mostrarConexao(EstadoConexao.DESCONECTADO);
    }

    /**
     * Grava no arquivo as mensagens públicas da sala que estão na tela.
     *
     * @return o arquivo gravado
     * @throws IllegalStateException se não há o que exportar
     * @throws IOException           se o arquivo não puder ser gravado
     */
    public Path exportar(Path arquivo) throws IOException {
        List<String> linhas;
        synchronized (lock) {
            linhas = publicas.stream().map(MensagemTela::formatar).toList();
        }
        if (linhas.isEmpty()) {
            throw new IllegalStateException("Não há mensagens para exportar nesta sala. "
                    + "Carregue o histórico ou espere novas mensagens.");
        }
        return ExportadorHistorico.exportarPara(arquivo, salaAtual, linhas, relogio);
    }

    // ------------------------------------------------------------------ envio

    /** Registra o pedido (se houver) e envia; falha de rede derruba a sessão e mostra o erro. */
    private void transmitir(String linha, Pedido pedido) {
        ConexaoChat c = conexao;
        if (c == null || !conectado()) {
            if (pedido == Pedido.BUSCA) {
                view.mostrarErroDeBusca(DESCONECTADO);
            } else {
                view.mostrarErro(DESCONECTADO);
            }
            return;
        }
        if (pedido != null) {
            synchronized (lock) {
                pendentes.add(pedido);
            }
        }
        try {
            c.enviar(linha);
        } catch (IOException e) {
            if (pedido != null) {
                synchronized (lock) {
                    pendentes.removeLastOccurrence(pedido);
                }
            }
            falhaDeRede(c, e);
        }
    }

    private void enviarSilencioso(String linha) {
        ConexaoChat c = conexao;
        if (c == null || !conectado()) {
            return;
        }
        try {
            c.enviar(linha);
        } catch (IOException e) {
            falhaDeRede(c, e);
        }
    }

    private void falhaDeRede(ConexaoChat c, IOException e) {
        estado = EstadoConexao.DESCONECTADO;
        c.fechar();
        view.mostrarConexao(EstadoConexao.DESCONECTADO);
        view.mostrarErro(e.getMessage());
    }
}
