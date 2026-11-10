package br.unicesumar.chat;

import br.unicesumar.chat.client.DemonstracaoSimultanea;
import br.unicesumar.chat.client.net.OpcoesDeConexao;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Roda a demonstração de mensagens simultâneas contra um servidor em execução. Uso:
 * {@code DemonstracaoSimultaneaMain [servidor] [porta] [clientes] [mensagens] [sala] [--tls[=certificado.crt]]}
 * (padrão: localhost 5000, 5 clientes, 10 mensagens cada, sala geral). As contas {@code demo1..demoN} são
 * criadas na primeira vez, com uma senha aleatória guardada em {@code demonstracao.senha} (no {@code .gitignore}).
 */
public final class DemonstracaoSimultaneaMain {

    private static final Path ARQUIVO_DA_SENHA = Path.of("demonstracao.senha");

    private DemonstracaoSimultaneaMain() {
    }

    /** Lê os argumentos, roda a demonstração e termina com código 0 (ok) ou 1 (falhou). */
    public static void main(String[] args) throws InterruptedException {
        PrintStream tela = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        OpcoesDeConexao opcoes = OpcoesDeConexao.de(args);
        List<String> p = opcoes.posicionais();
        try {
            opcoes.validar();
            String host = p.size() > 0 ? p.get(0) : "localhost";
            int porta = p.size() > 1 ? Integer.parseInt(p.get(1)) : 5000;
            int clientes = p.size() > 2 ? Integer.parseInt(p.get(2)) : 5;
            int mensagens = p.size() > 3 ? Integer.parseInt(p.get(3)) : 10;
            String sala = p.size() > 4 ? p.get(4) : "geral";
            DemonstracaoSimultanea.Resultado r = DemonstracaoSimultanea.executar(opcoes::conexao, host, porta,
                    clientes, mensagens, sala, senha(), tela);
            System.exit(r.ok() ? 0 : 1);
        } catch (NumberFormatException e) {
            tela.println("[erro] Porta, clientes e mensagens precisam ser números. Exemplo: localhost 5000 5 10");
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            tela.println("[erro] " + e.getMessage());
            System.exit(1);
        }
    }

    /** Senha das contas de demonstração: aleatória, criada na primeira execução. */
    private static String senha() throws IOException {
        if (Files.isRegularFile(ARQUIVO_DA_SENHA)) {
            String lida = Files.readString(ARQUIVO_DA_SENHA, StandardCharsets.UTF_8).strip();
            if (lida.length() >= 6) {
                return lida;
            }
        }
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        String nova = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(ARQUIVO_DA_SENHA, nova + "\n", StandardCharsets.UTF_8);
        return nova;
    }
}
