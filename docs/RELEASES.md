# Releases e versionamento

Este projeto usa versionamento semântico no formato `vMAJOR.MINOR.PATCH`.

Exemplos:

- `v1.0.0`: primeira versão estável.
- `v1.1.0`: nova funcionalidade compatível.
- `v1.1.1`: correção de bug.

## Publicar por tag

Na máquina local:

```bash
git checkout main
git pull
git tag v1.0.0
git push origin v1.0.0
```

## Publicar manualmente pelo GitHub Actions

Abra `Actions` > `Android Release` > `Run workflow` e informe a versão, por exemplo `v1.0.0`.

O workflow executa:

1. Checkout do código.
2. Configuração do Java 17.
3. Configuração do Gradle 8.7.
4. `gradle clean assembleDebug`.
5. Renomeia o APK para incluir a versão.
6. Cria automaticamente um GitHub Release.
7. Anexa o APK instalável ao Release.

## Assinatura atual

O APK publicado automaticamente nesta fase usa a assinatura de debug gerada pelo Android/Gradle. Ele é instalável e adequado para homologação e testes internos, mas não deve ser tratado como assinatura oficial de produção.

## Assinatura de produção

Para releases comerciais/produção, configure uma keystore própria usando GitHub Actions Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Nunca publique o arquivo `.jks`, `.keystore` ou senhas no repositório.
