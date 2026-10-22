package br.unicesumar.chat.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;

/** Força o layout de um componente sem tela (os eventos de redimensionamento não existem sem janela). */
final class LayoutDeTeste {

    private LayoutDeTeste() {
    }

    /** Dá o tamanho e arruma tudo, duas vezes (a primeira pode mudar larguras e alturas). */
    static void forcar(Component c, int largura, int altura) {
        c.setSize(new Dimension(largura, altura));
        for (int passada = 0; passada < 3; passada++) {
            arrumar(c);
        }
    }

    private static void arrumar(Component c) {
        if (c instanceof Container container) {
            container.doLayout();
            for (Component filho : container.getComponents()) {
                arrumar(filho);
            }
        }
    }

    /** Desenha o componente (já com tamanho) em uma imagem. */
    static BufferedImage desenhar(Component c) {
        BufferedImage img = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()),
                BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        c.printAll(g);
        g.dispose();
        return img;
    }
}
