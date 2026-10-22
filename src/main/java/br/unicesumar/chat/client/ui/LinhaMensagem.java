package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * Uma mensagem na conversa: um balão arredondado com remetente e horário em fonte menor.
 * As minhas ficam à direita, as dos outros à esquerda e os avisos do sistema ao centro,
 * em cinza e sem balão. Mensagens privadas têm o rótulo "privada" e cor própria.
 */
public class LinhaMensagem extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HORA_MINUTO = DateTimeFormatter.ofPattern("HH:mm");
    private static final int RAIO = 16;
    private static final int LARGURA_PADRAO = 480;
    /** Mede a largura do texto sem quebra de linha (só é usado na EDT, sob lock por segurança). */
    private static final JTextArea MEDIDOR = new JTextArea();

    /** Lado da conversa em que a linha aparece. */
    public enum Alinhamento { ESQUERDA, DIREITA, CENTRO }

    private final transient Tema tema;
    private final transient MensagemTela mensagem;
    private final Balao balao;
    private final Alinhamento alinhamento;
    private int larguraMaxima = LARGURA_PADRAO;

    /** Cria a linha para a mensagem. */
    public LinhaMensagem(Tema tema, MensagemTela mensagem) {
        super(new GridBagLayout());
        this.tema = Objects.requireNonNull(tema, "tema");
        this.mensagem = Objects.requireNonNull(mensagem, "mensagem");
        this.alinhamento = mensagem.tipo() == Tipo.SISTEMA ? Alinhamento.CENTRO
                : mensagem.minha() ? Alinhamento.DIREITA : Alinhamento.ESQUERDA;
        this.balao = new Balao();
        setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.insets = new Insets(Tema.espaco(1) / 2, Tema.espaco(1), Tema.espaco(1) / 2, Tema.espaco(1));
        c.anchor = switch (alinhamento) {
            case ESQUERDA -> GridBagConstraints.WEST;
            case DIREITA -> GridBagConstraints.EAST;
            case CENTRO -> GridBagConstraints.CENTER;
        };
        add(balao, c);
        getAccessibleContext().setAccessibleName(descricaoAcessivel());
        aplicarTema();
    }

    private String descricaoAcessivel() {
        String quem = mensagem.tipo() == Tipo.SISTEMA ? "Aviso" : mensagem.minha() ? "Você" : mensagem.remetente();
        String privada = mensagem.tipo() == Tipo.PRIVADA ? " (mensagem privada)" : "";
        return quem + privada + " às " + mensagem.horario().format(HORA_MINUTO) + ": " + mensagem.texto();
    }

    /** A mensagem mostrada. */
    public MensagemTela mensagem() {
        return mensagem;
    }

    /** Lado em que a linha aparece. */
    public Alinhamento alinhamento() {
        return alinhamento;
    }

    /** Texto do cabeçalho (remetente, "privada" e horário); vazio nos avisos do sistema. */
    public String cabecalho() {
        String hora = mensagem.horario().format(HORA_MINUTO);
        return switch (mensagem.tipo()) {
            case SISTEMA -> "";
            case PUBLICA -> (mensagem.minha() ? "você" : mensagem.remetente()) + " · " + hora;
            case PRIVADA -> mensagem.minha()
                    ? "você → " + mensagem.destinatario() + " · privada · " + hora
                    : mensagem.remetente() + " · privada · " + hora;
        };
    }

    /** Cor de fundo do balão; {@code null} nos avisos do sistema, que não têm balão. */
    public Color corDoBalao() {
        Tema.Paleta p = tema.paleta();
        return switch (mensagem.tipo()) {
            case SISTEMA -> null;
            case PRIVADA -> p.balaoPrivado();
            case PUBLICA -> mensagem.minha() ? p.balaoMeu() : p.balaoOutro();
        };
    }

    /** Cor do texto do balão. */
    public Color corDoTexto() {
        Tema.Paleta p = tema.paleta();
        return switch (mensagem.tipo()) {
            case SISTEMA -> p.textoSecundario();
            case PRIVADA -> p.textoBalaoPrivado();
            case PUBLICA -> mensagem.minha() ? p.textoBalaoMeu() : p.textoBalaoOutro();
        };
    }

    /** Fonte do texto da mensagem. */
    public Font fonteDoTexto() {
        return balao.corpo.getFont();
    }

    /** Fonte menor do cabeçalho. */
    public Font fonteDoCabecalho() {
        return balao.cabecalho.getFont();
    }

    /** Margem interna da área de texto (zero: o cabeçalho e o texto ficam alinhados). */
    public Insets margemDoTexto() {
        return balao.corpo.getMargin();
    }

    /**
     * Reaplica cores, fontes, margem e borda. Trocar o visual do FlatLaf reinstala as margens padrão
     * dos componentes, por isso isto precisa rodar a cada troca de tema.
     */
    public void aplicarTema() {
        balao.corpo.setFont(tema.fonteCorpo());
        balao.cabecalho.setFont(tema.fontePequena());
        balao.corpo.setMargin(new Insets(0, 0, 0, 0));
        balao.corpo.setBorder(null);
        balao.corpo.setForeground(corDoTexto());
        balao.cabecalho.setForeground(corDoTexto());
        balao.limparCache();
        balao.invalidate();
        invalidate();
        balao.repaint();
    }

    /** Define a largura máxima do balão; textos mais longos quebram em mais linhas. */
    public void definirLarguraMaxima(int pixels) {
        if (pixels < 1) {
            throw new IllegalArgumentException("A largura máxima deve ser positiva.");
        }
        if (pixels != larguraMaxima) {
            larguraMaxima = pixels;
            balao.limparCache();
            balao.invalidate();
            invalidate();
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** O balão arredondado, com o cabeçalho e o texto (que quebra linha). */
    private final class Balao extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JLabel cabecalho = new JLabel();
        private final JTextArea corpo = new JTextArea();
        private Dimension tamanhoEmCache;

        Balao() {
            super(new BorderLayout());
            setOpaque(false);
            boolean comBalao = mensagem.tipo() != Tipo.SISTEMA;
            setBorder(comBalao
                    ? BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(2), Tema.espaco(1), Tema.espaco(2))
                    : BorderFactory.createEmptyBorder(0, Tema.espaco(1), 0, Tema.espaco(1)));
            cabecalho.setText(cabecalho());
            cabecalho.setVisible(comBalao);
            corpo.setText(mensagem.texto());
            corpo.setEditable(false);
            corpo.setFocusable(false);
            corpo.setOpaque(false);
            corpo.setLineWrap(true);
            corpo.setWrapStyleWord(true);
            corpo.setBorder(null);
            add(cabecalho, BorderLayout.NORTH);
            add(corpo, BorderLayout.CENTER);
        }

        void limparCache() {
            tamanhoEmCache = null;
        }

        /** Largura do texto sem quebra, medida pelo mesmo motor de texto que o desenha. */
        private int larguraNatural() {
            synchronized (MEDIDOR) {
                MEDIDOR.setLineWrap(false);
                MEDIDOR.setFont(corpo.getFont());
                MEDIDOR.setMargin(new Insets(0, 0, 0, 0));
                MEDIDOR.setBorder(null);
                MEDIDOR.setText(mensagem.texto());
                int texto = MEDIDOR.getPreferredSize().width;
                int titulo = cabecalho.isVisible() ? cabecalho.getPreferredSize().width : 0;
                return Math.max(Math.max(texto, titulo), 1);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (tamanhoEmCache == null) {
                Insets in = getInsets();
                int maxInterna = Math.max(40, larguraMaxima - in.left - in.right);
                int largura = Math.min(larguraNatural(), maxInterna);
                corpo.setSize(largura, 1); // com quebra de linha, a altura preferida depende da largura
                int altura = corpo.getPreferredSize().height
                        + (cabecalho.isVisible() ? cabecalho.getPreferredSize().height : 0);
                tamanhoEmCache = new Dimension(largura + in.left + in.right, altura + in.top + in.bottom);
            }
            return new Dimension(tamanhoEmCache);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color cor = corDoBalao();
            if (cor != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(cor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), RAIO, RAIO);
                } finally {
                    g2.dispose();
                }
            }
            super.paintComponent(g);
        }
    }
}
