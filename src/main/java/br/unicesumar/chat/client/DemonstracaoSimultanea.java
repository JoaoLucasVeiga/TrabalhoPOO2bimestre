package br.unicesumar.chat.client;

import br.unicesumar.chat.client.controller.FabricaConexao;
import br.unicesumar.chat.client.net.ConexaoChat;
import br.unicesumar.chat.protocol.Evento;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demonstração prática de mensagens simultâneas: vários clientes reais entram no servidor, esperam
 * todos ficarem prontos e então enviam suas mensagens ao mesmo tempo. No fim, confere se cada cliente
 * recebeu todas as mensagens (sem perda e sem duplicata) e se todos viram <b>a mesma ordem</b>.
 *
 * <p>Usa só a interface {@link ConexaoChat}: não conhece banco nem regras do servidor. As mensagens de
 * teste levam um marcador único, então outras conversas na mesma sala não atrapalham a conferência.
 */
public final class DemonstracaoSimultanea {

    private static final int ESPERA_POR_RESPOSTA_MS = 15_000;
    private static final int ESPERA_PELAS_MENSAGENS_S = 60;
    private static final int LINHAS_DE_AMOSTRA = 12;

    /**
     * Resultado da demonstração.
     *
     * @param ok                  tudo chegou e a ordem foi igual para todos
     * @param esperadas           quantas mensagens cada cliente deveria receber
     * @param recebidasPorCliente quantas mensagens de teste cada cliente recebeu
     * @param mesmaOrdem          se todos os clientes viram exatamente a mesma sequência
     * @param duracaoMs           tempo entre a largada e a última mensagem recebida
     */
    public record Resultado(boolean ok, int esperadas, List<Integer> recebidasPorCliente, boolean mesmaOrdem,
            long duracaoMs) {
    }

    private DemonstracaoSimultanea() {
    }

    /**
     * Roda a demonstração.
     *
     * @param clientes  de 2 em diante
     * @param mensagens quantas mensagens cada cliente envia (1 em diante)
     * @param sala      sala em que a demonstração acontece
     * @param senha     senha das contas {@code demo1..demoN} (criadas na primeira vez)
     * @throws IllegalArgumentException se os números forem inválidos
     * @throws IllegalStateException    se o servidor não puder ser alcançado ou recusar a entrada
     */
    public static Resultado executar(FabricaConexao fabrica, String host, int porta, int clientes, int mensagens,
            String sala, String senha, PrintStream saida) throws InterruptedException {
        if (clientes < 2) {
            throw new IllegalArgumentException("Use pelo menos 2 clientes para ver mensagens simultâneas.");
        }
        if (mensagens < 1) {
            throw new IllegalArgumentException("Cada cliente precisa enviar pelo menos 1 mensagem.");
        }
        String marcador = "[sim-" + UUID.randomUUID().toString().substring(0, 4) + "] ";
        int esperadas = clientes * mensagens;
        List<ConexaoChat> conexoes = new ArrayList<>();
        List<List<String>> recebidas = new ArrayList<>();
        CountDownLatch prontos = new CountDownLatch(clientes);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch terminaram = new CountDownLatch(clientes);
        List<Throwable> falhas = Collections.synchronizedList(new ArrayList<>());
        saida.println("Conectando " + clientes + " clientes em " + host + ":" + porta + " (sala " + sala + ")...");
        try {
            for (int i = 1; i <= clientes; i++) {
                ConexaoChat conexao = fabrica.criar(host, porta);
                conexoes.add(conexao);
                List<String> lista = Collections.synchronizedList(new ArrayList<>());
                recebidas.add(lista);
                final String nome = "demo" + i;
                Thread t = new Thread(() -> cliente(conexao, nome, senha, sala, marcador, mensagens, esperadas, lista,
                        prontos, largada, terminaram, falhas), "demo-" + nome);
                t.setDaemon(true);
                t.start();
            }
            if (!prontos.await(ESPERA_POR_RESPOSTA_MS * 2L, TimeUnit.MILLISECONDS) || !falhas.isEmpty()) {
                throw new IllegalStateException(falhas.isEmpty()
                        ? "Os clientes não conseguiram entrar a tempo. Verifique se o servidor está em execução."
                        : falhas.get(0).getMessage());
            }
            saida.println("Todos conectados. Enviando " + esperadas + " mensagens ao mesmo tempo...");
            long inicio = System.nanoTime();
            largada.countDown();
            boolean completo = terminaram.await(ESPERA_PELAS_MENSAGENS_S, TimeUnit.SECONDS);
            long duracao = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            return conferir(saida, marcador, esperadas, recebidas, completo, duracao);
        } finally {
            conexoes.forEach(ConexaoChat::fechar);
        }
    }

