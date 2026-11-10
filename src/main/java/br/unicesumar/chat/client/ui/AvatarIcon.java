package br.unicesumar.chat.client.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;
import java.util.Objects;
import javax.swing.Icon;

/**
 * Avatar redondo: um círculo com a cor do nome e a inicial em branco. Com {@code online}, ganha uma
 * bolinha verde no canto inferior direito. Serve de ícone em listas e nos blocos de mensagem.
 */
public final class AvatarIcon implements Icon {

    private static final double FRACAO_DA_BOLINHA = 0.32;
    private static final double FRACAO_DA_FONTE = 0.45;

    private final String nome;
    private final int tamanho;
    private final boolean online;
    private final Tema tema;

    /**
     * Cria o avatar.
     *
     * @param nome    nome de quem é o avatar (define a cor e a inicial)
     * @param tamanho diâmetro em pixels
     * @param online  se mostra a bolinha de online
     * @throws IllegalArgumentException se o tamanho não for positivo
     */
    public AvatarIcon(String nome, int tamanho, boolean online, Tema tema) {
        if (tamanho < 1) {
            throw new IllegalArgumentException("O tamanho do avatar deve ser positivo.");
        }
        this.nome = nome;
        this.tamanho = tamanho;
        this.online = online;
        this.tema = Objects.requireNonNull(tema, "tema");
    }

    /** Primeira letra do nome em maiúscula, ou {@code ?} se não houver nome. */
    public static String inicial(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        return nome.strip().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Tema.corDoAvatar(nome));
            g2.fillOval(x, y, tamanho, tamanho);
            g2.setColor(Color.WHITE);
            g2.setFont(tema.fonteCorpo().deriveFont(Font.BOLD, (float) (tamanho * FRACAO_DA_FONTE)));
            FontMetrics fm = g2.getFontMetrics();
            String letra = inicial(nome);
            g2.drawString(letra, x + (tamanho - fm.stringWidth(letra)) / 2,
                    y + (tamanho - fm.getHeight()) / 2 + fm.getAscent());
            if (online) {
                int d = (int) Math.round(tamanho * FRACAO_DA_BOLINHA);
                int bx = x + tamanho - d;
                int by = y + tamanho - d;
                g2.setColor(tema.paleta().superficie());
                g2.fillOval(bx - 2, by - 2, d + 4, d + 4);
                g2.setColor(tema.paleta().sucesso());
                g2.fillOval(bx, by, d, d);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return tamanho;
    }

    @Override
    public int getIconHeight() {
        return tamanho;
    }
}
