package br.unicesumar.chat.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListCellRenderer;

/**
 * Lado esquerdo da janela: lista de salas (um clique entra) com o botão "Nova sala", e os
 * usuários online da sala (duplo clique abre conversa privada), com o próprio usuário em
 * destaque e sempre em primeiro.
 */
public class PainelLateral extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int LARGURA = 240;
    private static final int LARGURA_DOS_MEMBROS = 220;
    private static final int TAMANHO_DO_AVATAR = 28;

    private final transient Tema tema;
    private final transient Consumer<String> aoEntrarNaSala;
    private final transient Consumer<String> aoAbrirPrivada;
    private final DefaultListModel<String> modeloDeSalas = new DefaultListModel<>();
    private final DefaultListModel<String> modeloDeUsuarios = new DefaultListModel<>();
    private final JList<String> listaDeSalas = new JList<>(modeloDeSalas);
    private final JList<String> listaDeUsuarios = new JList<>(modeloDeUsuarios);
    private final DefaultListModel<String> modeloDeConversas = new DefaultListModel<>();
    private final JList<String> listaDeConversas = new JList<>(modeloDeConversas);
    private final JLabel tituloDeConversas = new JLabel("CONVERSAS");
    private final JScrollPane rolagemDeConversas = new JScrollPane(listaDeConversas);
    private final JPanel colunaDosMembros = new JPanel(new BorderLayout());
    private final JPanel cartaoDoUsuario = new JPanel(new java.awt.GridBagLayout());
    private final JLabel avatarDoUsuario = new JLabel();
    private final JLabel nomeDoUsuario = new JLabel(" ");
    private final JLabel statusDoUsuario = new JLabel("Desconectado");
    private boolean conectado;
    private String nomeDoCartao = "";
    private Map<String, Integer> naoLidas = Map.of();
    private String conversaAtual;
    private final JLabel tituloDeSalas = new JLabel("SALAS");
    private final JLabel tituloDeUsuarios = new JLabel("ONLINE (0)");
    private final JButton botaoNovaSala = new JButton("Nova sala");
    private final JScrollPane rolagemDeSalas = new JScrollPane(listaDeSalas);
    private final JScrollPane rolagemDeUsuarios = new JScrollPane(listaDeUsuarios);
    private String salaAtual = "";
    private String eu = "";

    /**
     * Cria o painel.
     *
     * @param aoEntrarNaSala chamado com o nome da sala clicada (se não for a atual)
     * @param aoAbrirPrivada chamado com o usuário de duplo clique (se não for o próprio)
     * @param aoCriarSala    chamado ao clicar em "Nova sala"
     */
    public PainelLateral(Tema tema, Consumer<String> aoEntrarNaSala, Consumer<String> aoAbrirPrivada,
            Runnable aoCriarSala) {
        super(new BorderLayout());
        this.tema = Objects.requireNonNull(tema, "tema");
        this.aoEntrarNaSala = Objects.requireNonNull(aoEntrarNaSala, "aoEntrarNaSala");
        this.aoAbrirPrivada = Objects.requireNonNull(aoAbrirPrivada, "aoAbrirPrivada");
        Objects.requireNonNull(aoCriarSala, "aoCriarSala");
        botaoNovaSala.addActionListener(e -> aoCriarSala.run());
        montar();
        aplicarTema();
        tema.aoMudar(this::aplicarTema);
    }

    private void montar() {
        setPreferredSize(new Dimension(LARGURA, 0));
        JPanel conteudo = new JPanel();
        conteudo.setOpaque(false);
        conteudo.setLayout(new BoxLayout(conteudo, BoxLayout.Y_AXIS));
        conteudo.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1), Tema.espaco(1)));

        listaDeSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaDeSalas.setCellRenderer(new RenderizadorDeSalas());
        listaDeSalas.setToolTipText("Salas ativas. Clique em uma sala para entrar nela.");
        listaDeSalas.getAccessibleContext().setAccessibleName("Lista de salas");
        listaDeSalas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int indice = indiceNoPonto(listaDeSalas, e);
                if (indice >= 0) {
                    acionarSala(indice);
                }
            }
        });
        associarEnter(listaDeSalas, () -> acionarSala(listaDeSalas.getSelectedIndex()));

        listaDeUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaDeUsuarios.setCellRenderer(new CelulaDeMembro());
        listaDeUsuarios.setToolTipText("Usuários online nesta sala. Duplo clique abre uma conversa privada.");
        listaDeUsuarios.getAccessibleContext().setAccessibleName("Lista de usuários online");
        listaDeUsuarios.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int indice = indiceNoPonto(listaDeUsuarios, e);
                if (e.getClickCount() == 2 && indice >= 0) {
                    acionarUsuario(indice);
                }
            }
        });
        associarEnter(listaDeUsuarios, () -> acionarUsuario(listaDeUsuarios.getSelectedIndex()));

        listaDeConversas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaDeConversas.setCellRenderer(new CelulaDeConversa());
        listaDeConversas.setToolTipText("Suas conversas privadas. Clique para abrir; o número é de mensagens não lidas.");
        listaDeConversas.getAccessibleContext().setAccessibleName("Lista de conversas privadas");
        listaDeConversas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int indice = indiceNoPonto(listaDeConversas, e);
                if (indice >= 0) {
                    acionarConversa(indice);
                }
            }
        });
        associarEnter(listaDeConversas, () -> acionarConversa(listaDeConversas.getSelectedIndex()));
        tituloDeConversas.setAlignmentX(Component.LEFT_ALIGNMENT);
        rolagemDeConversas.setAlignmentX(Component.LEFT_ALIGNMENT);
        rolagemDeConversas.setBorder(null);

        botaoNovaSala.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        botaoNovaSala.setToolTipText("Criar uma sala nova e entrar nela");
        botaoNovaSala.getAccessibleContext().setAccessibleName("Nova sala");
        botaoNovaSala.setAlignmentX(Component.LEFT_ALIGNMENT);
        tituloDeSalas.setAlignmentX(Component.LEFT_ALIGNMENT);
        tituloDeUsuarios.setAlignmentX(Component.LEFT_ALIGNMENT);
        rolagemDeSalas.setAlignmentX(Component.LEFT_ALIGNMENT);
        rolagemDeUsuarios.setAlignmentX(Component.LEFT_ALIGNMENT);
        rolagemDeSalas.setBorder(null);
        rolagemDeUsuarios.setBorder(null);
        rolagemDeSalas.setPreferredSize(new Dimension(LARGURA, Tema.espaco(15)));
        rolagemDeSalas.setMaximumSize(new Dimension(Integer.MAX_VALUE, Tema.espaco(15)));

        conteudo.add(tituloDeSalas);
        conteudo.add(rolagemDeSalas);
        conteudo.add(javax.swing.Box.createVerticalStrut(Tema.espaco(1)));
        conteudo.add(botaoNovaSala);
        conteudo.add(javax.swing.Box.createVerticalStrut(Tema.espaco(2)));
        conteudo.add(tituloDeConversas);
        conteudo.add(rolagemDeConversas);
        add(conteudo, BorderLayout.CENTER);
        montarCartaoDoUsuario();
        add(cartaoDoUsuario, BorderLayout.SOUTH);
        montarColunaDosMembros();
    }

    private void montarColunaDosMembros() {
        colunaDosMembros.setPreferredSize(new Dimension(LARGURA_DOS_MEMBROS, 0));
        tituloDeUsuarios.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(2), Tema.espaco(2), Tema.espaco(1),
                Tema.espaco(1)));
        colunaDosMembros.add(tituloDeUsuarios, BorderLayout.NORTH);
        colunaDosMembros.add(rolagemDeUsuarios, BorderLayout.CENTER);
        colunaDosMembros.getAccessibleContext().setAccessibleName("Membros online");
    }

    private void montarCartaoDoUsuario() {
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        c.insets = new java.awt.Insets(0, 0, 0, Tema.espaco(1));
        cartaoDoUsuario.add(avatarDoUsuario, c);
        c.gridx = 1;
        c.gridheight = 1;
        c.weightx = 1;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        cartaoDoUsuario.add(nomeDoUsuario, c);
        c.gridy = 1;
        cartaoDoUsuario.add(statusDoUsuario, c);
        cartaoDoUsuario.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1),
                Tema.espaco(1)));
        cartaoDoUsuario.getAccessibleContext().setAccessibleName("Você");
    }

    private static int indiceNoPonto(JList<String> lista, MouseEvent e) {
        int indice = lista.locationToIndex(e.getPoint());
        if (indice < 0) {
            return -1;
        }
        Rectangle celula = lista.getCellBounds(indice, indice);
        return celula != null && celula.contains(e.getPoint()) ? indice : -1;
    }

    private static void associarEnter(JList<String> lista, Runnable acao) {
        lista.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "acionar");
        lista.getActionMap().put("acionar", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                acao.run();
            }
        });
    }

    private void aplicarTema() {
        Tema.Paleta p = tema.paleta();
        setBackground(p.superficie());
        rolagemDeSalas.setBorder(null); // o FlatLaf reinstala a borda padrão ao trocar de visual
        rolagemDeUsuarios.setBorder(null);
        rolagemDeConversas.setBorder(null);
        colunaDosMembros.setBackground(p.superficie());
        colunaDosMembros.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, p.borda()));
        cartaoDoUsuario.setBackground(p.superficie());
        cartaoDoUsuario.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, p.borda()),
                BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1), Tema.espaco(1))));
        nomeDoUsuario.setFont(tema.fonteCorpo().deriveFont(Font.BOLD));
        nomeDoUsuario.setForeground(p.texto());
        statusDoUsuario.setFont(tema.fontePequena());
        statusDoUsuario.setForeground(p.textoSecundario());
        atualizarAvatarDoCartao();
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, p.borda()));
        for (JLabel titulo : List.of(tituloDeSalas, tituloDeUsuarios, tituloDeConversas)) {
            titulo.setFont(tema.fontePequena().deriveFont(Font.BOLD));
            titulo.setForeground(p.textoSecundario());
        }
        for (JList<String> lista : List.of(listaDeSalas, listaDeUsuarios, listaDeConversas)) {
            lista.setFont(tema.fonteCorpo());
            lista.setBackground(p.superficie());
            lista.setForeground(p.texto());
            lista.setSelectionBackground(p.borda());
            lista.setSelectionForeground(p.texto());
        }
        botaoNovaSala.setFont(tema.fonteCorpo());
        repaint();
    }

    /** Atualiza as salas e destaca a atual (que entra na lista se ainda não estiver). */
    public void atualizarSalas(List<String> salas, String atual) {
        Objects.requireNonNull(salas, "salas");
        Objects.requireNonNull(atual, "atual");
        salaAtual = atual;
        modeloDeSalas.clear();
        List<String> todas = new ArrayList<>(salas);
        if (!todas.contains(atual)) {
            todas.add(atual);
        }
        todas.forEach(modeloDeSalas::addElement);
        listaDeSalas.repaint();
    }

    /** Atualiza os usuários online: o próprio primeiro, depois os outros em ordem alfabética. */
    public void atualizarUsuarios(List<String> nomes, String proprio) {
        Objects.requireNonNull(nomes, "nomes");
        eu = Objects.requireNonNull(proprio, "proprio");
        List<String> ordenados = new ArrayList<>(nomes);
        ordenados.sort(Comparator.<String, Boolean>comparing(n -> !n.equalsIgnoreCase(proprio))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        modeloDeUsuarios.clear();
        ordenados.forEach(modeloDeUsuarios::addElement);
        tituloDeUsuarios.setText("ONLINE (" + ordenados.size() + ")");
        listaDeUsuarios.repaint();
    }

    /** Clique em uma sala: entra nela, a menos que seja a atual. */
    public void acionarSala(int indice) {
        if (indice < 0 || indice >= modeloDeSalas.size()) {
            return;
        }
        String sala = modeloDeSalas.get(indice);
        if (!sala.equals(salaAtual) || conversaAtual != null) { // dentro de uma conversa, a sala atual é o "voltar"
            aoEntrarNaSala.accept(sala);
        }
    }

    /** Duplo clique em um usuário: abre conversa privada, a menos que seja o próprio. */
    public void acionarUsuario(int indice) {
        if (indice < 0 || indice >= modeloDeUsuarios.size()) {
            return;
        }
        String nome = modeloDeUsuarios.get(indice);
        if (!nome.equalsIgnoreCase(eu)) {
            aoAbrirPrivada.accept(nome);
        }
    }

    /** Define o nome mostrado no cartão do usuário (rodapé da coluna da esquerda). */
    public void definirUsuarioAtual(String nome) {
        nomeDoCartao = Objects.requireNonNull(nome, "nome");
        nomeDoUsuario.setText(nome.isBlank() ? " " : nome);
        atualizarAvatarDoCartao();
    }

    /** Diz se há conexão: o cartão mostra "Conectado" ou "Desconectado". */
    public void definirConectado(boolean ligado) {
        conectado = ligado;
        statusDoUsuario.setText(ligado ? "Conectado" : "Desconectado");
        atualizarAvatarDoCartao();
    }

    private void atualizarAvatarDoCartao() {
        avatarDoUsuario.setIcon(new AvatarIcon(nomeDoCartao, TAMANHO_DO_AVATAR + 4, conectado, tema));
    }

    /** Nome mostrado no cartão do usuário. */
    public String nomeDoCartao() {
        return nomeDoCartao;
    }

    /** Texto de status do cartão do usuário: "Conectado" ou "Desconectado". */
    public String statusDoCartao() {
        return statusDoUsuario.getText();
    }

    /** Coluna da direita com os membros online; quem monta a janela a coloca no lado direito. */
    public JPanel colunaDosMembros() {
        return colunaDosMembros;
    }

    /** Atualiza as conversas privadas (a conversa aberta, se houver, continua na lista). */
    public void atualizarConversas(List<String> nomes) {
        Objects.requireNonNull(nomes, "nomes");
        modeloDeConversas.clear();
        nomes.forEach(modeloDeConversas::addElement);
        garantirConversaAberta();
        listaDeConversas.repaint();
    }

    /** Atualiza as mensagens não lidas de cada conversa (nome do interlocutor para a quantidade). */
    public void atualizarNaoLidas(Map<String, Integer> naoLidas) {
        this.naoLidas = Map.copyOf(Objects.requireNonNull(naoLidas, "naoLidas"));
        listaDeConversas.repaint();
    }

    /** Marca a conversa aberta (ou {@code null} quando se volta para a sala). */
    public void selecionarConversa(String nome) {
        conversaAtual = nome;
        garantirConversaAberta();
        if (nome == null) {
            listaDeConversas.clearSelection();
        } else {
            listaDeConversas.setSelectedValue(nome, true);
        }
        listaDeSalas.repaint();
        listaDeConversas.repaint();
    }

    private void garantirConversaAberta() {
        String atual = conversaAtual;
        if (atual != null && !java.util.Collections.list(modeloDeConversas.elements()).contains(atual)) {
            modeloDeConversas.addElement(atual);
        }
    }

    /** Clique em uma conversa: abre ela. */
    public void acionarConversa(int indice) {
        if (indice < 0 || indice >= modeloDeConversas.size()) {
            return;
        }
        aoAbrirPrivada.accept(modeloDeConversas.get(indice));
    }

    /** Nomes das conversas, na ordem exibida. */
    public List<String> conversas() {
        return java.util.Collections.list(modeloDeConversas.elements());
    }

    /** Interlocutor da conversa aberta, ou {@code null}. */
    public String conversaAtual() {
        return conversaAtual;
    }

    /** Texto mostrado para a conversa do índice: o nome, e a quantidade de não lidas entre parênteses. */
    public String rotuloDaConversa(int indice) {
        String nome = modeloDeConversas.get(indice);
        int n = naoLidasDe(nome);
        return n > 0 ? nome + " (" + n + ")" : nome;
    }

    /** Soma das mensagens não lidas de todas as conversas. */
    public int totalDeNaoLidas() {
        return naoLidas.values().stream().mapToInt(Integer::intValue).sum();
    }

    private int naoLidasDe(String nome) {
        Integer exato = naoLidas.get(nome);
        if (exato != null) {
            return exato;
        }
        return naoLidas.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(nome))
                .mapToInt(Map.Entry::getValue).findFirst().orElse(0);
    }

    /** Título "CONVERSAS". */
    public String tituloDasConversas() {
        return tituloDeConversas.getText();
    }

    /** A lista de conversas. */
    public JList<String> listaDeConversas() {
        return listaDeConversas;
    }

    /** Rolagem da lista de conversas. */
    public JScrollPane rolagemDeConversas() {
        return rolagemDeConversas;
    }

    /** Nomes das salas listadas. */
    public List<String> salas() {
        return java.util.Collections.list(modeloDeSalas.elements());
    }

    /** Sala atual. */
    public String salaAtual() {
        return salaAtual;
    }

    /** Nomes dos usuários, na ordem exibida. */
    public List<String> usuarios() {
        return java.util.Collections.list(modeloDeUsuarios.elements());
    }

    /** Texto mostrado para o usuário do índice (o próprio ganha "(você)"). */
    public String rotuloDoUsuario(int indice) {
        String nome = modeloDeUsuarios.get(indice);
        return nome.equalsIgnoreCase(eu) ? nome + " (você)" : nome;
    }

    /** Título "ONLINE (n)". */
    public String tituloDosUsuarios() {
        return tituloDeUsuarios.getText();
    }

    /** O botão "Nova sala". */
    public JButton botaoNovaSala() {
        return botaoNovaSala;
    }

    /** Rolagem da lista de salas. */
    public JScrollPane rolagemDeSalas() {
        return rolagemDeSalas;
    }

    /** Rolagem da lista de usuários. */
    public JScrollPane rolagemDeUsuarios() {
        return rolagemDeUsuarios;
    }

    /** A lista de salas. */
    public JList<String> listaDeSalas() {
        return listaDeSalas;
    }

    /** A lista de usuários. */
    public JList<String> listaDeUsuarios() {
        return listaDeUsuarios;
    }

    private final class RenderizadorDeSalas extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                boolean selecionada, boolean foco) {
            JLabel l = (JLabel) super.getListCellRendererComponent(lista, valor, indice, selecionada, foco);
            boolean atual = conversaAtual == null && String.valueOf(valor).equals(salaAtual);
            l.setText("# " + valor);
            l.setFont(atual ? tema.fonteCorpo().deriveFont(Font.BOLD) : tema.fonteCorpo());
            l.setOpaque(true);
            if (!selecionada) {
                l.setForeground(atual ? tema.paleta().texto() : tema.paleta().textoSecundario());
                l.setBackground(atual ? tema.paleta().borda() : tema.paleta().superficie());
            }
            l.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1)));
            return l;
        }
    }

    /** Uma linha de avatar + nome (e, nas conversas, o emblema de não lidas à direita). */
    private abstract class CelulaComAvatar extends JPanel implements javax.swing.ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;
        final JLabel nome = new JLabel();
        final JLabel emblema = new JLabel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                try {
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(tema.paleta().destaque());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };

        CelulaComAvatar() {
            super(new BorderLayout(Tema.espaco(1), 0));
            add(nome, BorderLayout.CENTER);
            add(emblema, BorderLayout.EAST);
            emblema.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            emblema.setBorder(BorderFactory.createEmptyBorder(1, Tema.espaco(1), 1, Tema.espaco(1)));
            emblema.setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2,
                    Tema.espaco(1)));
        }

        /** Pinta fundo, texto e emblema (vazio esconde o emblema). */
        void preencher(String texto, String nomeDoAvatar, boolean online, boolean destacada, boolean selecionada,
                String textoDoEmblema, boolean negrito) {
            Tema.Paleta p = tema.paleta();
            nome.setIcon(new AvatarIcon(nomeDoAvatar, TAMANHO_DO_AVATAR, online, tema));
            nome.setText(texto);
            nome.setIconTextGap(Tema.espaco(1) + 2);
            nome.setFont(negrito ? tema.fonteCorpo().deriveFont(Font.BOLD) : tema.fonteCorpo());
            nome.setForeground(destacada || negrito ? p.texto() : p.textoSecundario());
            setBackground(selecionada || destacada ? p.borda() : p.superficie());
            emblema.setVisible(textoDoEmblema != null);
            emblema.setText(textoDoEmblema == null ? "" : textoDoEmblema);
            emblema.setFont(tema.fontePequena().deriveFont(Font.BOLD));
            emblema.setForeground(p.textoSobreDestaque());
        }
    }

    private final class CelulaDeMembro extends CelulaComAvatar {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> lista, String valor, int indice,
                boolean selecionada, boolean foco) {
            boolean proprio = valor.equalsIgnoreCase(eu);
            preencher(proprio ? valor + " (você)" : valor, valor, true, false, selecionada, null, proprio);
            return this;
        }
    }

    private final class CelulaDeConversa extends CelulaComAvatar {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> lista, String valor, int indice,
                boolean selecionada, boolean foco) {
            boolean aberta = conversaAtual != null && conversaAtual.equalsIgnoreCase(valor);
            int n = naoLidasDe(valor);
            boolean online = java.util.Collections.list(modeloDeUsuarios.elements()).stream()
                    .anyMatch(u -> u.equalsIgnoreCase(valor));
            preencher(valor, valor, online, aberta, selecionada, n > 0 ? String.valueOf(n) : null, n > 0);
            return this;
        }
    }
}
