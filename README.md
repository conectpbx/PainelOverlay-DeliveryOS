# PainelOverlay Delivery OS

Aplicativo Android nativo em Kotlin para exibir um painel flutuante com velocidade, distância da viagem e odômetro, além de disponibilizar os dados por API local e enviá-los automaticamente para o Delivery OS.

## Principais recursos

- Painel flutuante (overlay) sobre outros aplicativos.
- Velocidade em km/h usando GPS.
- Distância da viagem atual.
- Odômetro total persistente.
- API HTTP local em `http://IP_DO_CELULAR:8080/status`.
- Integração automática com Delivery OS por HTTP.
- URL do endpoint configurável.
- Métodos HTTP `POST`, `PUT` e `PATCH`.
- Autenticação Bearer Token, API Key, Header personalizado ou sem autenticação.
- Intervalo de sincronização configurável.
- Teste de conexão diretamente pelo aplicativo.
- Configuração persistida localmente com `SharedPreferences`.

## Arquitetura

```text
GPS Android
   |
   v
OverlayService
   |-----------------------> Overlay flutuante
   |-----------------------> ApiServer (/status :8080)
   |
   v
DeliveryOsClient
   |
   v
HTTP POST / PUT / PATCH
   |
   v
Delivery OS / API / Webhook / n8n
```

## Estrutura do projeto

```text
PainelOverlay-DeliveryOS/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/web/dashboard.html
│       ├── java/com/painel/overlay/
│       │   ├── ApiServer.kt
│       │   ├── DeliveryOsClient.kt
│       │   ├── GeoUtils.kt
│       │   ├── MainActivity.kt
│       │   ├── OverlayService.kt
│       │   └── Prefs.kt
│       └── res/
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
```

## Requisitos

- JDK 17
- Android SDK
- Android SDK Platform 34
- Android Gradle Plugin 8.5.0
- Kotlin 1.9.24

O projeto usa `compileSdk 34` e `targetSdk 34`.

## Preparação do Gradle Wrapper

Se `gradlew` não existir no pacote baixado:

```bash
gradle wrapper --gradle-version 8.7
chmod +x gradlew
./gradlew --version
```

## Build Debug

```bash
./gradlew clean
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build Release

```bash
./gradlew clean
./gradlew assembleRelease
```

Artefatos:

```text
app/build/outputs/apk/release/
```

Para distribuição oficial, configure uma keystore de assinatura.

## Integração automática com Delivery OS

A tela principal permite configurar URL, método HTTP, autenticação, credencial e intervalo de envio.

### URL

Exemplo:

```text
https://delivery.exemplo.com/api/v1/telemetry
```

Pode apontar para uma API REST do Delivery OS, webhook n8n, middleware, API Spring Boot ou outro endpoint HTTP compatível.

### Métodos HTTP

- `POST`
- `PUT`
- `PATCH`

Para telemetria contínua, `POST` é recomendado.

### Autenticação

#### Bearer Token

```http
Authorization: Bearer SEU_TOKEN
Content-Type: application/json
```

#### API Key

Exemplo:

```http
X-API-Key: SUA_CHAVE
```

#### Header personalizado

Exemplo:

```http
X-Delivery-Token: TOKEN_INTERNO
```

#### Sem autenticação

Útil apenas em ambiente local/controlado. Para produção pública, utilize autenticação e HTTPS.

## Intervalo de envio

Configurável entre 2 e 3600 segundos. Para rastreamento quase em tempo real, 5 segundos é uma configuração típica.

## Payload de telemetria

```json
{
  "source": "painel-overlay",
  "latitude": -23.5505,
  "longitude": -46.6333,
  "accuracy_m": 5.2,
  "speed_kmh": 38.4,
  "trip_km": 12.7,
  "total_km": 1852.3,
  "captured_at": 1791240000000,
  "sent_at": 1791240001000
}
```

| Campo | Descrição |
|---|---|
| `source` | Origem da telemetria. |
| `latitude` | Latitude atual. |
| `longitude` | Longitude atual. |
| `accuracy_m` | Precisão GPS em metros. |
| `speed_kmh` | Velocidade em km/h. |
| `trip_km` | Distância da viagem atual. |
| `total_km` | Odômetro total persistido. |
| `captured_at` | Timestamp da captura GPS. |
| `sent_at` | Timestamp do envio HTTP. |

## Exemplo de endpoint Spring Boot

```java
@PostMapping("/api/v1/telemetry")
public ResponseEntity<Void> receive(@RequestBody TelemetryRequest request) {
    telemetryService.save(request);
    return ResponseEntity.accepted().build();
}
```

## Exemplo com n8n

Webhook:

```text
Method: POST
Path: delivery-os/telemetry
```

Fluxo sugerido:

```text
Webhook
  -> Validar autenticação
  -> Validar payload
  -> Identificar entregador/veículo
  -> Persistir ponto GPS
  -> Atualizar rota/entrega
  -> Responder HTTP 200/202
