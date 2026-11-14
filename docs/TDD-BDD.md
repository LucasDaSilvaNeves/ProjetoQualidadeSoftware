# Evidências de TDD e BDD

## O que já está no repositório (BDD)
`src/test/resources/features/reserva.feature` descreve o comportamento em português (Gherkin) e é executado
pelo Cucumber (`bdd/`) contra a aplicação real. O relatório sai em `target/cucumber-report.html`.

## O que você precisa produzir (TDD) — e que ninguém consegue fazer por você
A evidência de TDD é o **histórico do seu repositório**: um commit com o teste falhando, depois outro com o
código que o faz passar. Sugestão de funcionalidade nova para a equipe desenvolver assim:
**"limite de duração da reserva" (ex.: no máximo 4 h, exceto ADMINISTRADOR)** ou
**"antecedência mínima de 2 h para solicitar"**.

Passo a passo, um commit por etapa:

1. `test: reserva acima de 4h deve ser recusada (RED)` — escreva o cenário em `reserva.feature` e/ou o teste em
   `ReservationServiceTest`. Rode `./mvnw test` e **guarde a captura do teste falhando**.
2. `feat: limite de duração da reserva (GREEN)` — o mínimo de código para passar. Capture o teste passando.
3. `refactor: extrai validação de duração` — melhore sem mudar comportamento; testes continuam verdes.

Entregue: os links dos 3 commits, as capturas (vermelho e verde) e um parágrafo sobre o que o teste revelou.
No Pull Request, preencha o checklist do template.
