package br.unicesumar.chat.client.ui;

import java.util.Objects;
import javax.swing.JButton;

/** Aplica os estados padronizados aos botões, sem perder o texto original. */
public final class Botoes {

    /** Texto mostrado enquanto uma operação está em andamento. */
    public static final String TEXTO_CARREGANDO = "Carregando...";

    private static final String TEXTO_ORIGINAL = "chat.textoOriginal";

    private Botoes() {
    }

    /** Coloca o botão no estado pedido; o texto de antes de "Carregando..." volta no estado ativo. */
    public static void aplicar(JButton botao, EstadoBotao estado) {
        Objects.requireNonNull(botao, "botao");
        Objects.requireNonNull(estado, "estado");
        if (botao.getClientProperty(TEXTO_ORIGINAL) == null) {
            botao.putClientProperty(TEXTO_ORIGINAL, botao.getText());
        }
        String original = (String) botao.getClientProperty(TEXTO_ORIGINAL);
        switch (estado) {
            case ATIVO -> {
                botao.setText(original);
                botao.setEnabled(true);
            }
            case DESABILITADO -> {
                botao.setText(original);
                botao.setEnabled(false);
            }
            case CARREGANDO -> {
                botao.setText(TEXTO_CARREGANDO);
                botao.setEnabled(false);
            }
        }
    }
}
