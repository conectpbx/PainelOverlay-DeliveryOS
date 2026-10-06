# Releases e versionamento

Este projeto usa versionamento semântico no formato `vMAJOR.MINOR.PATCH`.

Exemplos:

- `v1.0.0`: primeira versão estável.
- `v1.1.0`: nova funcionalidade compatível.
- `v1.1.1`: correção de bug.

## Publicar uma nova versão

Na máquina local:

```bash
git checkout main
git pull
git tag v1.0.0
git push origin v1.0.0
```

Ao receber uma tag iniciada por `v`, o GitHub Actions executa:

1. Checkout do código.
2. Configuração do Java 17.
3. Configuração do Gradle 8.7.
4. `gradle clean assembleRelease`.
5. Renomeia o APK com a versão.
6. Cria automaticamente um GitHub Release.
7. Anexa o APK ao Release.

## Observação sobre assinatura

A configuração atual gera um APK Release não assinado. Ele é útil para validação do pipeline, mas para distribuição/instalação oficial deve ser assinado.

A evolução recomendada é adicionar secrets do GitHub:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Nunca publique o arquivo `.jks` ou senhas no repositório.
