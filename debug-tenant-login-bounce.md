# Debug Session: tenant-login-bounce [OPEN]

## Sintoma
- Após informar login e senha em qualquer subdomínio do ChamaQui, a aplicação entra na tela de tickets por menos de 1 segundo e retorna sozinha para a tela de login.
- O problema não deve ocorrer em nenhum subdomínio atual nem futuro.

## Impacto
- Bloqueio total de autenticação no app multi-tenant.
- Regressão crítica introduzida após a mudança para token por tenant.

## Hipóteses Falsificáveis
1. O frontend salva o `authToken`, mas uma chamada imediata subsequente está saindo sem `Authorization` e provoca `401`, disparando retorno para login.
2. O backend emite `authToken` no login, porém o `/api/v1/auth/me` ou o primeiro bundle autenticado rejeita esse token por escopo de tenant incorreto.
3. O `bootstrapSession()` e o fluxo de `handleAuthenticatedUser()` estão competindo entre si, sobrescrevendo estado autenticado com estado nulo.
4. O token está sendo persistido em uma origem e lido em outra chave/host diferente, fazendo o app acreditar que não há sessão logo após o login.
5. Alguma chamada periódica inicial do dashboard recebe `401` por causa do novo filtro e força `handleNavigateLogin()` mesmo com login recém-concluído.

## Plano Inicial
1. Instrumentar login, persistência/leitura do token e transição para a tela autenticada no frontend.
2. Instrumentar o filtro de autenticação e a validação do token no backend.
3. Reproduzir o erro e comparar a sequência de eventos entre login bem-sucedido e retorno indevido ao `/login`.
4. Só então aplicar a correção mínima baseada nos logs.

## Evidências
- Pendente.

## Próximo Passo
- Adicionar instrumentação de debug no frontend e backend para capturar:
  - emissão do token;
  - leitura do token;
  - headers enviados;
  - resposta `401`;
  - ponto exato que chama o redirecionamento para login.
