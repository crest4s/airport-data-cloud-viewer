# DEPLOYMENT.md — Despliegue manual desde la consola AWS

Guía completa para desplegar el backend del proyecto desde la consola web de AWS
**sin instalar ninguna herramienta** en tu ordenador.

> Los archivos `template.yaml`, `AWS-CONFIG.md` e `IAM-SETUP.md` siguen disponibles
> para quien quiera usar SAM CLI. Esta guía cubre exactamente la misma infraestructura
> pero creada a mano, clic a clic.

---

## Visión general de lo que vamos a crear

```
Scala App / Web Viewer
        │
        │  HTTP POST / GET
        ▼
Lambda Function URL  ──── boto3 ────►  DynamoDB
(handler.py)                           tabla: airport-results
```

| Recurso | Nombre exacto | Servicio |
|---|---|---|
| Función Lambda | `AirportResultsFunction` | AWS Lambda |
| Tabla NoSQL | `airport-results` | Amazon DynamoDB |
| Rol de ejecución | `AirportResultsFunction-role` | IAM |
| URL pública | se genera automáticamente | Lambda Function URL |

Tiempo estimado: **20–30 minutos**.
Coste: **$0** dentro del Free Tier.

---

## Requisitos previos

- Cuenta AWS activa (vale la capa gratuita)
- Acceso a la consola en [https://console.aws.amazon.com](https://console.aws.amazon.com)
- El archivo `cloud-api/lambda/handler.py` de este repositorio

> **Región recomendada**: usa la misma en todos los pasos.
> Esta guía usa `eu-west-1` (Irlanda) como ejemplo. >> Utiliza esta, funciona muy bien y es económica
> Si eliges otra, sustitúyela en todos los ARNs y URLs.

---

## Paso 1 — Crear la tabla DynamoDB

### 1.1 Ir a DynamoDB

1. En la consola AWS, busca **DynamoDB** en la barra de búsqueda superior.
2. Pulsa **Create table**.

### 1.2 Configurar la tabla

Rellena exactamente estos campos:

| Campo | Valor |
|---|---|
| **Table name** | `airport-results` |
| **Partition key** | `id` |
| **Partition key type** | `String` |
| **Sort key** | *(dejar vacío — no se usa)* |

### 1.3 Ajustes de capacidad

- En **Table settings** selecciona **Customize settings**.
- En **Read/write capacity settings** elige **On-demand**.
  > Esto equivale a `PAY_PER_REQUEST` en el template SAM.
  > Sin capacidad aprovisionada → coste cero en pruebas.

### 1.4 Cifrado y tags

- Deja el cifrado por defecto (**Owned by Amazon DynamoDB**).
- Tags: no son obligatorios.

### 1.5 Crear

- Pulsa **Create table**.
- Espera a que el estado cambie a **Active** (puede tardar 30–60 segundos).

Anota el nombre: `airport-results` (lo necesitarás en el Paso 3).

---

## Paso 2 — Crear el rol IAM para la Lambda

La Lambda necesita un rol que le permita leer y escribir en DynamoDB.

### 2.1 Ir a IAM

1. Busca **IAM** en la barra de búsqueda.
2. En el menú lateral, pulsa **Roles → Create role**.

### 2.2 Tipo de entidad

- **Trusted entity type**: `AWS service`
- **Use case**: `Lambda`
- Pulsa **Next**.

### 2.3 Añadir permisos

Busca y marca estas dos políticas gestionadas por AWS:

| Política | Para qué |
|---|---|
| `AWSLambdaBasicExecutionRole` | Permite a la Lambda escribir logs en CloudWatch |
| `AmazonDynamoDBFullAccess` | Permite acceso completo a DynamoDB |

> Si quieres ser más restrictivo (recomendado para producción), en lugar de
> `AmazonDynamoDBFullAccess` crea una política inline — ver sección al final.
> Para el proyecto de clase `AmazonDynamoDBFullAccess` es suficiente.

Pulsa **Next**.

### 2.4 Nombre y crear

| Campo | Valor |
|---|---|
| **Role name** | `AirportResultsFunction-role` |
| **Description** | `Rol de ejecución para la Lambda del proyecto airport-data` |

Pulsa **Create role**.

Anota el **ARN del rol** — tiene este formato:
```
arn:aws:iam::<TU-ACCOUNT-ID>:role/AirportResultsFunction-role
```
Lo necesitarás en el Paso 3.

---

## Paso 3 — Crear la función Lambda

### 3.1 Ir a Lambda

1. Busca **Lambda** en la barra de búsqueda.
2. Pulsa **Create function**.

### 3.2 Configuración básica

| Campo | Valor |
|---|---|
| **Author from scratch** | seleccionado (opción por defecto) |
| **Function name** | `AirportResultsFunction` |
| **Runtime** | `Python 3.12` |
| **Architecture** | `x86_64` |

### 3.3 Asignar el rol creado en el Paso 2

- Despliega **Change default execution role**.
- Selecciona **Use an existing role**.
- En el desplegable, busca y selecciona `AirportResultsFunction-role`.

Pulsa **Create function**.

---

## Paso 4 — Subir el código de la Lambda

### 4.1 Preparar el archivo ZIP

En tu ordenador, comprime **solo** el archivo `handler.py`:

**macOS / Linux:**
```bash
cd cloud-api/lambda
zip function.zip handler.py
```

**Windows (PowerShell):**
```powershell
Compress-Archive -Path handler.py -DestinationPath function.zip
```

> El archivo `requirements.txt` contiene solo `boto3`, que ya viene incluido
> en el entorno de Python 3.12 de Lambda. No hace falta empaquetarlo.

### 4.2 Subir el ZIP

1. En la página de la función Lambda, ve a la pestaña **Code**.
2. Pulsa **Upload from → .zip file**.
3. Selecciona el `function.zip` que acabas de crear.
4. Pulsa **Save**.

Verifica que en el editor de código aparece el contenido de `handler.py`.

### 4.3 Comprobar el handler

En la sección **Runtime settings** (debajo del editor de código):

- Pulsa **Edit**.
- **Handler**: debe ser `handler.lambda_handler`
  > Si no lo es, cámbialo a ese valor exacto.
- Pulsa **Save**.

---

## Paso 5 — Configurar variables de entorno y timeout

### 5.1 Ir a Configuration → General configuration

1. Pestaña **Configuration** → **General configuration** → **Edit**.
2. Cambia **Timeout** a `0 min 30 sec`.
3. Pulsa **Save**.

### 5.2 Variables de entorno

1. Pestaña **Configuration** → **Environment variables** → **Edit**.
2. Pulsa **Add environment variable**:

| Key | Value |
|---|---|
| `TABLE_NAME` | `airport-results` |

3. Pulsa **Save**.

---

## Paso 6 — Activar la Function URL (endpoint público)

La Function URL reemplaza a API Gateway — es la URL que usarán la app Scala y el web viewer.

### 6.1 Crear la URL

1. Pestaña **Configuration** → **Function URL** → **Create function URL**.

| Campo | Valor |
|---|---|
| **Auth type** | `NONE` |
| *(esto la hace pública, sin firma IAM)* | |

2. Despliega **Additional settings** y activa **Configure cross-origin resource sharing (CORS)**.
3. Rellena los campos CORS:

| Campo | Valor |
|---|---|
| **Allow origin** | `*` |
| **Allow methods** | marca `GET`, `POST`, `OPTIONS` |
| **Allow headers** | `Content-Type` |
| **Max age** | *(dejar vacío)* |

4. Pulsa **Save**.

### 6.2 Copiar la URL

Después de guardar verás la **Function URL** en la parte superior de la sección.
Tiene este formato:
```
https://<id-aleatorio>.lambda-url.<region>.on.aws/
```

**Copia esta URL — la necesitas en los pasos 7 y 8.**

---

## Paso 7 — Probar que la Lambda funciona

### 7.1 Test rápido desde la consola

1. Ve a la pestaña **Test**.
2. Pulsa **Create new test event**.
3. **Event name**: `test-post`
4. **Event JSON**:

```json
{
  "requestContext": {
    "http": {
      "method": "POST"
    }
  },
  "body": "{\"phase\":\"Phase01\",\"parameters\":\"threshold=5\",\"result\":\"10 vuelos encontrados\",\"timestamp\":\"2026-05-08T10:00:00Z\",\"username\":\"test\"}"
}
```

5. Pulsa **Save** y después **Test**.

Resultado esperado:
```json
{
  "statusCode": 200,
  "body": "{\"message\": \"OK\", \"id\": \"<uuid>\"}"
}
```

### 7.2 Verificar en DynamoDB

1. Ve a **DynamoDB → Tables → airport-results → Explore table items**.
2. Deberías ver un ítem nuevo con el phase, parameters, result, timestamp y username del test.

### 7.3 Probar GET desde el navegador

Abre esta URL directamente en el navegador (sustituye con tu URL real):
```
https://<id>.lambda-url.<region>.on.aws/
```

Debe devolver un array JSON con el ítem que insertaste en el test.

---

## Paso 8 — Actualizar el web viewer con la URL real

### 8.1 Editar index.html

Abre `web-viewer/index.html` y localiza la línea 113:

```javascript
const API_URL = 'https://REPLACE_WITH_API_GATEWAY_URL/results';
```

Reemplázala con la Function URL del Paso 6 **sin `/results` al final**
(la Lambda responde en la raíz `/`):

```javascript
const API_URL = 'https://<id>.lambda-url.<region>.on.aws/';
```

### 8.2 Guardar y hacer commit

```bash
git add web-viewer/index.html
git commit -m "Connect web viewer to deployed Lambda Function URL"
git push origin main
```

### 8.3 Publicar en GitHub Pages

1. En GitHub, ve a tu repositorio → pestaña **Actions**.
2. En el panel izquierdo, selecciona el workflow **"Deploy web-viewer to GitHub Pages"**.
3. Pulsa **Run workflow → Run workflow**.
4. Espera 1–2 minutos a que el workflow termine (estado verde ✓).

URL pública del viewer: `https://crest4s.github.io/airport-data-cloud-viewer/`

---

## Paso 9 — Configurar la app Scala para usar el cloud

La app Scala (`scala-cloud/`) usa la Function URL a través de la variable de
entorno `CLOUD_API_URL`.

### Opción A — Variable de entorno (recomendada)

Antes de ejecutar la app, define la variable en tu terminal:

```bash
# macOS / Linux
export CLOUD_API_URL="https://<id>.lambda-url.<region>.on.aws/"

# Windows PowerShell
$env:CLOUD_API_URL = "https://<id>.lambda-url.<region>.on.aws/"
```

### Opción B — Hardcodear en el código

Si prefieres no usar variables de entorno, abre
`scala-cloud/src/main/scala/cloud/CloudClient.scala` y localiza la línea donde
se lee `CLOUD_API_URL`, sustitúyela por la URL directamente.

---

## Resumen de valores finales

Completa esta tabla cuando termines el despliegue:

| Variable | Valor |
|---|---|
| Región AWS | `eu-west-1` *(o la que hayas elegido)* |
| Nombre tabla DynamoDB | `airport-results` |
| Nombre función Lambda | `AirportResultsFunction` |
| Rol IAM | `AirportResultsFunction-role` |
| Function URL | `https://__________.lambda-url._______.on.aws/` |
| Web viewer URL | `https://crest4s.github.io/airport-data-cloud-viewer/` |

---

## Solución de problemas frecuentes

### La Lambda devuelve error 500

- Ve a **CloudWatch → Log groups → /aws/lambda/AirportResultsFunction**
- Lee el último log stream para ver el traceback de Python
- Causa más común: la variable `TABLE_NAME` no está configurada o el nombre de la tabla no coincide exactamente

### El web viewer no carga datos (pantalla vacía)

- Abre las DevTools del navegador (F12) → pestaña **Console**
- Si ves un error CORS: verifica que la Function URL tiene configurado `Allow origin: *`
- Si ves un error de red: verifica que `API_URL` en `index.html` apunta a la URL correcta

### La app Scala no se conecta al cloud

- Verifica que `CLOUD_API_URL` termina en `/` (sin `/results`)
- Ejecuta un test manual con `curl`:
  ```bash
  curl -X POST "$CLOUD_API_URL" \
    -H "Content-Type: application/json" \
    -d '{"phase":"Test","parameters":"n/a","result":"ok","timestamp":"2026-05-08T00:00:00Z","username":"test"}'
  ```
- Respuesta esperada: `{"message": "OK", "id": "<uuid>"}`

### El test de Lambda da error de permisos DynamoDB

- Ve a **IAM → Roles → AirportResultsFunction-role**
- Verifica que tiene adjuntada la política `AmazonDynamoDBFullAccess`
- Si no aparece, vuelve al Paso 2 y adjunta la política manualmente

---

## Política IAM restrictiva (alternativa a AmazonDynamoDBFullAccess)

Si quieres seguir el principio de mínimo privilegio (como hace SAM con
`DynamoDBCrudPolicy`), en lugar de `AmazonDynamoDBFullAccess` crea una
política inline en el rol con este JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DynamoDBCrudAirportResults",
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Scan",
        "dynamodb:Query",
        "dynamodb:BatchGetItem",
        "dynamodb:BatchWriteItem",
        "dynamodb:DescribeTable"
      ],
      "Resource": "arn:aws:dynamodb:<REGION>:<ACCOUNT-ID>:table/airport-results"
    }
  ]
}
```

Sustituye `<REGION>` y `<ACCOUNT-ID>` con los valores de tu cuenta.
El ARN completo de la tabla aparece en **DynamoDB → Tables → airport-results → Overview → ARN**.
