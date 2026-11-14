# Alocação de Recursos

Sistema para organizar a alocação de **salas, professores e materiais** sem conflitos de horário.
Java 17 · Spring Boot 3.2 · **MongoDB 7** · Spring Security · Testcontainers · WireMock · JaCoCo · SonarCloud · JMeter.

## Como rodar

Pré-requisito: Java 17 e um MongoDB (escolha UMA opção).

**Opção A — Docker**
```bash
docker compose up -d db          # MongoDB local na porta 27017
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Opção B — sem Docker: MongoDB Atlas (nuvem, plano gratuito)**
1. Crie uma conta e um cluster gratuito em https://www.mongodb.com/atlas, crie um usuário de banco e libere o seu IP.
2. Copie a *connection string* e rode (PowerShell):
```powershell
$env:MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster0.xxxxx.mongodb.net/alocacao"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Opção C — sem Docker: MongoDB Community instalado no Windows**
Instale o MongoDB Community Server (https://www.mongodb.com/try/download/community) com a opção "Install as a Service";
ele escuta em `localhost:27017`, que é o padrão da aplicação. Depois rode só o comando `spring-boot:run` acima.

- Interface: http://localhost:8080
- Documentação da API (OpenAPI/Swagger): http://localhost:8080/swagger-ui.html
- Usuários do perfil `dev` (senha `Senha#12345`, **somente para desenvolvimento**): `admin`, `responsavel`, `professor`.

Produção (sem perfil `dev`): informe `MONGODB_URI` e, para criar o primeiro administrador, `ADMIN_PASSWORD`.
Opcional: `NOTIFICATION_WEBHOOK_URL`. **Nenhuma senha fica no repositório.**

## Testes

| Comando | O que roda | Precisa de Docker? |
|---|---|---|
| `./mvnw test` | Unitários (JUnit 5, parametrizados, Mockito) | Não |
| `./mvnw verify` | Tudo: unitários + integração + API caixa-preta + E2E + BDD + cobertura JaCoCo | Sim (Testcontainers sobe um MongoDB real) |

Relatório de cobertura: `target/site/jacoco/index.html`. Relatório BDD: `target/cucumber-report.html`.

| Tipo | Onde | Observação |
|---|---|---|
| Unitários | `src/test/.../unit` | `ReservationStatusTest` (49 transições), `SlotsTest`, `ReservationServiceTest` (Mockito) |
| Integração com banco real | `it/MongoConstraintIT`, `it/ReservationConcurrencyIT` | MongoDB em container (Testcontainers) |
| Integração externa | `it/NotificationWebhookIT` | API de notificação simulada com WireMock |
| API caixa-preta | `it/ReservationApiIT` | só HTTP + RestAssured; matriz de autorização |
| End-to-end | `it/EndToEndFlowIT` | fluxo completo com os três perfis |
| BDD | `src/test/resources/features/reserva.feature` + `bdd/` | Cucumber em português |
| Concorrência | `ReservationConcurrencyIT` | 12 threads / 8 requisições HTTP simultâneas → exatamente 1 reserva |
| Desempenho | `performance/alocacao.jmx` | ver `docs/DESEMPENHO.md` |

## Como a dupla reserva é impedida no MongoDB

O MongoDB não tem restrição de sobreposição de intervalos, então o sistema usa **travas de horário**
(`SlotLock`, coleção `slot_locks`):

1. O tempo é dividido em faixas de **15 minutos**. Reservas começam e terminam em múltiplos de 15 min e duram no máximo 24 h.
2. Cada faixa de cada recurso é um documento cujo `_id` é `"idDoRecurso:faixa"`.
3. Para reservar, a aplicação grava todas as faixas necessárias. O MongoDB garante a unicidade de `_id` de forma
   **atômica**: se duas requisições disputam a mesma faixa, só uma consegue gravar; a outra **desfaz as travas que já
   gravou** e recebe `409`.
4. Antes disso há uma verificação prévia, só para dar mensagem clara ("já existe reserva para Sala 101") e contar
   "conflitos evitados".
5. Cancelar, rejeitar ou marcar não comparecimento **libera** as faixas.

Consequências a registrar na apresentação: intervalo semiaberto `[início, fim)` (reservas consecutivas são aceitas);
o MongoDB em instância única não tem transações multidocumento, então a gravação segue o padrão
"reservar faixas → salvar reserva → auditar" com desfazer em caso de falha (o `@Version` protege decisões simultâneas
sobre a mesma reserva). Uma falha entre "salvar" e "auditar" poderia deixar uma mudança sem registro de auditoria; em
produção, um *replica set* com transações ou o padrão *outbox* fecharia essa janela.

## Outras decisões

- **Sala, professor e material são `resources`**: a agenda do professor usa a mesma regra de travas das salas (RF5).
- **Estados em um enum** (`ReservationStatus.canTransitionTo`); toda transição passa por `changeStatus`, que grava auditoria.
- **Autorização em duas camadas**: regras por URL/perfil no `SecurityConfig` (falha fechada) e verificação de perfil/dono no serviço.
- **Notificação só depois que a reserva foi gravada**: tentativa conflitante não notifica, e falha da API externa nunca desfaz a reserva.

## Segurança

- Senhas com BCrypt; HTTP Basic *stateless* (sem cookie, por isso CSRF desabilitado e documentado).
- 401/403 em JSON, sem `WWW-Authenticate` (evita pop-up do navegador).
- Validação de entrada (Bean Validation); erros em `ProblemDetail` sem stack trace ou nomes de classe
  (`GlobalExceptionHandler`, teste `errorResponsesDoNotLeakInternalDetails`).
- A interface usa `textContent` (nunca `innerHTML`); segredos só por variável de ambiente.

**Limitações conhecidas**: sem limitação de tentativas de login, HTTP Basic reenvia a senha a cada chamada (use HTTPS),
sem Content-Security-Policy (Bootstrap via CDN sem hash), e a janela de auditoria descrita acima.

## CI e qualidade

- `.github/workflows/ci.yml`: roda `./mvnw verify` em todo *pull request* e em `main`, publica relatórios e envia ao SonarCloud.
- Configure uma vez: crie o projeto no SonarCloud, ajuste `sonar.organization` e `sonar.projectKey` no `pom.xml` e
  cadastre o segredo `SONAR_TOKEN`. No GitHub, exija o check `CI` na proteção da branch.

## Documentação
- `docs/REQUISITOS.md`: matriz requisito → código → teste. `docs/TDD-BDD.md`: evidência de TDD/BDD. `docs/DESEMPENHO.md`: JMeter.
