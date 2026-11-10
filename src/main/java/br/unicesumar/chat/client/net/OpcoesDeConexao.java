package br.unicesumar.chat.client.net;

import br.unicesumar.chat.infra.CertificadoTls;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.net.SocketFactory;

/**
 * Argumentos de linha de comando dos clientes: os valores posicionais (servidor e porta) e a opção
 * {@code --tls[=certificado.crt]}, que liga o TLS confiando só no certificado indicado
 * (padrão {@code chat-tls.crt}, na pasta atual).
 */
public final class OpcoesDeConexao {

    private static final String OPCAO_TLS = "--tls";
    private static final Path CERTIFICADO_PADRAO = Path.of("chat-tls.crt");

    private final List<String> posicionais;
    private final Path certificado;
    private SocketFactory fabricaTls;

    private OpcoesDeConexao(List<String> posicionais, Path certificado) {
        this.posicionais = List.copyOf(posicionais);
        this.certificado = certificado;
    }

    /** Separa os argumentos em posicionais e opções. */
    public static OpcoesDeConexao de(String[] args) {
        List<String> posicionais = new ArrayList<>();
        Path certificado = null;
        for (String arg : args) {
            if (arg.equals(OPCAO_TLS)) {
                certificado = CERTIFICADO_PADRAO;
            } else if (arg.startsWith(OPCAO_TLS + "=")) {
                String caminho = arg.substring(OPCAO_TLS.length() + 1).strip();
                certificado = caminho.isEmpty() ? CERTIFICADO_PADRAO : Path.of(caminho);
            } else {
                posicionais.add(arg);
            }
        }
        return new OpcoesDeConexao(posicionais, certificado);
    }

    /** Valores sem opção, na ordem (servidor, porta). */
    public List<String> posicionais() {
        return posicionais;
    }

    /** Indica se o cliente deve usar TLS. */
    public boolean usaTls() {
        return certificado != null;
    }

    /** Certificado em que o cliente confia (só faz sentido com {@link #usaTls()}). */
    public Path certificado() {
        return certificado;
    }

    /**
     * Confere antes de conectar se o certificado pode ser lido.
     *
     * @throws IOException se o arquivo não existe ou não é um certificado
     */
    public synchronized void validar() throws IOException {
        if (usaTls() && fabricaTls == null) {
            fabricaTls = CertificadoTls.contextoDoCliente(certificado).getSocketFactory();
        }
    }

    /**
     * Cria a conexão (ainda fechada), com ou sem TLS.
     *
     * @throws IllegalStateException se o TLS foi pedido e o certificado não pôde ser lido
     */
    public synchronized ConexaoSocket conexao(String host, int porta) {
        try {
            validar();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return usaTls() ? new ConexaoSocket(host, porta, fabricaTls) : new ConexaoSocket(host, porta);
    }
}
