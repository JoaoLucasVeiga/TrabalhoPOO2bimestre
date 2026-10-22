package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.EstadoConexao;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/**
 * Indicador do estado da conexão: um círculo desenhado em Java (verde, âmbar ou vermelho)
 * e, sempre, o texto do estado. A cor nunca é o único sinal.
 */
public class IndicadorConexao extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int MARGEM = 4;
    private static final int DIAMETRO = 12;

    private final transient Tema tema;
    private volatile EstadoConexao estado = EstadoConexao.DESCONECTADO;

    /** Cria o indicador, inicialmente "Desconectado". */
    public IndicadorConexao(Tema tema) {
        this.tema = Objects.requireNonNull(tema, "tema");
        setFont(tema.fonteCorpo());
        atualizarNomeAcessivel();
        tema.aoMudar(this::repaint);
    }

    /** Muda o estado mostrado. */
    public void definir(EstadoConexao novo) {
        this.estado = Objects.requireNonNull(novo, "novo");
        atualizarNomeAcessivel();
        revalidate();
        repaint();
    }

    /** Estado atual. */
    public EstadoConexao estado() {
        return estado;
    }

    /** Texto do estado atual. */
    public String texto() {
        return textoDe(estado);
    }

    /** Cor do círculo para o estado atual. */
    public Color corDoCirculo() {
        Tema.Paleta p = tema.paleta();
        return switch (estado) {
            case CONECTADO -> p.sucesso();
            case CONECTANDO -> p.atencao();
            case DESCONECTADO -> p.erro();
        };
    }

    private static String textoDe(EstadoConexao e) {
        return switch (e) {
            case CONECTADO -> "Conectado";
            case CONECTANDO -> "Conectando...";
            case DESCONECTADO -> "Desconectado";
        };
    }

    /** Um componente simples não tem contexto de acessibilidade: aqui ele se declara um rótulo. */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                private static final long serialVersionUID = 1L;

                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.LABEL;
                }
            };
        }
        return accessibleContext;
    }

    private void atualizarNomeAcessivel() {
        getAccessibleContext().setAccessibleName("Estado da conexão: " + texto());
        setToolTipText("Estado da conexão com o servidor: " + texto());
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int maior = 0;
        for (EstadoConexao e : EstadoConexao.values()) {
            maior = Math.max(maior, fm.stringWidth(textoDe(e)));
        }
        int altura = Math.max(fm.getHeight(), DIAMETRO) + 2 * MARGEM;
        return new Dimension(MARGEM + DIAMETRO + Tema.espaco(1) + maior + MARGEM, altura);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int centro = getHeight() / 2;
            g2.setColor(corDoCirculo());
            g2.fillOval(MARGEM, centro - DIAMETRO / 2, DIAMETRO, DIAMETRO);
            g2.setColor(tema.paleta().texto());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto(), MARGEM + DIAMETRO + Tema.espaco(1), centro + (fm.getAscent() - fm.getDescent()) / 2);
        } finally {
            g2.dispose();
        }
    }
}
