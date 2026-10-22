package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.MensagemTela;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Conteúdo do diálogo de busca: abas Usuário, Texto e Período, resultados em tabela e uma linha
 * de status. Enquanto uma busca está em andamento os botões ficam "Carregando..." e uma segunda
 * busca não é disparada.
 */
public class PainelBusca extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** O que o painel pede à janela (que repassa ao controlador em segundo plano). */
    public interface Acoes {
        /** Busca por nome de usuário. */
        void porUsuario(String nome);

        /** Busca por trecho de texto. */
        void porTexto(String trecho);

        /** Busca entre duas datas (texto digitado, AAAA-MM-DD). */
        void porPeriodo(String inicio, String fim);
    }

    private final transient Tema tema;
    private final transient Acoes acoes;
    private final JLabel titulo = new JLabel("Buscar");
    private final JTabbedPane abas = new JTabbedPane();
    private final JTextField campoUsuario = new JTextField(20);
    private final JTextField campoTexto = new JTextField(20);
    private final JTextField campoInicio = new JTextField(10);
    private final JTextField campoFim = new JTextField(10);
    private final JButton[] botoes = {new JButton("Buscar"), new JButton("Buscar"), new JButton("Buscar")};
    private final DefaultTableModel modelo = new DefaultTableModel(new Object[] {"Hora", "Usuário", "Mensagem"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int linha, int coluna) {
            return false;
        }
    };
    private final JTable tabela = new JTable(modelo);
    private final JLabel status = new JLabel(" ");
    private boolean emAndamento;
    private boolean statusDeErro;

    /** Cria o painel. */
    public PainelBusca(Tema tema, Acoes acoes) {
        super(new BorderLayout(0, Tema.espaco(1)));
        this.tema = Objects.requireNonNull(tema, "tema");
        this.acoes = Objects.requireNonNull(acoes, "acoes");
        montar();
        aplicarTema();
        tema.aoMudar(this::aplicarTema);
    }

    private void montar() {
        setBorder(BorderFactory.createEmptyBorder(Tema.espaco(2), Tema.espaco(2), Tema.espaco(2), Tema.espaco(2)));
        abas.addTab("Usuário", aba("Nome do usuário", campoUsuario, null, null, botoes[0]));
        abas.addTab("Texto", aba("Texto da mensagem", campoTexto, null, null, botoes[1]));
        abas.addTab("Período", aba("De", campoInicio, "Até", campoFim, botoes[2]));
        abas.getAccessibleContext().setAccessibleName("Tipo de busca");

        campoUsuario.setToolTipText("Nome exato do usuário (sem diferenciar maiúsculas). Enter busca.");
        campoTexto.setToolTipText("Trecho de texto a procurar nas mensagens. Enter busca.");
        campoInicio.setToolTipText("Data inicial no formato AAAA-MM-DD, por exemplo 2026-10-01");
        campoFim.setToolTipText("Data final no formato AAAA-MM-DD, por exemplo 2026-10-31");
        campoUsuario.getAccessibleContext().setAccessibleName("Nome do usuário");
        campoTexto.getAccessibleContext().setAccessibleName("Texto da mensagem");
        campoInicio.getAccessibleContext().setAccessibleName("Data inicial");
        campoFim.getAccessibleContext().setAccessibleName("Data final");
        for (JTextField campo : List.of(campoUsuario, campoTexto, campoInicio, campoFim)) {
            campo.addActionListener(e -> buscarNaAbaAtual());
        }
        for (JButton botao : botoes) {
            botao.addActionListener(e -> buscarNaAbaAtual());
            botao.setToolTipText("Buscar na sala atual (Enter)");
        }

        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setFillsViewportHeight(true);
        tabela.getAccessibleContext().setAccessibleName("Resultados da busca");
        tabela.getColumnModel().getColumn(0).setPreferredWidth(110);
        tabela.getColumnModel().getColumn(1).setPreferredWidth(110);
        tabela.getColumnModel().getColumn(2).setPreferredWidth(420);
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(640, 220));

        JPanel centro = new JPanel(new BorderLayout(0, Tema.espaco(1)));
        centro.setOpaque(false);
        centro.add(abas, BorderLayout.NORTH);
        centro.add(rolagem, BorderLayout.CENTER);
        add(titulo, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private static JPanel aba(String rotulo1, JTextField campo1, String rotulo2, JTextField campo2, JButton botao) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1), Tema.espaco(1));
        c.gridy = 0;
        c.gridx = 0;
        p.add(new JLabel(rotulo1), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(campo1, c);
        int proxima = 2;
        if (campo2 != null) {
            c.gridx = proxima++;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            p.add(new JLabel(rotulo2), c);
            c.gridx = proxima++;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(campo2, c);
        }
        c.gridx = proxima;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(botao, c);
        return p;
    }

    private void aplicarTema() {
        Tema.Paleta p = tema.paleta();
        setBackground(p.fundo());
        titulo.setFont(tema.fonteTitulo());
        titulo.setForeground(p.texto());
        status.setFont(tema.fonteCorpo());
        status.setForeground(statusDeErro ? p.erro() : p.textoSecundario());
        tabela.setFont(tema.fonteCorpo());
        tabela.setRowHeight(Tema.espaco(3) + 4);
        tabela.getTableHeader().setFont(tema.fonteCorpo().deriveFont(java.awt.Font.BOLD));
        abas.setFont(tema.fonteCorpo());
        for (JTextField campo : List.of(campoUsuario, campoTexto, campoInicio, campoFim)) {
            campo.setFont(tema.fonteCorpo());
        }
        for (JButton botao : botoes) {
            botao.setFont(tema.fonteCorpo());
        }
    }

    /** Executa a busca da aba aberta (ignorado se já há uma em andamento). */
    public void buscarNaAbaAtual() {
        if (emAndamento) {
            return;
        }
        emAndamento = true;
        for (JButton botao : botoes) {
            Botoes.aplicar(botao, EstadoBotao.CARREGANDO);
        }
        definirStatus("Buscando...", false);
        switch (abas.getSelectedIndex()) {
            case 0 -> acoes.porUsuario(campoUsuario.getText());
            case 1 -> acoes.porTexto(campoTexto.getText());
            default -> acoes.porPeriodo(campoInicio.getText(), campoFim.getText());
        }
    }

    /** Mostra os resultados na tabela e libera os botões. */
    public void mostrarResultado(List<MensagemTela> resultados) {
        Objects.requireNonNull(resultados, "resultados");
        modelo.setRowCount(0);
        for (MensagemTela m : resultados) {
            modelo.addRow(new Object[] {m.horario().format(HORA), m.remetente(), m.texto()});
        }
        int n = resultados.size();
        definirStatus(n == 0 ? "Nenhum resultado." : n + (n == 1 ? " resultado" : " resultados"), false);
        liberar();
    }

    /** Mostra o erro na linha de status e libera os botões. */
    public void mostrarErro(String texto) {
        Objects.requireNonNull(texto, "texto");
        definirStatus(texto, true);
        liberar();
    }

    /** Esvazia a tabela e o status. */
    public void limparResultados() {
        modelo.setRowCount(0);
        definirStatus("", false);
        liberar();
    }

    private void liberar() {
        emAndamento = false;
        for (JButton botao : botoes) {
            Botoes.aplicar(botao, EstadoBotao.ATIVO);
        }
    }

    private void definirStatus(String texto, boolean erro) {
        statusDeErro = erro;
        status.setText(texto);
        status.setForeground(erro ? tema.paleta().erro() : tema.paleta().textoSecundario());
    }

    /** Mostra em que sala a busca é feita. */
    public void definirSala(String sala) {
        titulo.setText("Buscar em #" + sala);
    }

    /** As abas. */
    public JTabbedPane abas() {
        return abas;
    }

    /** Campo da aba Usuário. */
    public JTextField campoUsuario() {
        return campoUsuario;
    }

    /** Campo da aba Texto. */
    public JTextField campoTexto() {
        return campoTexto;
    }

    /** Campo de data inicial. */
    public JTextField campoInicio() {
        return campoInicio;
    }

    /** Campo de data final. */
    public JTextField campoFim() {
        return campoFim;
    }

    /** Botão Buscar de cada aba. */
    public JButton botaoDaAba(int indice) {
        return botoes[indice];
    }

    /** Tabela de resultados. */
    public JTable tabela() {
        return tabela;
    }

    /** Linha de status. */
    public JLabel status() {
        return status;
    }

    /** Título "Buscar em #sala". */
    public JLabel titulo() {
        return titulo;
    }

    /** Quantidade de linhas na tabela. */
    public int linhasDaTabela() {
        return modelo.getRowCount();
    }

    /** Valor de uma célula da tabela. */
    public Object valorNaTabela(int linha, int coluna) {
        return modelo.getValueAt(linha, coluna);
    }
}
