package br.unicesumar.chat.client.ui;

import java.awt.Font;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPopupMenu;

/** Janelinha com a grade de emojis: clicar em um entrega o emoji e fecha a janelinha. */
public class SeletorDeEmojis extends JPopupMenu {

    private static final long serialVersionUID = 1L;
    private static final int COLUNAS = 8;
    private static final int TAMANHO_DA_FONTE = 20;

    /**
     * Cria o seletor.
     *
     * @param aoEscolher chamado com o emoji clicado
     */
    public SeletorDeEmojis(Consumer<String> aoEscolher) {
        Objects.requireNonNull(aoEscolher, "aoEscolher");
        setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1), Tema.espaco(1)));
        setLayout(new GridLayout(0, COLUNAS, 2, 2));
        for (String emoji : Emojis.catalogo()) {
            JButton botao = new JButton(emoji);
            botao.setFont(new Font(Font.DIALOG, Font.PLAIN, TAMANHO_DA_FONTE));
            botao.setFocusable(false);
            botao.setBorderPainted(false);
            botao.setContentAreaFilled(false);
            botao.getAccessibleContext().setAccessibleName("Emoji " + emoji);
            botao.addActionListener(e -> {
                setVisible(false);
                aoEscolher.accept(emoji);
            });
            add(botao);
        }
    }
}
