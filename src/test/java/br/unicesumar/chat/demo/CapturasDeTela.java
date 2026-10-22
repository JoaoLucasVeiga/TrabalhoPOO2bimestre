package br.unicesumar.chat.demo;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.client.ui.DialogoBusca;
import br.unicesumar.chat.client.ui.JanelaChat;
import br.unicesumar.chat.client.ui.JanelaLogin;
import br.unicesumar.chat.client.ui.PainelBusca;
import br.unicesumar.chat.client.ui.Tema;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Gera as capturas de tela das janelas (para o README e o relatório) com dados de exemplo.
 * Precisa de uma tela: {@code xvfb-run -a java -cp ... br.unicesumar.chat.demo.CapturasDeTela docs/prints}.
 */
public final class CapturasDeTela {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 5);

    private CapturasDeTela() {
    }

    private static MensagemTela publica(String de, int minuto, String texto, boolean minha) {
        return new MensagemTela(Tipo.PUBLICA, de, null, T.plusMinutes(minuto), texto, minha);
    }

    private static List<MensagemTela> conversa() {
        return List.of(
                MensagemTela.sistema("bia entrou na sala geral.", T),
                publica("bia", 0, "Oi, pessoal! Alguém já terminou o trabalho de POO?", false),
                publica("caio", 1, "Quase. Estou só ajustando os testes de concorrência.", false),
                publica("ana", 2, "Eu terminei o servidor. O histórico já aparece para quem conecta depois!", true),
                new MensagemTela(Tipo.PRIVADA, "caio", "ana", T.plusMinutes(3), "Psiu, me manda o script do banco depois?", false),
                new MensagemTela(Tipo.PRIVADA, "ana", "caio", T.plusMinutes(4), "Mando sim, já te passo.", true),
                publica("bia", 5, "Show! Vou testar com três clientes ao mesmo tempo.\nDepois mando os prints.", false),
                MensagemTela.sistema("dani entrou na sala geral.", T.plusMinutes(6)));
    }

    private static void preencher(JanelaChat chat) {
        chat.definirUsuario("ana");
        chat.mostrarConexao(EstadoConexao.CONECTADO);
        chat.substituirHistorico(conversa(), "geral");
        chat.atualizarUsuarios("geral", List.of("ana", "bia", "caio", "dani"));
        chat.atualizarSalas(List.of("geral", "java", "projeto"), "geral");
    }

    private static void salvar(Component c, File arquivo) throws Exception {
        BufferedImage[] imagem = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            c.printAll(g);
            g.dispose();
            imagem[0] = img;
        });
        ImageIO.write(imagem[0], "png", arquivo);
        System.out.println("Gravado: " + arquivo + " (" + imagem[0].getWidth() + "x" + imagem[0].getHeight() + ")");
    }

    private static void esperarEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Gera todas as capturas na pasta indicada (padrão {@code docs/prints}). */
    public static void main(String[] args) throws Exception {
        File pasta = new File(args.length > 0 ? args[0] : "docs/prints");
        if (!pasta.isDirectory() && !pasta.mkdirs()) {
            throw new IOException("Não foi possível criar " + pasta);
        }
        Tema claro = new Tema(Tema.Modo.CLARO);
        SwingUtilities.invokeAndWait(claro::instalarLookAndFeel);

        // ---- login
        JanelaLogin[] login = new JanelaLogin[1];
        ChatController[] controlador = new ChatController[1];
        JanelaChat[] chat = new JanelaChat[1];
        SwingUtilities.invokeAndWait(() -> {
            chat[0] = new JanelaChat(claro, () -> { }, () -> { });
            controlador[0] = new ChatController(chat[0], (h, p) -> new ConexaoFalsa());
            chat[0].definirControlador(controlador[0]);
            login[0] = new JanelaLogin(claro, controlador[0], () -> { });
            login[0].painel().campoUsuario().setText("ana");
            login[0].setVisible(true);
        });
        esperarEdt();
        salvar(login[0].getContentPane(), new File(pasta, "01-login.png"));

        SwingUtilities.invokeAndWait(() -> {
            login[0].painel().mostrarErro("Não foi possível conectar a localhost:5000. "
                    + "Verifique se o servidor está em execução.");
            login[0].pack();
        });
        esperarEdt();
        salvar(login[0].getContentPane(), new File(pasta, "02-login-erro.png"));

        SwingUtilities.invokeAndWait(() -> {
            login[0].painel().limparErro();
            login[0].painel().campoSenha().setText("minhasenha");
            login[0].painel().definirConectando(true, false);
            login[0].pack();
        });
        esperarEdt();
        salvar(login[0].getContentPane(), new File(pasta, "03-login-conectando.png"));
        SwingUtilities.invokeAndWait(() -> login[0].dispose());

        // ---- chat claro
        SwingUtilities.invokeAndWait(() -> {
            preencher(chat[0]);
            chat[0].painel().entrada().campo().setText("Que ótimo! Vou rodar o servidor agora e já te aviso.");
            chat[0].painel().entrada().campo().select(0, 0); // sem o texto marcado na captura
            chat[0].setSize(new Dimension(1000, 680));
            chat[0].setVisible(true);
        });
        esperarEdt();
        salvar(chat[0].getContentPane(), new File(pasta, "04-chat-claro.png"));

        // ---- diálogo de busca
        DialogoBusca[] busca = new DialogoBusca[1];
        SwingUtilities.invokeAndWait(() -> {
            busca[0] = new DialogoBusca(chat[0], claro, new PainelBusca.Acoes() {
                @Override
                public void porUsuario(String nome) {
                }

                @Override
                public void porTexto(String trecho) {
                }

                @Override
                public void porPeriodo(String inicio, String fim) {
                }
            });
            busca[0].abrir("geral");
            busca[0].painel().abas().setSelectedIndex(1);
            busca[0].painel().campoTexto().setText("servidor");
            busca[0].painel().mostrarResultado(List.of(
                    publica("ana", 2, "Eu terminei o servidor. O histórico já aparece para quem conecta depois!", true),
                    publica("caio", 9, "O servidor aguenta vários clientes ao mesmo tempo.", false),
                    publica("bia", 12, "Rodei o servidor com três clientes e funcionou.", false)));
            busca[0].pack();
        });
        esperarEdt();
        salvar(busca[0].getContentPane(), new File(pasta, "05-busca.png"));
        SwingUtilities.invokeAndWait(() -> busca[0].dispose());

        // ---- chat escuro
        SwingUtilities.invokeAndWait(() -> {
            claro.alternar();
            claro.instalarLookAndFeel();
        });
        esperarEdt();
        salvar(chat[0].getContentPane(), new File(pasta, "06-chat-escuro.png"));

        // ---- chat desconectado
        SwingUtilities.invokeAndWait(() -> {
            claro.alternar();
            claro.instalarLookAndFeel();
            chat[0].mostrarConexao(EstadoConexao.DESCONECTADO);
            chat[0].mostrarErro("A conexão com o servidor foi encerrada. Entre novamente para continuar.");
        });
        esperarEdt();
        salvar(chat[0].getContentPane(), new File(pasta, "07-chat-desconectado.png"));
        SwingUtilities.invokeAndWait(() -> chat[0].dispose());
    }
}
