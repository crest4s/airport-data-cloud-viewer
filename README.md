# airport-data-cloud-viewer

Functional Scala analysis of the US Airline dataset (about 1.2 million flights, 2018–2020), with an AWS serverless API that stores the results and a web viewer to browse them.

Lab project (PL2) for the *Paradigmas Avanzados de Programación* (Advanced Programming Paradigms) course at the University of Alcalá (UAH), 2025–26 academic year. The four analysis phases are the same ones implemented with CUDA in [cuda-flight-delay-analysis](https://github.com/crest4s/cuda-flight-delay-analysis), rewritten here in a purely functional style.

## How it works

```
Scala console app ──POST──▶ AWS Lambda (Function URL) ──▶ DynamoDB
                                     ▲
web-viewer (static page) ──GET / DELETE──┘
```

### Scala application

An interactive console menu loads the CSV and runs one of four phases:

| Phase | What it computes |
|-------|------------------|
| 01 – Departure delay | Flights whose `DEP_DELAY` is ≥ a threshold (or ≤ it, for negative thresholds) |
| 02 – Arrival delay | Same with `ARR_DELAY`, plus the number of matching flights |
| 03 – Delay reduction | Minimum or maximum of a delay column in a single tail-recursive pass |
| 04 – Airport histogram | Occurrences of each origin or destination airport above a threshold, printed as a console histogram |

The code follows strict functional rules: immutable `val`s only, `List` as the only collection, tail recursion (`@tailrec`) for everything that walks the 1.2M rows, and hand-written list utilities (`utils/ListUtils.scala`) instead of library helpers.

After each phase the program can upload a summary and the first 20 results to the cloud API, tagged with a user name.

- `scala-app/` — standalone version of the analysis.
- `scala-cloud/` — same analysis plus the HTTP client (`cloud/CloudClient.scala`) that sends results to the API.

### Cloud API (`cloud-api/`)

- `lambda/handler.py` — Python 3.12 Lambda that handles `POST` (store a result), `GET` (list results, optionally filtered by phase) and `DELETE` (by id), backed by a DynamoDB table (`airport-results`). Unit tests with pytest in `lambda/test_handler.py`.
- `DEPLOYMENT.md` — step-by-step deployment from the AWS console (Spanish): Lambda with a public Function URL (CORS enabled) and an on-demand DynamoDB table.

> [!WARNING]
> **Lab scope, not production-ready.** The Lambda Function URL is public and unauthenticated (`AuthType: NONE`), and it accepts `DELETE` requests, so anyone who knows the URL can read, add or delete results. That was acceptable for a short-lived course demo, but a real deployment should require authentication (for example IAM auth on the Function URL, or API Gateway with API keys or Cognito), restrict CORS to the viewer's origin and drop or protect the `DELETE` route.

### Web viewer (`web-viewer/index.html`)

Single static page that lists the uploaded results with search, filters by phase, pagination, detail view, CSV export, auto-refresh, record deletion and light/dark theme. It can be served from any static host.

## Running it

### Dataset

Download the US Airline dataset (Kaggle) as a CSV and place it at `scala-app/data/Airline_dataset.csv` (or `scala-cloud/data/`). CSV files are git-ignored. The program asks for the path at start-up.

### Scala app

Requirements: JDK and Scala 2.13. The projects were developed with IntelliJ IDEA (open `scala-app/` or `scala-cloud/` and run `Main`). From the command line:

```bash
cd scala-cloud
scalac -d out $(find src -name "*.scala")
scala -cp out Main
```

### API

Deploy the Lambda and the DynamoDB table following [`cloud-api/DEPLOYMENT.md`](cloud-api/DEPLOYMENT.md), then set your Function URL as `API_URL` in `scala-cloud/src/main/scala/cloud/CloudClient.scala` and `web-viewer/index.html` (both contain a placeholder).

Lambda tests:

```bash
cd cloud-api/lambda
pip install boto3 pytest
pytest
```

## Authors

- Adrián Morales Rodríguez ([@crest4s](https://github.com/crest4s))
- [@BCA-Lucas](https://github.com/BCA-Lucas)

## License

[MIT](LICENSE)
