package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.ChatView;
import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Janela principal (sem a moldura): topo com sala, usuários online, indicador de conexão e botão de
 * tema; barra de ferramentas; painel lateral; conversa em balões; faixa de avisos; e o rodapé.
 * Implementa {@link ChatView}: os métodos podem ser chamados de qualquer thread, e todo acesso a
 * componentes é levado para a EDT. As ações de rede rodam em segundo plano ({@link Segundo}).
 */
public class PainelChat extends JPanel implements ChatView {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(PainelChat.class.getName());
    private static final int USUARIOS_NO_RESUMO = 5;
    private static final int TEMPO_DA_FAIXA_MS = 8000;
    private static final int TEMPO_MAXIMO_DO_HISTORICO_MS = 10_000;

    private final transient Tema tema;
    private final JLabel titulo = new JLabel("# geral");
    private final JLabel resumoDeUsuarios = new JLabel("ninguém online");
    private final IndicadorConexao indicador;
    private final JButton botaoTema = new JButton("Tema escuro");
    private final JButton botaoHistorico = new JButton("Histórico");
    private final JButton botaoBuscar = new JButton("Buscar (Ctrl+F)");
    private final JButton botaoExportar = new JButton("Exportar");
    private final JButton botaoSair = new JButton("Sair (Ctrl+Q)");
    private final PainelLateral lateral;
    private final PainelMensagens mensagens;
    private final PainelEntrada entrada;
    private final JLabel faixaDeAviso = new JLabel(" ");
    private final JPanel topo = new JPanel(new GridBagLayout());
    private final JToolBar barra = new JToolBar();
    private final Timer relogioDaFaixa;
    private final Timer relogioDoHistorico;

    private volatile transient ChatController controlador;
    private transient Runnable aoSair = () -> { };
    private transient Consumer<String> aoSessaoPerdida = motivo -> { };
    private transient Runnable aoMensagemDeOutro = () -> { };
    private transient Supplier<String> solicitadorDeSala = this::pedirNomeDeSala;
    private transient Function<String, Optional<Path>> seletorDeArquivo = this::escolherArquivo;
    private transient DialogoBusca dialogoDeBusca;
    private String usuario = "";
    private String salaAtual = "geral";

    /** Cria o painel (chame na EDT). */
    public PainelChat(Tema tema) {
        super(new BorderLayout());
        this.tema = Objects.requireNonNull(tema, "tema");
        this.indicador = new IndicadorConexao(tema);
        this.lateral = new PainelLateral(tema, this::entrarNaSala, this::abrirPrivada, this::criarSala);
        this.mensagens = new PainelMensagens(tema);
        this.entrada = new PainelEntrada(tema, this::enviar);
        this.relogioDaFaixa = new Timer(TEMPO_DA_FAIXA_MS, e -> esconderFaixa());
        this.relogioDaFaixa.setRepeats(false);
        this.relogioDoHistorico = new Timer(TEMPO_MAXIMO_DO_HISTORICO_MS, e -> liberarHistorico());
        this.relogioDoHistorico.setRepeats(false);
        montar();
        instalarAtalhos();
        aplicarTema();
        tema.aoMudar(this::aplicarTema);
        definirConexaoNaEdt(EstadoConexao.DESCONECTADO);
    }

    // ------------------------------------------------------------------ montagem

