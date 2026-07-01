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
- `trae-debug-log-tenant-login-bounce.ndjson:1-3`: o bootstrap inicial começa sem token e encerra corretamente, sem limpar sessão válida.
- `trae-debug-log-tenant-login-bounce.ndjson:5-9`: o login em `lopesconsultoria.chamaqui.app.br` retorna sucesso, traz `authToken`, salva o token e chama `handleAuthenticatedUser`.
- `trae-debug-log-tenant-login-bounce.ndjson:11-13`: as primeiras chamadas autenticadas para `/api/v1/profile` e `/api/v1/tickets` saem com `Authorization: Bearer ...`.
- `trae-debug-log-tenant-login-bounce.ndjson:14-18`: mesmo com bearer token presente, `/api/v1/profile` recebe `401`, o dashboard chama `handleNavigateLogin()` e a sessão local é removida.

## Verificação de Hipóteses
| ID | Hipótese | Status | Evidência |
|----|----------|--------|-----------|
| A | Requisição sai sem `Authorization` | ❌ Rejeitada | Linhas 11-13 mostram `hasAuthorization: true` para `/api/v1/profile` e `/api/v1/tickets`. |
| B | Backend rejeita o token recém-emitido no primeiro endpoint protegido | ✅ Confirmada | Linhas 5-9 mostram token emitido e salvo; linhas 14-18 mostram `401` imediato no primeiro endpoint protegido. |
| C | `bootstrapSession()` ou `handleAuthenticatedUser()` apaga o estado autenticado por disputa interna | ❌ Rejeitada | Linhas 7-9 mostram login concluído e navegação correta; a limpeza só ocorre após o `401` da API. |
| D | Token salvo/lido no host errado | ❌ Rejeitada | Linhas 1, 4, 8, 10 e 17 mostram leitura e escrita consistentes no mesmo host. |
| E | O primeiro bundle autenticado recebe `401` e força logout | ✅ Confirmada | Linhas 14-18 mostram exatamente essa sequência. |

## Causa Raiz Confirmada
- A evidência de runtime confirma que o problema não está no login do frontend nem no armazenamento local do token.
- A causa raiz está no backend: o `AppSessionAuthenticationFilter` valida o bearer token antes de o `TenantResolutionFilter` popular o `TenantContext`.
- Como o token é assinado com escopo de tenant, a validação sem `TenantContext` rejeita o token recém-emitido e responde `401` já na primeira chamada protegida.

## Próximo Passo
- Ajustar a ordem dos filtros em `SecurityConfig` para garantir que o tenant seja resolvido antes da autenticação do app.
- Manter a instrumentação atual para comprovar o comportamento `post-fix`.

## Próximo Passo
- Adicionar instrumentação de debug no frontend e backend para capturar:
  - emissão do token;
  - leitura do token;
  - headers enviados;
  - resposta `401`;
  - ponto exato que chama o redirecionamento para login.
