# language: pt
Funcionalidade: Reserva de recursos sem conflito de horário
  Para que ninguém seja surpreendido por uma sala ou professor ocupado,
  o sistema deve impedir reservas sobrepostas e exigir aprovação de recursos restritos.

  Contexto:
    Dado que existe a sala "Sala 201" sem restrição
    E que existe o laboratório "Lab Química" restrito
    E que existe o solicitante "ana"
    E que existe o solicitante "bruno"

  Cenário: Duas reservas no mesmo horário não são aceitas
    Dado que "ana" reservou "Sala 201" de 10h às 12h
    Quando "bruno" tenta reservar "Sala 201" de 11h às 13h
    Então a resposta deve ser 409
    E a sala "Sala 201" deve ter apenas 1 reserva ativa

  Cenário: Reservas consecutivas são aceitas
    Dado que "ana" reservou "Sala 201" de 10h às 12h
    Quando "bruno" tenta reservar "Sala 201" de 12h às 14h
    Então a resposta deve ser 201

  Cenário: Término igual ao início é recusado com mensagem clara
    Quando "ana" tenta reservar "Sala 201" de 10h às 10h
    Então a resposta deve ser 422
    E a mensagem deve conter "término"

  Cenário: Recurso restrito aguarda aprovação
    Quando "ana" tenta reservar "Lab Química" de 10h às 12h
    Então a resposta deve ser 201
    E o status da reserva deve ser "SOLICITADA"

  Cenário: Recurso sem restrição é aprovado automaticamente
    Quando "ana" tenta reservar "Sala 201" de 10h às 12h
    Então a resposta deve ser 201
    E o status da reserva deve ser "APROVADA"
