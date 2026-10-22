package br.unicesumar.chat.client.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Tela de conexão e login: servidor, porta, usuário, senha e os botões "Entrar" e "Criar conta".
 * Enter confirma. Durante a conexão os campos e botões ficam desligados e o texto mostra
 * "Conectando...". Os erros aparecem em uma linha, dizendo o que houve e o que fazer.
 */
public class PainelLogin extends JPanel {

    private static final long serialVersionUID = 1L;

    /** O que o painel pede quando o usuário confirma. */
    @FunctionalInterface
    public interface Acoes {
        /** Entrar ({@code criarConta=false}) ou criar conta e entrar ({@code true}). */
        void entrar(String host, String porta, String usuario, String senha, boolean criarConta);
    }

    private final transient Tema tema;
    private final transient Acoes acoes;
    private final JLabel titulo = new JLabel("Chat Multiusuário");
    private final JTextField campoServidor = new JTextField("localhost", 16);
    private final JTextField campoPorta = new JTextField("5000", 5);
    private final JTextField campoUsuario = new JTextField(16);
    private final JPasswordField campoSenha = new JPasswordField(16);
    private final JTextArea rotuloDeErro = new JTextArea();
    private final JLabel rotuloDeStatus = new JLabel("");
    private final JButton botaoEntrar = new JButton("Entrar");
    private final JButton botaoCriarConta = new JButton("Criar conta");
    private final List<JLabel> rotulos = new java.util.ArrayList<>();
    private boolean conectando;

    /** Cria o painel. */
    public PainelLogin(Tema tema, Acoes acoes) {
        super(new GridBagLayout());
        this.tema = Objects.requireNonNull(tema, "tema");
        this.acoes = Objects.requireNonNull(acoes, "acoes");
        montar();
        aplicarTema();
        tema.aoMudar(this::aplicarTema);
    }