    private void montar() {
        titulo.setToolTipText("Sala atual");
        titulo.getAccessibleContext().setAccessibleName("Sala atual");
        resumoDeUsuarios.setToolTipText("Usuários online na sala");
        resumoDeUsuarios.getAccessibleContext().setAccessibleName("Usuários online");
        botaoTema.setToolTipText("Alternar entre tema claro e escuro");
        botaoTema.getAccessibleContext().setAccessibleName("Alternar tema");
        botaoHistorico.setToolTipText("Carregar mensagens anteriores da sala");
        botaoHistorico.getAccessibleContext().setAccessibleName("Carregar histórico");
        botaoBuscar.setToolTipText("Buscar no histórico da sala (Ctrl+F)");
        botaoBuscar.getAccessibleContext().setAccessibleName("Buscar");
        botaoExportar.setToolTipText("Salvar as mensagens da sala que estão na tela em um arquivo .txt");
        botaoExportar.getAccessibleContext().setAccessibleName("Exportar");
        botaoSair.setToolTipText("Sair do chat (Ctrl+Q)");
        botaoSair.getAccessibleContext().setAccessibleName("Sair");
        faixaDeAviso.setVisible(false);
        faixaDeAviso.setToolTipText("Clique para fechar este aviso");
        faixaDeAviso.getAccessibleContext().setAccessibleName("Aviso");
        faixaDeAviso.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                esconderFaixa();
            }
        });

        botaoTema.addActionListener(e -> {
            tema.alternar();
            tema.instalarLookAndFeel();
        });
        botaoHistorico.addActionListener(e -> {
            Botoes.aplicar(botaoHistorico, EstadoBotao.CARREGANDO);
            relogioDoHistorico.restart();
            acao(ChatController::carregarHistoricoAnterior);
        });
        botaoBuscar.addActionListener(e -> abrirBusca());
        botaoExportar.addActionListener(e -> exportar());
        botaoSair.addActionListener(e -> aoSair.run());

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1), Tema.espaco(1));
        c.gridy = 0;
        c.gridx = 0;
        topo.add(titulo, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        topo.add(resumoDeUsuarios, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        topo.add(indicador, c);
        c.gridx = 3;
        topo.add(botaoTema, c);

        barra.setFloatable(false);
        barra.setRollover(true);
        barra.add(botaoHistorico);
        barra.add(botaoBuscar);
        barra.add(botaoExportar);
        barra.addSeparator();
        barra.add(botaoSair);

        JPanel norte = new JPanel();
        norte.setLayout(new BoxLayout(norte, BoxLayout.Y_AXIS));
        norte.setOpaque(false);
        topo.setAlignmentX(Component.LEFT_ALIGNMENT);
        barra.setAlignmentX(Component.LEFT_ALIGNMENT);
        norte.add(topo);
        norte.add(barra);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        centro.add(mensagens, BorderLayout.CENTER);
        centro.add(faixaDeAviso, BorderLayout.SOUTH);

        add(norte, BorderLayout.NORTH);
        add(lateral, BorderLayout.WEST);
        add(centro, BorderLayout.CENTER);
        add(entrada, BorderLayout.SOUTH);
    }

    private void instalarAtalhos() {
        registrarAtalho("control F", "buscar", this::abrirBusca);
        registrarAtalho("control Q", "sair", () -> aoSair.run());
    }

    private void registrarAtalho(String tecla, String nome, Runnable acao) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(tecla), nome);
        getActionMap().put(nome, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                acao.run();
            }
        });
    }

    private void aplicarTema() {
        Tema.Paleta p = tema.paleta();
        setBackground(p.fundo());
        topo.setBackground(p.superficie());
        topo.setOpaque(true);
        topo.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, p.borda()));
        titulo.setFont(tema.fonteTitulo());
        titulo.setForeground(p.textoDestaque());
        resumoDeUsuarios.setFont(tema.fonteCorpo());
        resumoDeUsuarios.setForeground(p.textoSecundario());
        for (JButton b : List.of(botaoTema, botaoHistorico, botaoBuscar, botaoExportar, botaoSair)) {
            b.setFont(tema.fonteCorpo());
        }
        botaoTema.setText(tema.modo() == Tema.Modo.CLARO ? "Tema escuro" : "Tema claro");
        faixaDeAviso.setFont(tema.fonteCorpo());
        faixaDeAviso.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(2), Tema.espaco(1), Tema.espaco(2)));
        repaint();
    }

    // ------------------------------------------------------------------ configuração

    /** Define o controlador que recebe as ações do usuário. */
    public void definirControlador(ChatController controlador) {
        this.controlador = controlador;
    }

    /** O que fazer quando o usuário pede para sair (botão ou Ctrl+Q). */
    public void aoSair(Runnable acao) {
        this.aoSair = Objects.requireNonNull(acao, "acao");
    }

    /** O que fazer quando a sessão termina sem o usuário pedir (recebe o motivo). */
    public void aoSessaoPerdida(Consumer<String> acao) {
        this.aoSessaoPerdida = Objects.requireNonNull(acao, "acao");
    }

    /** Chamado (na EDT) quando chega mensagem de outra pessoa; a janela usa para contar não lidas. */
    public void aoMensagemDeOutro(Runnable acao) {
        this.aoMensagemDeOutro = Objects.requireNonNull(acao, "acao");
    }

    /** Troca a forma de perguntar o nome de uma nova sala (testes injetam uma resposta). */
    void definirSolicitadorDeSala(Supplier<String> solicitador) {
        this.solicitadorDeSala = Objects.requireNonNull(solicitador, "solicitador");
    }

    /** Troca a forma de escolher o arquivo de exportação (testes injetam uma resposta). */
    void definirSeletorDeArquivo(Function<String, Optional<Path>> seletor) {
        this.seletorDeArquivo = Objects.requireNonNull(seletor, "seletor");
    }

    // ------------------------------------------------------------------ ações do usuário

    /** Roda uma ação do controlador em segundo plano; erro inesperado vira aviso, nunca exceção solta. */
    private void acao(Consumer<ChatController> tarefa) {
        ChatController c = controlador;
        if (c == null) {
            mostrarErro("Você não está conectado. Entre novamente para continuar.");
            return;
        }
        Segundo.plano(() -> tarefa.accept(c), () -> { }, falha -> {
            LOG.log(Level.SEVERE, "Falha inesperada em uma ação: " + falha.getMessage(), falha);
            mostrarErro("Ocorreu um erro inesperado. Tente novamente.");
        });
    }

    private void enviar(String texto) {
        acao(c -> c.enviar(texto));
    }

    private void entrarNaSala(String sala) {
        acao(c -> c.entrarSala(sala));
    }

    private void criarSala() {
        String nome = solicitadorDeSala.get();
        if (nome != null && !nome.isBlank()) {
            entrarNaSala(nome.strip());
        }
    }

    private String pedirNomeDeSala() {
        return JOptionPane.showInputDialog(this, "Nome da nova sala (letras sem acento, números ou _):",
                "Nova sala", JOptionPane.PLAIN_MESSAGE);
    }

    /** Duplo clique em um usuário: prepara uma mensagem privada no campo de texto. */
    private void abrirPrivada(String nome) {
        entrada.campo().setText("/msg " + nome + " ");
        entrada.campo().requestFocusInWindow();
        entrada.campo().setCaretPosition(entrada.campo().getText().length());
    }

    /** Abre o diálogo de busca (Ctrl+F). */
    public void abrirBusca() {
        if (controlador == null) {
            mostrarErro("Você não está conectado. Entre novamente para continuar.");
            return;
        }
        if (dialogoDeBusca == null) {
            dialogoDeBusca = new DialogoBusca(SwingUtilities.getWindowAncestor(this), tema, new PainelBusca.Acoes() {
                @Override
                public void porUsuario(String nome) {
                    buscar(c -> c.buscarPorUsuario(nome));
                }

                @Override
                public void porTexto(String trecho) {
                    buscar(c -> c.buscarPorTexto(trecho));
                }

                @Override
                public void porPeriodo(String inicio, String fim) {
                    buscar(c -> c.buscarPorPeriodo(inicio, fim));
                }
            });
        }
        dialogoDeBusca.abrir(salaAtual);
    }

    private void buscar(Consumer<ChatController> tarefa) {
        ChatController c = controlador;
        if (c == null) {
            mostrarErroDeBusca("Você não está conectado. Entre novamente para continuar.");
            return;
        }
        Segundo.plano(() -> tarefa.accept(c), () -> { }, falha -> {
            LOG.log(Level.SEVERE, "Falha inesperada na busca: " + falha.getMessage(), falha);
            mostrarErroDeBusca("Ocorreu um erro inesperado na busca. Tente novamente.");
        });
    }

    private void exportar() {
        ChatController c = controlador;
        if (c == null) {
            mostrarErro("Você não está conectado. Entre novamente para continuar.");
            return;
        }
        Optional<Path> destino = seletorDeArquivo.apply("historico-" + c.salaAtual() + ".txt");
        if (destino.isEmpty()) {
            return;
        }
        Botoes.aplicar(botaoExportar, EstadoBotao.CARREGANDO);
        Segundo.plano(() -> c.exportar(destino.get()), arquivo -> {
            Botoes.aplicar(botaoExportar, EstadoBotao.ATIVO);
            mostrarAviso("Histórico exportado para " + arquivo.getFileName() + ".");
        }, falha -> {
            Botoes.aplicar(botaoExportar, EstadoBotao.ATIVO);
            if (falha instanceof IllegalStateException) {
                mostrarErro(falha.getMessage());
            } else if (falha instanceof IOException) {
                mostrarErro("Não foi possível gravar o arquivo " + destino.get().getFileName()
                        + ". Verifique se a pasta existe e se você tem permissão para gravar nela.");
            } else {
                LOG.log(Level.SEVERE, "Falha inesperada ao exportar: " + falha.getMessage(), falha);
                mostrarErro("Ocorreu um erro inesperado ao exportar. Tente novamente.");
            }
        });
    }

    private Optional<Path> escolherArquivo(String sugestao) {
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Exportar histórico");
        seletor.setSelectedFile(new File(sugestao));
        seletor.setFileFilter(new FileNameExtensionFilter("Arquivo de texto (*.txt)", "txt"));
        if (seletor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        File arquivo = seletor.getSelectedFile();
        if (!arquivo.getName().toLowerCase().endsWith(".txt")) {
            arquivo = new File(arquivo.getPath() + ".txt");
        }
        if (arquivo.exists() && JOptionPane.showConfirmDialog(this,
                "O arquivo " + arquivo.getName() + " já existe. Deseja substituí-lo?", "Substituir arquivo",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return Optional.empty();
        }
        return Optional.of(arquivo.toPath());
    }

    // ------------------------------------------------------------------ ChatView (qualquer thread)

    private static void emEdt(Runnable tarefa) {
        if (SwingUtilities.isEventDispatchThread()) {
            tarefa.run();
        } else {
            SwingUtilities.invokeLater(tarefa);
        }
    }

    @Override
    public void definirUsuario(String nome) {
        emEdt(() -> usuario = nome);
    }

    @Override
    public void mostrarConexao(EstadoConexao estado) {
        emEdt(() -> definirConexaoNaEdt(estado));
    }

    private void definirConexaoNaEdt(EstadoConexao estado) {
        indicador.definir(estado);
        boolean conectado = estado == EstadoConexao.CONECTADO;
        entrada.definirHabilitado(conectado);
        for (JButton b : List.of(botaoHistorico, botaoBuscar, botaoExportar)) {
            b.setEnabled(conectado);
        }
        botaoNovaSalaHabilitado(conectado);
    }

    private void botaoNovaSalaHabilitado(boolean ligado) {
        lateral.botaoNovaSala().setEnabled(ligado);
    }

    @Override
    public void adicionar(MensagemTela mensagem) {
        emEdt(() -> {
            mensagens.adicionar(mensagem);
            if (mensagem.tipo() != Tipo.SISTEMA && !mensagem.minha()) {
                aoMensagemDeOutro.run();
            }
        });
    }

    @Override
    public void substituirHistorico(List<MensagemTela> historico, String sala) {
        emEdt(() -> {
            mensagens.substituir(historico);
            salaAtual = sala;
            titulo.setText("# " + sala);
            liberarHistorico();
        });
    }

    @Override
    public void inserirAnteriores(List<MensagemTela> anteriores) {
        emEdt(() -> {
            mensagens.inserirNoInicio(anteriores);
            liberarHistorico();
        });
    }

    @Override
    public void atualizarUsuarios(String sala, List<String> nomes) {
        emEdt(() -> {
            lateral.atualizarUsuarios(nomes, usuario);
            atualizarResumo();
        });
    }

    private void atualizarResumo() {
        List<String> nomes = lateral.usuarios();
        if (nomes.isEmpty()) {
            resumoDeUsuarios.setText("ninguém online");
            return;
        }
        int mostrar = Math.min(nomes.size(), USUARIOS_NO_RESUMO);
        String lista = String.join(", ", nomes.subList(0, mostrar));
        int restantes = nomes.size() - mostrar;
        resumoDeUsuarios.setText(nomes.size() + " online: " + lista + (restantes > 0 ? " e mais " + restantes : ""));
    }

    @Override
    public void atualizarSalas(List<String> salas, String atual) {
        emEdt(() -> {
            lateral.atualizarSalas(salas, atual);
            salaAtual = atual;
            titulo.setText("# " + atual);
        });
    }

    @Override
    public void mostrarResultadoBusca(List<MensagemTela> resultados) {
        emEdt(() -> {
            if (dialogoDeBusca != null) {
                dialogoDeBusca.painel().mostrarResultado(resultados);
            }
        });
    }

    @Override
    public void mostrarErroDeBusca(String texto) {
        emEdt(() -> {
            if (dialogoDeBusca != null) {
                dialogoDeBusca.painel().mostrarErro(texto);
            } else {
                mostrarFaixa(texto, true);
            }
        });
    }

    @Override
    public void mostrarErro(String texto) {
        emEdt(() -> {
            mostrarFaixa(texto, true);
            liberarHistorico();
        });
    }

    @Override
    public void mostrarAviso(String texto) {
        emEdt(() -> {
            mostrarFaixa(texto, false);
            liberarHistorico();
        });
    }

    @Override
    public void sessaoEncerrada(String motivo) {
        emEdt(() -> {
            mostrarFaixa(motivo, true);
            liberarHistorico();
            aoSessaoPerdida.accept(motivo);
        });
    }

    private void mostrarFaixa(String texto, boolean erro) {
        faixaDeAviso.setText(texto);
        faixaDeAviso.setForeground(erro ? tema.paleta().erro() : tema.paleta().textoSecundario());
        faixaDeAviso.setVisible(true);
        relogioDaFaixa.restart();
        revalidate();
    }

    private void esconderFaixa() {
        faixaDeAviso.setVisible(false);
        faixaDeAviso.setText(" ");
        revalidate();
    }

    private void liberarHistorico() {
        relogioDoHistorico.stop();
        if (botaoHistorico.getText().equals(Botoes.TEXTO_CARREGANDO)) {
            Botoes.aplicar(botaoHistorico, EstadoBotao.ATIVO);
            botaoHistorico.setEnabled(indicador.estado() == EstadoConexao.CONECTADO);
        }
    }

    // ------------------------------------------------------------------ acesso (janela e testes)

    /** Ordem do Tab: barra de ferramentas, laterais, caixa de mensagem e Enviar. */
    public List<Component> ordemDeFoco() {
        return List.of(botaoHistorico, botaoBuscar, botaoExportar, botaoSair, botaoTema,
                lateral.listaDeSalas(), lateral.botaoNovaSala(), lateral.listaDeUsuarios(),
                entrada.campo(), entrada.botaoEnviar());
    }

    /** Quem recebe o foco ao abrir a janela: a caixa de mensagem. */
    public Component componenteInicial() {
        return entrada.campo();
    }

    /** Nome da sala em maiúsculas e minúsculas como no título. */
    public JLabel titulo() {
        return titulo;
    }

    /** Resumo "n online: ...". */
    public JLabel resumoDeUsuarios() {
        return resumoDeUsuarios;
    }

    /** Indicador de conexão. */
    public IndicadorConexao indicador() {
        return indicador;
    }

    /** Rodapé de entrada. */
    public PainelEntrada entrada() {
        return entrada;
    }

    /** Painel lateral. */
    public PainelLateral lateral() {
        return lateral;
    }

    /** Lista de mensagens. */
    public PainelMensagens mensagens() {
        return mensagens;
    }

    /** Faixa de avisos e erros. */
    public JLabel faixaDeAviso() {
        return faixaDeAviso;
    }

    /** Botão Histórico. */
    public JButton botaoHistorico() {
        return botaoHistorico;
    }

    /** Botão Buscar. */
    public JButton botaoBuscar() {
        return botaoBuscar;
    }

    /** Botão Exportar. */
    public JButton botaoExportar() {
        return botaoExportar;
    }

    /** Botão Sair. */
    public JButton botaoSair() {
        return botaoSair;
    }

    /** Botão de tema. */
    public JButton botaoTema() {
        return botaoTema;
    }

    /** Diálogo de busca, se já foi aberto. */
    public DialogoBusca dialogoDeBusca() {
        return dialogoDeBusca;
    }
}