    private static Resultado conferir(PrintStream saida, String marcador, int esperadas, List<List<String>> recebidas,
            boolean completo, long duracao) {
        List<Integer> contagens = new ArrayList<>();
        for (List<String> lista : recebidas) {
            contagens.add(lista.size());
        }
        List<String> primeira = new ArrayList<>(recebidas.get(0));
        boolean mesmaOrdem = recebidas.stream().allMatch(l -> new ArrayList<>(l).equals(primeira));
        boolean semPerda = contagens.stream().allMatch(n -> n == esperadas);
        boolean semDuplicata = recebidas.stream().allMatch(l -> new ArrayList<>(l).stream().distinct().count() == l.size());
        saida.println();
        for (int i = 0; i < contagens.size(); i++) {
            saida.println("  demo" + (i + 1) + " recebeu " + contagens.get(i) + " de " + esperadas + " mensagens");
        }
        saida.println();
        saida.println("Sem perda de mensagens:      " + (semPerda && completo ? "SIM" : "NÃO"));
        saida.println("Sem mensagens duplicadas:    " + (semDuplicata ? "SIM" : "NÃO"));
        saida.println("Mesma ordem para todos:      " + (mesmaOrdem ? "SIM" : "NÃO"));
        saida.println("Tempo total:                 " + duracao + " ms");
        saida.println();
        saida.println("Primeiras mensagens, na ordem em que TODOS os clientes receberam:");
        for (int i = 0; i < Math.min(LINHAS_DE_AMOSTRA, primeira.size()); i++) {
            saida.println("  " + (i + 1) + ". " + primeira.get(i).substring(marcador.length()));
        }
        boolean ok = semPerda && completo && semDuplicata && mesmaOrdem;
        saida.println();
        saida.println(ok ? "RESULTADO: OK" : "RESULTADO: FALHOU");
        return new Resultado(ok, esperadas, contagens, mesmaOrdem, duracao);
    }

    private static void cliente(ConexaoChat conexao, String nome, String senha, String sala, String marcador,
            int mensagens, int esperadas, List<String> lista, CountDownLatch prontos, CountDownLatch largada,
            CountDownLatch terminaram, List<Throwable> falhas) {
        boolean contouPronto = false;
        try {
            conexao.definirTempoDeEspera(ESPERA_POR_RESPOSTA_MS);
            conexao.abrir();
            entrar(conexao, nome, senha);
            if (!"geral".equalsIgnoreCase(sala)) {
                conexao.enviar("/join " + sala);
                esperarOk(conexao);
            }
            Thread leitor = new Thread(() -> ler(conexao, sala, marcador, esperadas, lista, terminaram),
                    "leitor-" + nome);
            leitor.setDaemon(true);
            conexao.definirTempoDeEspera(0);
            leitor.start();
            contouPronto = true;
            prontos.countDown();
            largada.await();
            for (int k = 1; k <= mensagens; k++) {
                conexao.enviar(marcador + nome + ": mensagem " + k + " de " + mensagens);
            }
        } catch (IOException e) {
            falhas.add(new IllegalStateException(e.getMessage(), e));
        } catch (IllegalStateException e) {
            falhas.add(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!contouPronto) {
                prontos.countDown();
                terminaram.countDown();
            }
        }
    }

    /** Entra com conta (cria na primeira vez) ou, se o servidor não exige login, só com o nome. */
    private static void entrar(ConexaoChat conexao, String nome, String senha) throws IOException {
        Evento boasVindas = conexao.receber();
        boolean comLogin = boasVindas instanceof Evento.Sistema s && s.aviso().contains("/register");
        if (!comLogin) {
            conexao.enviar(nome);
            esperarOk(conexao);
            return;
        }
        conexao.enviar("/register " + nome + " " + senha);
        if (!respondeuOk(conexao)) {
            conexao.enviar("/login " + nome + " " + senha);
            if (!respondeuOk(conexao)) {
                throw new IllegalStateException("Não foi possível entrar como " + nome
                        + ". Se a conta já existe com outra senha, apague o arquivo demonstracao.senha "
                        + "e use outro servidor, ou peça ao administrador para remover a conta.");
            }
        }
    }

    private static boolean respondeuOk(ConexaoChat conexao) throws IOException {
        for (int i = 0; i < 100; i++) {
            Evento e = conexao.receber();
            if (e == null) {
                throw new IllegalStateException("O servidor encerrou a conexão. Verifique se ele está em execução.");
            }
            if (e instanceof Evento.Ok) {
                return true;
            }
            if (e instanceof Evento.Erro) {
                return false;
            }
        }
        return false;
    }

    private static void esperarOk(ConexaoChat conexao) throws IOException {
        if (!respondeuOk(conexao)) {
            throw new IllegalStateException("O servidor recusou o pedido. Confira o nome da sala e tente de novo.");
        }
    }

    private static void ler(ConexaoChat conexao, String sala, String marcador, int esperadas, List<String> lista,
            CountDownLatch terminaram) {
        try {
            while (lista.size() < esperadas) {
                Evento e = conexao.receber();
                if (e == null) {
                    break;
                }
                if (e instanceof Evento.Msg m && m.texto().startsWith(marcador)) {
                    lista.add(m.texto());
                }
            }
        } catch (IOException e) {
            // conexão fechada no fim da demonstração
        } finally {
            terminaram.countDown();
        }
    }
}