    private void montar() {
        setBorder(BorderFactory.createEmptyBorder(Tema.espaco(3), Tema.espaco(3), Tema.espaco(3), Tema.espaco(3)));
        campoServidor.setToolTipText("Endereço do servidor do chat, por exemplo localhost");
        campoPorta.setToolTipText("Porta do servidor, de 1 a 65535 (padrão 5000)");
        campoUsuario.setToolTipText("Seu nome de usuário (de 3 a 20 letras sem acento, números ou _)");
        campoSenha.setToolTipText("Sua senha (de 6 a 128 caracteres)");
        botaoEntrar.setToolTipText("Entrar com a conta existente (Enter)");
        botaoCriarConta.setToolTipText("Criar uma conta nova com este usuário e senha e entrar");
        campoServidor.getAccessibleContext().setAccessibleName("Servidor");
        campoPorta.getAccessibleContext().setAccessibleName("Porta");
        campoUsuario.getAccessibleContext().setAccessibleName("Usuário");
        campoSenha.getAccessibleContext().setAccessibleName("Senha");
        botaoEntrar.getAccessibleContext().setAccessibleName("Entrar");
        botaoCriarConta.getAccessibleContext().setAccessibleName("Criar conta");

        rotuloDeErro.setEditable(false);
        rotuloDeErro.setFocusable(false);
        rotuloDeErro.setOpaque(false);
        rotuloDeErro.setLineWrap(true);
        rotuloDeErro.setWrapStyleWord(true);
        rotuloDeErro.setBorder(null);
        rotuloDeErro.setColumns(30);
        rotuloDeErro.setVisible(false);
        rotuloDeErro.getAccessibleContext().setAccessibleName("Mensagem de erro");

        for (JTextField campo : List.of(campoServidor, campoPorta, campoUsuario, campoSenha)) {
            campo.addActionListener(e -> solicitar(false));
        }
        botaoEntrar.addActionListener(e -> solicitar(false));
        botaoCriarConta.addActionListener(e -> solicitar(true));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1));
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        c.insets = new Insets(0, Tema.espaco(1), Tema.espaco(2), Tema.espaco(1));
        add(titulo, c);

        c.gridwidth = 1;
        c.insets = new Insets(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1));
        linha(c, 1, "Servidor", campoServidor, 1);
        c.gridx = 2;
        add(rotulo("Porta", campoPorta), c);
        c.gridx = 3;
        add(campoPorta, c);
        linha(c, 2, "Usuário", campoUsuario, 3);
        linha(c, 3, "Senha", campoSenha, 3);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 4;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(Tema.espaco(2), Tema.espaco(1), 0, Tema.espaco(1));
        add(rotuloDeErro, c);
        c.gridy = 5;
        c.insets = new Insets(Tema.espaco(1), Tema.espaco(1), 0, Tema.espaco(1));
        add(rotuloDeStatus, c);

        JPanel botoes = new JPanel(new GridBagLayout());
        botoes.setOpaque(false);
        GridBagConstraints b = new GridBagConstraints();
        b.insets = new Insets(0, Tema.espaco(1), 0, 0);
        b.gridx = 0;
        botoes.add(botaoCriarConta, b);
        b.gridx = 1;
        botoes.add(botaoEntrar, b);
        c.gridy = 6;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(Tema.espaco(2), Tema.espaco(1), 0, Tema.espaco(1));
        add(botoes, c);
    }

    private JLabel rotulo(String texto, JComponent campo) {
        JLabel l = new JLabel(texto);
        l.setLabelFor(campo);
        rotulos.add(l);
        return l;
    }

    private void linha(GridBagConstraints c, int y, String texto, JComponent campo, int largura) {
        c.gridy = y;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        add(rotulo(texto, campo), c);
        c.gridx = 1;
        c.gridwidth = largura;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(campo, c);
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
    }

    private void aplicarTema() {
        Tema.Paleta p = tema.paleta();
        setBackground(p.fundo());
        titulo.setFont(tema.fonteTitulo());
        titulo.setForeground(p.texto());
        for (JLabel l : rotulos) {
            l.setFont(tema.fonteCorpo());
            l.setForeground(p.texto());
        }
        for (JTextField campo : List.of(campoServidor, campoPorta, campoUsuario, campoSenha)) {
            campo.setFont(tema.fonteCorpo());
        }
        botaoEntrar.setFont(tema.fonteCorpo());
        botaoCriarConta.setFont(tema.fonteCorpo());
        rotuloDeErro.setFont(tema.fonteCorpo());
        rotuloDeErro.setForeground(p.erro());
        rotuloDeStatus.setFont(tema.fonteCorpo());
        rotuloDeStatus.setForeground(p.textoSecundario());
    }

    private void solicitar(boolean criarConta) {
        if (conectando) {
            return;
        }
        limparErro();
        definirConectando(true, criarConta);
        acoes.entrar(campoServidor.getText(), campoPorta.getText(), campoUsuario.getText(),
                new String(campoSenha.getPassword()), criarConta);
    }

    /**
     * Liga ou desliga o estado "Conectando...": campos e botões desligados, o botão clicado em
     * "Carregando...".
     *
     * @param criarConta qual botão foi clicado (só importa ao ligar)
     */
    public void definirConectando(boolean ligado, boolean criarConta) {
        conectando = ligado;
        for (JTextField campo : List.of(campoServidor, campoPorta, campoUsuario, campoSenha)) {
            campo.setEnabled(!ligado);
        }
        JButton clicado = criarConta ? botaoCriarConta : botaoEntrar;
        JButton outro = criarConta ? botaoEntrar : botaoCriarConta;
        Botoes.aplicar(clicado, ligado ? EstadoBotao.CARREGANDO : EstadoBotao.ATIVO);
        Botoes.aplicar(outro, ligado ? EstadoBotao.DESABILITADO : EstadoBotao.ATIVO);
        if (!ligado) {
            Botoes.aplicar(botaoEntrar, EstadoBotao.ATIVO);
            Botoes.aplicar(botaoCriarConta, EstadoBotao.ATIVO);
        }
        rotuloDeStatus.setText(ligado ? "Conectando..." : "");
    }

    /** Mostra o erro em linha (o que houve e o que fazer). */
    public void mostrarErro(String texto) {
        Objects.requireNonNull(texto, "texto");
        rotuloDeErro.setText(texto);
        rotuloDeErro.setVisible(true);
        revalidate();
    }

    /** Esconde o erro. */
    public void limparErro() {
        rotuloDeErro.setText("");
        rotuloDeErro.setVisible(false);
    }

    /** Componente que recebe o foco ao abrir a tela: o campo de usuário. */
    public Component componenteInicial() {
        return campoUsuario;
    }

    /** Ordem do Tab. */
    public List<Component> ordemDeFoco() {
        return List.of(campoServidor, campoPorta, campoUsuario, campoSenha, botaoCriarConta, botaoEntrar);
    }

    /** O rótulo ligado a um campo. */
    public JLabel rotuloDe(JComponent campo) {
        return rotulos.stream().filter(l -> l.getLabelFor() == campo).findFirst().orElseThrow();
    }

    /** Campo do servidor. */
    public JTextField campoServidor() {
        return campoServidor;
    }

    /** Campo da porta. */
    public JTextField campoPorta() {
        return campoPorta;
    }

    /** Campo do usuário. */
    public JTextField campoUsuario() {
        return campoUsuario;
    }

    /** Campo da senha. */
    public JPasswordField campoSenha() {
        return campoSenha;
    }

    /** Botão Entrar. */
    public JButton botaoEntrar() {
        return botaoEntrar;
    }

    /** Botão Criar conta. */
    public JButton botaoCriarConta() {
        return botaoCriarConta;
    }

    /** Área do erro em linha. */
    public JTextArea rotuloDeErro() {
        return rotuloDeErro;
    }

    /** Texto de status ("Conectando..."). */
    public JLabel rotuloDeStatus() {
        return rotuloDeStatus;
    }
}
