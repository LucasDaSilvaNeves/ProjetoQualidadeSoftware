# Rastreabilidade: requisitos → implementação → testes

## Requisitos funcionais

| # | Requisito | Implementação | Testes |
|---|---|---|---|
| RF1 | Autenticação e autorização por perfil | `SecurityConfig`, `AppUserDetailsService` | `ReservationApiIT`: `requestWithoutCredentialsIsRejected`, `wrongPasswordIsRejected`, `roleIsForbiddenFromOperation` (13 casos) |
| RF2 | Cadastro e consulta de salas, professores e materiais | `ResourceController`, `ResourceService` | `searchFiltersBy...`, `resourceAdministrationValidatesInput`, `EndToEndFlowIT` |
| RF3 | Pesquisa por tipo, capacidade, localização, competência e disponibilidade | `ResourceSpecifications` | `searchFiltersByTypeCapacityLocationCompetencyAndAvailability`, `searchByAvailabilityRequiresBothBoundaries` |
| RF4 | Criar, alterar e cancelar reservas | `ReservationService.create/update/cancel` | `ReservationApiIT` (criação, `updatingToAnOccupiedSlotIsRejected`, `cancelledReservationFreesTheSlot`), `ReservationServiceTest` |
| RF5 | Sobreposição, inclusive na agenda do professor | `checkOverlaps` + travas `SlotLock` | `professorAgendaConflictIsDetectedEvenInADifferentRoom`, `MongoConstraintIT`, `SlotsTest`, BDD "Duas reservas no mesmo horário" |
| RF6 | Proteção contra dupla reserva simultânea | `_id` único das travas + `acquireSlots` (tudo ou nada) | `ReservationConcurrencyIT` (2 testes), `duplicateKeyOnSlotLockBecomesConflictAndUndoesPartialLocks` |
| RF7 | Aprovação obrigatória para recursos restritos | `Resource.restricted`, `doCreate` | `restrictedResourceRequiresApproval`, `onlyResponsavelApprovesRestrictedReservation`, `ReservationServiceTest` |
| RF8 | Bloqueio por manutenção | `MaintenanceWindow`, `checkMaintenance` | `resourceUnderMaintenanceCannotBeReserved`, `blocksResourceUnderMaintenance` |
| RF9 | Retirada e devolução | `checkout`, `giveBack` | `EndToEndFlowIT`, `checkoutAndReturnRecordWhoAndWhen` |
| RF10 | Histórico auditável | `AuditService`, `AuditController` | `EndToEndFlowIT` (passo 6), `everyStateChangeGeneratesAnAuditEntry` |
| RF11 | Notificação simulada ou API externa | `NotificationService` | `NotificationWebhookIT` (4 testes, WireMock) |
| RF12 | Relatório de utilização, carga horária e conflitos evitados | `ReportService` | `ReportIT` (3 testes) |
| RF13 | Interface responsiva e mensagens compreensíveis | `static/`, `GlobalExceptionHandler` | `missingFieldsReturnFieldErrors`, `endMustBeAfterStart` |
| RF14 | Documentação da API | springdoc (`/swagger-ui.html`), `@Operation` | — (inspeção manual) |

## Regras críticas

| Regra | Garantia | Teste |
|---|---|---|
| Término posterior ao início | `validatePeriod` | `endMustBeAfterStart`, `rejectsInvalidPeriods` (parametrizado), `timesMustBeMultiplesOfFifteenMinutes` |
| Mesmo recurso não se sobrepõe | travas de faixa de 15 min | `MongoConstraintIT`, `SlotsTest` |
| Duas solicitações simultâneas → uma reserva | idem + tradução do erro em 409 | `ReservationConcurrencyIT` |
| Recurso em manutenção não reserva | `checkMaintenance` | ver RF8 |
| Fluxo de estados | `ReservationStatus.canTransitionTo` | `ReservationStatusTest` (49 combinações) |
| Somente responsável aprova | URL + serviço | `roleIsForbiddenFromOperation`, `onlyResponsavelCanApprove` |
| Reserva iniciada não é apagada | `delete()` → 409 | `startedReservationCannotBeDeleted`, `startedReservationsCannotBeDeleted` |
| Toda mudança de estado gera auditoria | `changeStatus` | `everyStateChangeGeneratesAnAuditEntry` |

## Interpretações adotadas (registre na apresentação)

- "Retirada/devolução" é registrada por reserva (não por item), pelo perfil Responsável.
- Reserva só com recursos não restritos é aprovada automaticamente (auditada como `APROVACAO_AUTOMATICA`).
- Alterar o horário de uma reserva aprovada com recurso restrito a devolve para `SOLICITADA`.
- "Conflitos evitados" conta cada tentativa bloqueada, por recurso (na corrida detectada só pelo banco, conta
  todos os recursos da tentativa).
- Datas e horas são locais, sem fuso horário.
- Reservas usam faixas de 15 minutos (início/fim múltiplos de 15) e duram no máximo 24 h, por causa das travas de horário.
