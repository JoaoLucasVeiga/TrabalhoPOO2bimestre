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
    private static final int LARGURA = 208;

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
        listaDeUsuarios.setCellRenderer(new RenderizadorDeUsuarios());
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
        listaDeConversas.setCellRenderer(new RenderizadorDeConversas());
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
        rolagemDeConversas.setPreferredSize(new Dimension(LARGURA, Tema.espaco(14)));
        rolagemDeConversas.setMaximumSize(new Dimension(Integer.MAX_VALUE, Tema.espaco(14)));

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
        conteudo.add(tituloDeUsuarios);
        conteudo.add(rolagemDeUsuarios);
        conteudo.add(javax.swing.Box.createVerticalStrut(Tema.espaco(2)));
        conteudo.add(tituloDeConversas);
        conteudo.add(rolagemDeConversas);
        add(conteudo, BorderLayout.CENTER);
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
        for (JLabel titulo : List.of(tituloDeSalas, tituloDeUsuarios, tituloDeConversas)) {
            titulo.setFont(tema.fontePequena().deriveFont(Font.BOLD));
            titulo.setForeground(p.textoSecundario());
        }
        for (JList<String> lista : List.of(listaDeSalas, listaDeUsuarios, listaDeConversas)) {
            lista.setFont(tema.fonteCorpo());
            lista.setBackground(p.superficie());
            lista.setForeground(p.texto());
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
            if (!selecionada) {
                l.setForeground(atual ? tema.paleta().textoDestaque() : tema.paleta().texto());
                l.setBackground(tema.paleta().superficie());
            }
            l.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1)));
            return l;
        }
    }

    private final class RenderizadorDeUsuarios extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                boolean selecionada, boolean foco) {
            JLabel l = (JLabel) super.getListCellRendererComponent(lista, valor, indice, selecionada, foco);
            boolean proprio = String.valueOf(valor).equalsIgnoreCase(eu);
            l.setText(proprio ? valor + " (você)" : String.valueOf(valor));
            l.setFont(proprio ? tema.fonteCorpo().deriveFont(Font.BOLD) : tema.fonteCorpo());
            if (!selecionada) {
                l.setForeground(proprio ? tema.paleta().textoDestaque() : tema.paleta().texto());
                l.setBackground(tema.paleta().superficie());
            }
            l.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1)));
            return l;
        }
    }

    private final class RenderizadorDeConversas extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                boolean selecionada, boolean foco) {
            JLabel l = (JLabel) super.getListCellRendererComponent(lista, valor, indice, selecionada, foco);
            String nome = String.valueOf(valor);
            boolean aberta = conversaAtual != null && conversaAtual.equalsIgnoreCase(nome);
            boolean comNaoLidas = naoLidasDe(nome) > 0;
            l.setText("@ " + nome + (comNaoLidas ? " (" + naoLidasDe(nome) + ")" : ""));
            l.setFont(aberta || comNaoLidas ? tema.fonteCorpo().deriveFont(Font.BOLD) : tema.fonteCorpo());
            if (!selecionada) {
                l.setForeground(aberta || comNaoLidas ? tema.paleta().textoDestaque() : tema.paleta().texto());
                l.setBackground(tema.paleta().superficie());
            }
            l.setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1)));
            return l;
        }
    }
}
