DocumentETL is a Java + Python project for document ingestion, chunking, embedding, retrieval, and RAG evaluation.  
It combines a Spring Boot ETL service with a Python RAGAS evaluation workflow and MLflow tracking.

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Maven
- PostgreSQL + PGVector
- Apache Kafka
- LangChain4j + Vertex AI Gemini
- MLflow
- Python (RAGAS evaluation)

## Project Structure

- `src/main/java/...` - Spring Boot app (ETL, retrieval, orchestration, APIs)
- `src/main/resources/` - app config and source documents
- `python_eval/` - RAGAS evaluation pipeline and trace bridge tools
- `docker/` - Docker assets (including MLflow Dockerfile)
- `ecs/` - ECS task definitions
- `database_setup_and_upgrades.sql` - DB setup/update SQL

## Prerequisites

- JDK 21
- Maven (or use `./mvnw`)
- Python 3.10+ (for evaluation scripts)
- PostgreSQL (with vector support)
- Kafka
- Access to Vertex AI credentials (or Gemini API key)
- MLflow server

## Run the Application

```bash
./mvnw spring-boot:run
On Windows PowerShell:
.\mvnw.cmd spring-boot:run
Run Python Evaluation
Install dependencies:
pip install -r python_eval\requirements.txt
Run evaluation (example):
python python_eval\eval_ragas.py --limit 5
Environment Variables (Common)
•
SPRING_DATASOURCE_URL
•
SPRING_DATASOURCE_USERNAME
•
SPRING_DATASOURCE_PASSWORD
•
SPRING_KAFKA_BOOTSTRAP_SERVERS
•
MLFLOW_TRACKING_URI
•
VERTEX_PROJECT_ID
•
VERTEX_LOCATION
•
SPRING_CLOUD_GCP_CREDENTIALS_LOCATION or GOOGLE_APPLICATION_CREDENTIALS
•
GOOGLE_API_KEY (if using API key mode for evaluation)
Docker Services
docker-compose.yml includes supporting services such as:
•
mlflow_server
•
floci (local cloud emulator)
Start:
docker compose up -d
Security Notes
•
Do not commit secrets (.env, service account keys, passwords).
•
Keep credentials in environment variables or local secret stores.
•
Sensitive/local runtime files are intentionally gitignored.
License
No license is currently applied.
