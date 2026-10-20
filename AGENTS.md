# AGENTS.md — Chat Multiusuário com Histórico

Projeto acadêmico de Programação Orientada a Objetos (Java). Cliente-servidor com
sockets, uma thread por cliente, histórico em SQLite via JDBC. A especificação do
professor vence qualquer regra daqui em caso de conflito.

## Stack
- Java 21, Maven, JUnit 5, SQLite (`sqlite-jdbc`, arquivo `chat.db`), FlatLaf.
- Só essas dependências. Proibido: Spring, Hibernate/JPA, Docker, frameworks de mensagens.

## Pacotes (`br.unicesumar.chat`)
| Pacote | Conteúdo |
|---|---|
| `model` | Usuario, Mensagem, Sala, CredencialUsuario (imutáveis, domínio puro) |
| `persistence` | interfaces MensagemRepository, UsuarioRepository |
| `persistence.jdbc` | JdbcMensagemRepository, JdbcUsuarioRepository, ConnectionFactory |
| `persistence.memory` | repositórios em memória (testes) |
| `protocol` | Protocolo (codificar/decodificar eventos de texto) |
| `server` | Servidor, ClienteHandler, ClientRegistry, ChatService, CommandService, SalaService, AuthService |
| `infra` | ExportadorHistorico, LogArquivo |
| `client.net` | ConexaoChat (interface), ConexaoSocket, ConexaoFalsa |
| `client.controller` | ChatController, ChatView (interface) |
| `client.ui` | JanelaLogin, JanelaChat, DialogoBusca, Tema (Swing) |
| `client` | ClienteChat (terminal) |

## Regras de arquitetura
- `model` não importa `java.sql`, `java.net`, `java.io` nem `javax.swing`.
- Socket não conhece SQL. Repository não conhece socket. Model não conhece nenhum dos dois. Service conecta as partes.
- `ChatService` depende da interface `MensagemRepository`; a implementação JDBC é injetada no main.
- `Servidor` e `ClienteHandler` não têm SQL nem regra de negócio. Comandos vão para `CommandService`.
- `ChatController` não importa `javax.swing`. Cliente (terminal ou gráfico) nunca conhece Repository.
- Toda interface (porta) tem dois adaptadores: o real e o falso (memória / ConexaoFalsa).
- Sem God Classes: antes de criar uma classe, confirme que há responsabilidade nova.

## Concorrência
- Todo estado compartilhado fica em `ClientRegistry` (coleção thread-safe, iteração protegida).
- Escrita no `PrintWriter` de cada cliente protegida (`enviar()` synchronized ou fila por cliente).
- Flags de parada compartilhadas são `volatile`.
- Nunca compartilhar `Connection` JDBC: cada operação abre e fecha a sua (try-with-resources).
- SQLite: `journal_mode=WAL` e `busy_timeout=5000` no `ConnectionFactory`.
- Sempre `PreparedStatement`; nunca concatenar SQL. Não engolir exceções: registrar em log.
- Testes de concorrência não usam `Thread.sleep`: usar `join`, `CountDownLatch`, `awaitTermination`.

## Testes (TDD)
- Primeiro os testes (devem falhar), depois o mínimo de código, depois refatorar.
- Nunca alterar um teste só para fazê-lo passar.
- Testes unitários: sem rede e sem banco real (repositório em memória).
- Testes que usam SQLite em arquivo temporário são de integração (`@Tag("integracao")`).
- Rodar: `mvn test` (tudo) — a cada etapa só avançar com tudo verde.

## Estilo
- Nomes em português; Javadoc em todos os métodos públicos.
- Objetos de domínio imutáveis. Mensagens de erro dizem o que aconteceu e o que fazer.
- Alterações localizadas: não reescrever o projeto quando uma mudança pequena resolve.
- Nunca versionar `*.db`, `target/`, chaves ou tokens.
