package br.unicesumar.chat.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * TLS opcional com certificado autoassinado. O servidor guarda a chave em um {@code .p12} (criado com o
 * {@code keytool} do JDK na primeira vez) e exporta só o certificado público em um {@code .crt}. O cliente
 * confia <b>apenas</b> nesse certificado (fixação do certificado), então não precisa de autoridade
 * certificadora. A senha do {@code .p12} vem da configuração: nunca fica no código.
 */
public final class CertificadoTls {

    private static final String ALIAS = "chat";
    private static final String VARIAVEL_DA_SENHA = "CHAT_TLS_SENHA_KEYTOOL";
    private static final int VALIDADE_EM_DIAS = 825;

    private CertificadoTls() {
    }

    /**
     * Garante que existem o keystore e o certificado público. Não sobrescreve um keystore existente;
     * se só o {@code .crt} sumiu, ele é exportado de novo.
     *
     * @throws IOException se o keytool não puder ser executado ou o keystore não puder ser lido
     */
    public static void garantir(Path keystore, Path certificado, char[] senha) throws IOException {
        Objects.requireNonNull(keystore, "keystore");
        Objects.requireNonNull(certificado, "certificado");
        Objects.requireNonNull(senha, "senha");
        if (!Files.exists(keystore)) {
            gerarComKeytool(keystore, senha);
        }
        if (!Files.exists(certificado)) {
            exportar(keystore, certificado, senha);
        }
    }

    /** Monta o contexto TLS do servidor a partir do keystore. */
    public static SSLContext contextoDoServidor(Path keystore, char[] senha) throws IOException {
        KeyStore chaves = carregarKeystore(keystore, senha);
        try {
            KeyManagerFactory fabrica = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            fabrica.init(chaves, senha);
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(fabrica.getKeyManagers(), null, null);
            return contexto;
        } catch (GeneralSecurityException e) {
            throw new IOException("Não foi possível preparar o TLS do servidor com " + keystore
                    + ". Apague o arquivo para gerar um novo.", e);
        }
    }

    /** Monta o contexto TLS do cliente, que só confia no certificado do arquivo indicado. */
    public static SSLContext contextoDoCliente(Path certificado) throws IOException {
        if (!Files.isRegularFile(certificado)) {
            throw new IOException("Certificado do servidor não encontrado: " + certificado
                    + ". Copie o arquivo " + certificado.getFileName() + " da pasta do servidor para cá "
                    + "ou informe o caminho com --tls=caminho.crt.");
        }
        try (InputStream in = Files.newInputStream(certificado)) {
            Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
            KeyStore confiaveis = KeyStore.getInstance(KeyStore.getDefaultType());
            confiaveis.load(null, null);
            confiaveis.setCertificateEntry(ALIAS, cert);
            TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            fabrica.init(confiaveis);
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(null, fabrica.getTrustManagers(), null);
            return contexto;
        } catch (GeneralSecurityException e) {
            throw new IOException("O arquivo " + certificado + " não é um certificado válido. "
                    + "Copie de novo o .crt gerado pelo servidor.", e);
        }
    }

    private static KeyStore carregarKeystore(Path keystore, char[] senha) throws IOException {
        try (InputStream in = Files.newInputStream(keystore)) {
            KeyStore chaves = KeyStore.getInstance("PKCS12");
            chaves.load(in, senha);
            return chaves;
        } catch (IOException e) {
            throw new IOException("Não foi possível abrir " + keystore + ": confira a senha (tls.senha ou "
                    + "CHAT_TLS_SENHA) e se o arquivo é o keystore do chat.", e);
        } catch (GeneralSecurityException e) {
            throw new IOException("O arquivo " + keystore + " não é um keystore válido.", e);
        }
    }

    private static void exportar(Path keystore, Path certificado, char[] senha) throws IOException {
        try {
            Certificate cert = carregarKeystore(keystore, senha).getCertificate(ALIAS);
            if (cert == null) {
                throw new IOException("O keystore " + keystore + " não tem o certificado '" + ALIAS
                        + "'. Apague o arquivo para gerar um novo.");
            }
            String pem = "-----BEGIN CERTIFICATE-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(cert.getEncoded())
                    + "\n-----END CERTIFICATE-----\n";
            try (OutputStream out = Files.newOutputStream(certificado)) {
                out.write(pem.getBytes(StandardCharsets.US_ASCII));
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Não foi possível exportar o certificado para " + certificado + ".", e);
        }
    }

    private static void gerarComKeytool(Path keystore, char[] senha) throws IOException {
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        List<String> comando = List.of(keytool.toString(), "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA",
                "-keysize", "2048", "-validity", String.valueOf(VALIDADE_EM_DIAS), "-dname", "CN=chat",
                "-ext", "san=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12", "-keystore",
                keystore.toAbsolutePath().toString(), "-storepass:env", VARIAVEL_DA_SENHA);
        ProcessBuilder pb = new ProcessBuilder(comando).redirectErrorStream(true);
        Map<String, String> ambiente = pb.environment();
        ambiente.put(VARIAVEL_DA_SENHA, new String(senha));
        try {
            Process processo = pb.start();
            String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (processo.waitFor() != 0) {
                throw new IOException("O keytool falhou ao criar o certificado: " + saida.strip()
                        + ". A senha (tls.senha) precisa ter pelo menos 6 caracteres.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("A criação do certificado foi interrompida. Tente de novo.", e);
        } catch (IOException e) {
            throw new IOException("Não foi possível executar o keytool (" + keytool + "). Use um JDK ou JRE "
                    + "completo do Java 21 ou crie o keystore manualmente.", e);
        }
    }
}
