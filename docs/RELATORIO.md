# Relatório: Chat Multiusuário com Histórico

Disciplina de Programação Orientada a Objetos. Java 21, sockets, JDBC com SQLite.

## 1. Arquitetura

O sistema tem um servidor e dois tipos de cliente (terminal e janela). O código é dividido em pacotes com uma responsabilidade cada.

- `model`: Usuario, Mensagem, Sala. Só regras do domínio.
- `persistence`: interfaces dos repositórios. `persistence.jdbc` guarda todo o SQL.
- `protocol`: transforma eventos em linhas de texto e de volta.
- `server`: Servidor, ClienteHandler, ClientRegistry e os serviços (ChatService, CommandService, SalaService, AuthService).
- `client`: controlador, conexão e telas.

O servidor aceita conexões em um laço e cria uma thread por cliente. O `ClienteHandler` só lê linhas e entrega ao processador. As regras ficam nos serviços.

O `ChatService` usa a interface `MensagemRepository`. A classe JDBC é escolhida só no `ServidorMain`. Nos testes entra o repositório em memória, então nenhum teste unitário usa banco ou rede.

O cliente gráfico segue o mesmo desenho: `ChatController` fala com uma interface de tela e uma interface de conexão. Cada interface tem versão real e versão falsa.

## 2. Concorrência

Estado compartilhado entre threads:

- a lista de clientes conectados (`ClientRegistry`);
- a ordem em que as mensagens são salvas e transmitidas (`ChatService`);
- a escrita no socket de cada cliente (`ClienteHandler`);
- o banco de dados.

O que foi feito:

- A lista de clientes tem um lock privado. Quem percorre a lista usa uma cópia.
- `ChatService` serializa "salvar e transmitir". Todos recebem as mensagens na mesma ordem.
- Cada cliente tem uma fila de saída e uma thread de escrita. Um cliente lento não trava os outros. Passando de 256 KB pendentes, ele é desconectado.
- Flags compartilhadas são `volatile` ou `AtomicBoolean`.
- Cada operação de banco abre e fecha a sua própria `Connection`. O SQLite usa WAL e `busy_timeout` de 5 segundos.
- O mesmo nome não entra duas vezes: a verificação e a atribuição são uma operação só, e o banco tem `UNIQUE`.

Riscos que ficaram:

- A gravação no banco acontece dentro do lock de ordem. Um `INSERT` lento atrasa todos os envios. Mantive porque a ordem igual para todos é o requisito.
- Não há keep-alive TCP.

A revisão completa, com arquivo e linha, está em `docs/REVISOES.md`.

### Demonstração da condição de corrida

Duas threads somam 1.000.000 de vezes cada em um contador. O esperado é 2.000.000.

- Sem sincronização: 1.950.533, 1.196.893, 1.983.247, 1.994.274 e 2.000.000 (cinco rodadas).
- Com `synchronized`: 2.000.000.
- Com `AtomicLong`: 2.000.000.

As duas threads leem o mesmo valor, as duas somam um, e um incremento se perde. O lock evita isso.

## 3. Testes

São 905 testes com JUnit 5. A suíte tem três grupos.

- Unitários, sem rede e sem banco real.
- Integração (`@Tag("integracao")`): SQLite em arquivo temporário e sockets em loopback.
- Janelas (`@Tag("gui")`): rodam só com tela ou `xvfb-run`.

Os testes de concorrência usam 8 threads, `@RepeatedTest(10)` e `CountDownLatch`, sem `Thread.sleep`. Memória e JDBC passam pelo mesmo contrato de testes.

## 4. Onde a IA foi usada

- Escreveu o código e os testes, em etapas com TDD, um commit por etapa.
- Fez as revisões de concorrência, JDBC, arquitetura e usabilidade.
- Explicou cada método em até 3 linhas, para a defesa.

Como validei:

- rodei a suíte várias vezes e só avancei com tudo verde;
- subi o servidor com três clientes e conferi o comportamento;
- olhei as capturas de tela da interface;
- revisei cada achado: virou teste ou decisão registrada.

Um erro da IA que peguei: ela afirmou "8 rodadas sem falha" sem ter contado. Passamos a contar `BUILD SUCCESS` de verdade, e isso expôs testes instáveis, que foram consertados.

## 5. Limitações

- A senha trafega sem criptografia, porque não há TLS. O hash PBKDF2 com salt protege só o armazenamento.
- Um teste está vermelho: `SalaServiceTest`, linha 118. O repositório falso só falha ao gravar, e o cenário exige falha ao ler. O código está correto. Não alterei o teste.
- Não há keep-alive: um cliente que some sem aviso só é notado pela fila ou pelo TCP.
- A janela não avisa de mensagens novas quando se lê o histórico antigo.
- `CommandService` e `ChatService` são as maiores classes e estão no limite do tamanho aceitável.
- Os testes de janela rodaram só em Linux.
