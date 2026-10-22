package br.unicesumar.chat.client.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Concentra TODAS as cores, fontes e espaçamentos da interface. Visual: uma cor de
 * destaque (azul) e tons neutros, em modo claro e escuro, com contraste mínimo de
 * 4,5:1 em todo par de texto e fundo (garantido por teste). Espaçamentos seguem a
 * grade de 8 px. Nenhum outro arquivo da interface define uma cor ou fonte.
 */
public final class Tema {

    /** Modo de cores. */
    public enum Modo { CLARO, ESCURO }

    /**
     * Conjunto de cores de um modo.
     *
     * @param fundo              fundo geral
     * @param superficie         fundo de painéis laterais e barras
     * @param borda              linhas divisórias
     * @param texto              texto principal
     * @param textoSecundario    horários, legendas e avisos do sistema
     * @param destaque           única cor de destaque (preenchimentos)
     * @param textoSobreDestaque texto sobre {@code destaque}
     * @param textoDestaque      a cor de destaque usada como cor de texto
     * @param balaoMeu           balão das minhas mensagens
     * @param textoBalaoMeu      texto do balão das minhas mensagens
     * @param balaoOutro         balão das mensagens dos outros
     * @param textoBalaoOutro    texto do balão dos outros
     * @param balaoPrivado       balão das mensagens privadas (cor própria)
     * @param textoBalaoPrivado  texto do balão das privadas
     * @param erro               erros e alerta do contador
     * @param sucesso            estado "conectado"
     * @param atencao            estado "conectando"
     */
    public record Paleta(Color fundo, Color superficie, Color borda, Color texto, Color textoSecundario,
            Color destaque, Color textoSobreDestaque, Color textoDestaque, Color balaoMeu,
            Color textoBalaoMeu, Color balaoOutro, Color textoBalaoOutro, Color balaoPrivado,
            Color textoBalaoPrivado, Color erro, Color sucesso, Color atencao) {
    }

    /** Unidade da grade de espaçamento, em pixels. */
    public static final int GRADE = 8;
    /** Tamanho da fonte do corpo (nunca abaixo de 13). */
    public static final int TAMANHO_CORPO = 14;
    /** Tamanho da fonte de remetente e horário. */
    public static final int TAMANHO_PEQUENO = 12;
    /** Tamanho da fonte de títulos. */
    public static final int TAMANHO_TITULO = 18;

    private static final Paleta CLARA = new Paleta(
            cor(0xFFFFFF), cor(0xF3F4F6), cor(0xD1D5DB), cor(0x111827), cor(0x4B5563),
            cor(0x1D4ED8), cor(0xFFFFFF), cor(0x1D4ED8), cor(0x1D4ED8),
            cor(0xFFFFFF), cor(0xE5E7EB), cor(0x111827), cor(0xFEF3C7),
            cor(0x111827), cor(0xB91C1C), cor(0x15803D), cor(0xB45309));

    private static final Paleta ESCURA = new Paleta(
            cor(0x0F172A), cor(0x1E293B), cor(0x334155), cor(0xF1F5F9), cor(0x94A3B8),
            cor(0x2563EB), cor(0xFFFFFF), cor(0x60A5FA), cor(0x2563EB),
            cor(0xFFFFFF), cor(0x334155), cor(0xF1F5F9), cor(0x78350F),
            cor(0xFEF3C7), cor(0xFCA5A5), cor(0x4ADE80), cor(0xFBBF24));

    private final List<Runnable> ouvintes = new CopyOnWriteArrayList<>();
    private volatile Modo modo;

    /** Cria o tema no modo inicial informado. */
    public Tema(Modo inicial) {
        this.modo = Objects.requireNonNull(inicial, "inicial");
    }

    private static Color cor(int rgb) {
        return new Color(rgb);
    }

    /** Paleta de um modo. */
    public static Paleta paleta(Modo modo) {
        return modo == Modo.CLARO ? CLARA : ESCURA;
    }

    /** Múltiplo da grade de 8 px. */
    public static int espaco(int multiplos) {
        if (multiplos < 0) {
            throw new IllegalArgumentException("O espaçamento não pode ser negativo.");
        }
        return multiplos * GRADE;
    }

    /** Razão de contraste WCAG entre duas cores (de 1 a 21). */
    public static double contraste(Color a, Color b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminancia(Color c) {
        return 0.2126 * canal(c.getRed()) + 0.7152 * canal(c.getGreen()) + 0.0722 * canal(c.getBlue());
    }

    private static double canal(int valor) {
        double v = valor / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Modo atual. */
    public Modo modo() {
        return modo;
    }

    /** Paleta do modo atual. */
    public Paleta paleta() {
        return paleta(modo);
    }

    /** Troca entre claro e escuro e avisa quem se registrou com {@link #aoMudar}. */
    public void alternar() {
        modo = modo == Modo.CLARO ? Modo.ESCURO : Modo.CLARO;
        ouvintes.forEach(Runnable::run);
    }

    /** Registra quem precisa se redesenhar quando o modo muda. */
    public void aoMudar(Runnable ouvinte) {
        ouvintes.add(Objects.requireNonNull(ouvinte, "ouvinte"));
    }

    /** Fonte do corpo do texto. */
    public Font fonteCorpo() {
        return new Font(Font.SANS_SERIF, Font.PLAIN, TAMANHO_CORPO);
    }

    /** Fonte menor, de remetente e horário. */
    public Font fontePequena() {
        return new Font(Font.SANS_SERIF, Font.PLAIN, TAMANHO_PEQUENO);
    }

    /** Fonte de títulos. */
    public Font fonteTitulo() {
        return new Font(Font.SANS_SERIF, Font.BOLD, TAMANHO_TITULO);
    }

    /** Instala o FlatLaf claro ou escuro (conforme o modo) com a cor de destaque do tema. */
    public void instalarLookAndFeel() {
        Color destaque = paleta().destaque();
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor",
                String.format("#%02X%02X%02X", destaque.getRed(), destaque.getGreen(), destaque.getBlue())));
        if (modo == Modo.CLARO) {
            FlatLightLaf.setup();
        } else {
            FlatDarkLaf.setup();
        }
        FlatLaf.updateUI();
    }
}