```

## Teste de conexão

Respostas comuns:

| Código | Significado |
|---|---|
| 200 | Sucesso. |
| 201 | Recurso criado. |
| 202 | Aceito para processamento. |
| 400 | Payload/configuração inválida. |
| 401 | Credencial inválida ou ausente. |
| 403 | Sem permissão. |
| 404 | Endpoint incorreto. |
| 405 | Método não permitido. |
| 500 | Erro no servidor. |

## API HTTP local

```text
GET http://IP_DO_CELULAR:8080/status
```

Ela pode ser consumida pela PWA ou outro equipamento na mesma rede, conforme regras de rede/Android.

## Integração com o PWA Delivery OS

### Push automático

```text
PainelOverlay -> Internet -> API Delivery OS
```

É a estratégia recomendada para rastreamento remoto.

### Consulta local

```text
PWA Delivery OS -> GET /status -> PainelOverlay
```

Depende de conectividade local, regras de rede e CORS.

## Segurança recomendada

- Use HTTPS.
- Não publique tokens no código.
- Use credenciais diferentes por dispositivo/entregador.
- Faça rotação de tokens.
- Valide timestamp e dispositivo no backend.
- Implemente rate limit.
- Não confie em IDs enviados pelo cliente sem validação.
- Não grave segredos em logs.

## Evolução recomendada

O backend pode associar a credencial a entregador, veículo, entrega ativa e rota ativa. Assim cada ponto GPS entra automaticamente no histórico da entrega.

Exemplo futuro:

```json
{
  "device_id": "device-001",
  "delivery_id": "DEL-12345",
  "latitude": -23.5505,
  "longitude": -46.6333,
  "speed_kmh": 38.4,
  "trip_km": 12.7,
  "captured_at": 1791240000000
}
```

## Troubleshooting

### `gradlew` inexistente

```bash
gradle wrapper --gradle-version 8.7
chmod +x gradlew
```

### Android SDK não encontrado

Crie `local.properties`:

```properties
sdk.dir=/home/SEU_USUARIO/Android/Sdk
```

### Licenças Android

```bash
yes | sdkmanager --licenses
```

### Diagnóstico de build

```bash
./gradlew assembleDebug --stacktrace
```

ou:

```bash
./gradlew assembleDebug --info
```

### HTTP 401

Revise token, API Key ou header.

### HTTP 404

Revise URL e caminho do endpoint.

### HTTP 405

Confirme que o método configurado corresponde ao aceito pela API.

### GPS sem atualização

Confirme permissões de localização, serviço ativo e restrições de bateria do fabricante.

## Tecnologias

- Kotlin
- Android SDK
- Gradle
- HttpURLConnection
- SharedPreferences
- GPS / Location APIs
- Foreground Service
- Android Overlay

## Licença

Defina a licença conforme a política da ConectPbx antes de redistribuição pública ou comercial.
