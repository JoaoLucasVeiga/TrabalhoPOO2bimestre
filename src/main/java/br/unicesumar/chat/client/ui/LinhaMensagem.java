package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/**
 * Uma mensagem na conversa, em bloco: avatar redondo à esquerda e, ao lado, o nome, a hora e o texto.
 * Mensagens seguidas da mesma pessoa viram continuação (sem avatar nem nome, só o texto). Os avisos
 * do sistema ficam centralizados, em cinza.
 */
public class LinhaMensagem extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HORA_MINUTO = DateTimeFormatter.ofPattern("HH:mm");
    private static final int LARGURA_PADRAO = 480;
    private static final int TAMANHO_DO_AVATAR = 40;
    private static final long MINUTOS_PARA_AGRUPAR = 5;

    private final transient Tema tema;
    private final transient MensagemTela mensagem;
    private final boolean aviso;
    private final JLabel avatar = new JLabel();
    private final JLabel nome = new JLabel();
    private final JLabel hora = new JLabel();
    private final JTextArea corpo = new JTextArea();
    private final JPanel colunaDoAvatar = new JPanel(new BorderLayout());
    private final JPanel cabecalho = new JPanel();
    private boolean continuacao;
    private int larguraMaxima = LARGURA_PADRAO;
    private Dimension tamanhoEmCache;

    /** Cria a linha para a mensagem. */
    public LinhaMensagem(Tema tema, MensagemTela mensagem) {
        super(new BorderLayout());
        this.tema = Objects.requireNonNull(tema, "tema");
        this.mensagem = Objects.requireNonNull(mensagem, "mensagem");
        this.aviso = mensagem.tipo() == Tipo.SISTEMA;
        montar();
        getAccessibleContext().setAccessibleName(descricaoAcessivel());
        aplicarTema();
    }

    private void montar() {
        setOpaque(false);
        corpo.setText(mensagem.texto());
        corpo.setEditable(false);
        corpo.setFocusable(false);
        corpo.setOpaque(false);
        corpo.setLineWrap(true);
        corpo.setWrapStyleWord(true);
        corpo.setBorder(null);
        if (aviso) {
            corpo.setVisible(false);
            JLabel textoDoAviso = nome; // o aviso usa o rótulo do nome, centralizado
            textoDoAviso.setText(mensagem.texto());
            textoDoAviso.setHorizontalAlignment(SwingConstants.CENTER);
            add(textoDoAviso, BorderLayout.CENTER);
            atualizarBorda();
            return;
        }
        nome.setText(mensagem.remetente());
        hora.setText(mensagem.horario().format(HORA_MINUTO));
        cabecalho.setLayout(new BoxLayout(cabecalho, BoxLayout.X_AXIS));
        cabecalho.setOpaque(false);
        cabecalho.add(nome);
        cabecalho.add(Box.createHorizontalStrut(Tema.espaco(1)));
        cabecalho.add(hora);
        cabecalho.add(Box.createHorizontalGlue());

        colunaDoAvatar.setOpaque(false);
        colunaDoAvatar.add(avatar, BorderLayout.NORTH);
        colunaDoAvatar.setPreferredSize(new Dimension(TAMANHO_DO_AVATAR + Tema.espaco(2), 1));
        avatar.setVerticalAlignment(SwingConstants.TOP);

        JPanel conteudo = new JPanel(new BorderLayout());
        conteudo.setOpaque(false);
        conteudo.add(cabecalho, BorderLayout.NORTH);
        conteudo.add(corpo, BorderLayout.CENTER);
        add(colunaDoAvatar, BorderLayout.WEST);
        add(conteudo, BorderLayout.CENTER);
        atualizarBorda();
    }

    private String descricaoAcessivel() {
        String quem = aviso ? "Aviso" : mensagem.remetente();
        return quem + " às " + mensagem.horario().format(HORA_MINUTO) + ": " + mensagem.texto();
    }

    /**
     * Indica se a mensagem {@code atual} continua o bloco da {@code anterior}: mesma pessoa, mesmo tipo,
     * até 5 minutos depois e sem aviso do sistema no meio.
     */
    public static boolean agrupa(MensagemTela anterior, MensagemTela atual) {
        if (anterior == null || atual == null || anterior.tipo() == Tipo.SISTEMA || atual.tipo() == Tipo.SISTEMA
                || anterior.tipo() != atual.tipo() || !anterior.remetente().equalsIgnoreCase(atual.remetente())
                || atual.horario().isBefore(anterior.horario())) {
            return false;
        }
        return Duration.between(anterior.horario(), atual.horario()).toMinutes() <= MINUTOS_PARA_AGRUPAR;
    }

    /** A mensagem mostrada. */
    public MensagemTela mensagem() {
        return mensagem;
    }

    /** Define se esta linha continua o bloco da anterior (sem avatar nem nome). */
    public void definirContinuacao(boolean continua) {
        boolean nova = continua && !aviso;
        if (nova == continuacao) {
            return;
        }
        continuacao = nova;
        atualizarBorda();
        tamanhoEmCache = null;
        invalidate();
    }

    /** Indica se esta linha é continuação do bloco anterior. */
    public boolean continuacao() {
        return continuacao;
    }

    /** Indica se a linha mostra o avatar (primeira do bloco e não é aviso do sistema). */
    public boolean temAvatar() {
        return !aviso && !continuacao;
    }

    /** Texto do cabeçalho ({@code nome · hora}); vazio nos avisos do sistema. */
    public String cabecalho() {
        return aviso ? "" : mensagem.remetente() + " · " + mensagem.horario().format(HORA_MINUTO);
    }

    /** Cor do nome: destaque nas minhas mensagens, texto normal nas dos outros. */
    public Color corDoNome() {
        return mensagem.minha() ? tema.paleta().textoDestaque() : tema.paleta().texto();
    }

    /** Cor do texto da mensagem. */
    public Color corDoTexto() {
        return aviso ? tema.paleta().textoSecundario() : tema.paleta().texto();
    }

    /** Fonte do texto da mensagem. */
    public Font fonteDoTexto() {
        return corpo.getFont();
    }

    /** Fonte menor do cabeçalho (a hora). */
    public Font fonteDoCabecalho() {
        return hora.getFont();
    }

    /** Margem interna da área de texto (zero: o nome e o texto ficam alinhados). */
    public Insets margemDoTexto() {
        return corpo.getMargin();
    }

    private void atualizarBorda() {
        if (aviso) {
            int respiro = Tema.espaco(1) + 4;
            setBorder(BorderFactory.createEmptyBorder(respiro, Tema.espaco(2), respiro, Tema.espaco(2)));
            return;
        }
        int topo = continuacao ? 1 : Tema.espaco(2);
        setBorder(BorderFactory.createEmptyBorder(topo, Tema.espaco(2), 1, Tema.espaco(2)));
        avatar.setVisible(!continuacao);
        cabecalho.setVisible(!continuacao);
    }

    /**
     * Reaplica cores, fontes, margem e avatar. Trocar o visual do FlatLaf reinstala as margens padrão
     * dos componentes, por isso isto precisa rodar a cada troca de tema.
     */
    public void aplicarTema() {
        corpo.setFont(tema.fonteCorpo());
        corpo.setMargin(new Insets(0, 0, 0, 0));
        corpo.setBorder(null);
        corpo.setForeground(corDoTexto());
        if (aviso) {
            nome.setFont(tema.fontePequena());
            nome.setForeground(corDoTexto());
        } else {
            nome.setFont(tema.fonteCorpo().deriveFont(Font.BOLD));
            nome.setForeground(corDoNome());
            hora.setFont(tema.fontePequena());
            hora.setForeground(tema.paleta().textoSecundario());
            avatar.setIcon(new AvatarIcon(mensagem.remetente(), TAMANHO_DO_AVATAR, false, tema));
        }
        tamanhoEmCache = null;
        invalidate();
        repaint();
    }

    /** Define a largura da linha; textos mais longos quebram em mais linhas. */
    public void definirLarguraMaxima(int pixels) {
        if (pixels < 1) {
            throw new IllegalArgumentException("A largura máxima deve ser positiva.");
        }
        if (pixels != larguraMaxima) {
            larguraMaxima = pixels;
            tamanhoEmCache = null;
            invalidate();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (tamanhoEmCache == null) {
            Insets in = getInsets();
            int altura;
            if (aviso) {
                altura = nome.getPreferredSize().height;
            } else {
                int largura = Math.max(40, larguraMaxima - in.left - in.right - TAMANHO_DO_AVATAR - Tema.espaco(2));
                corpo.setSize(largura, 1); // com quebra de linha, a altura preferida depende da largura
                altura = corpo.getPreferredSize().height + (continuacao ? 0 : cabecalho.getPreferredSize().height);
                if (!continuacao) {
                    altura = Math.max(altura, TAMANHO_DO_AVATAR);
                }
            }
            tamanhoEmCache = new Dimension(larguraMaxima, altura + in.top + in.bottom);
        }
        return new Dimension(tamanhoEmCache);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(1, getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
