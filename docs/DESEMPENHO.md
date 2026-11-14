# Teste de desempenho com JMeter

Plano: `performance/alocacao.jmx` — dois grupos de usuários:

- **Leitura** (padrão 50 usuários): `GET /api/resources?type=SALA` (meta: ≤ 500 ms) e `GET /api/reservations`.
- **Escrita concorrente** (padrão 20 usuários): `POST /api/reservations` em horários aleatórios sobre o mesmo
  recurso. Respostas válidas: `201` ou `409`. **Qualquer 5xx reprova.**

## Executar
```bash
docker compose up -d db      # ou use MongoDB Atlas/local, veja o README
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # em outro terminal
AUTH=$(printf 'professor:Senha#12345' | base64)

jmeter -n -t performance/alocacao.jmx \
  -Jauth="$AUTH" -JresourceId=<ID_DO_RECURSO> -JreadUsers=50 -JwriteUsers=20 -Jduration=60 \
  -l performance/results/resultado.jtl -e -o performance/report
```
O `resourceId` é o campo `id` de qualquer recurso (consulte `GET /api/resources` no Swagger). Abra `performance/report/index.html`. Abra o `.jmx` no JMeter (modo GUI) ao menos uma vez para validá-lo.

## O que registrar no relatório da equipe
Máquina/versões, usuários simulados, vazão (req/s), p50/p95/p99, % de erro, e a análise de gargalos
(banco? pool de conexões? consulta sem índice?). Compare "antes" e "depois" de uma otimização que vocês fizerem.
Metas sugeridas, a confirmar com os dados: p95 de leitura < 500 ms, 0 erros 5xx, nenhuma dupla reserva no banco.
Verificação final de integridade após a carga (no `mongosh`): nenhuma faixa pode pertencer a uma reserva que não está mais ativa.
```javascript
// travas cujas reservas não existem ou foram liberadas (devem ser 0)
db.slot_locks.aggregate([
  { $lookup: { from: "reservations", localField: "reservationId", foreignField: "_id", as: "r" } },
  { $match: { $or: [ { r: { $size: 0 } }, { "r.status": { $in: ["CANCELADA", "REJEITADA", "NAO_COMPARECEU"] } } ] } },
  { $count: "travasOrfas" }
])
```
