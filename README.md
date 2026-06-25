# StoryPlatform - AI-Powered Epistolary & Interactive Fiction Engine

StoryPlatform is a modern, microservice-based interactive storytelling and RPG engine designed to eliminate the classic LLM context-window limitation. By leveraging a Hybrid RAG (Retrieval-Augmented Generation) architecture, Relational Database Entity Extraction, and an Autonomous Running Summary loop, the system maintains a persistent, infinite-horizon memory of stories, characters, locations, and items.

## System Architecture

The project is built as a **Monorepo** comprising two decoupled services communicating asynchronously via **Apache Kafka** and synchronously via **REST APIs**:

```
[ Frontend (React / Vue) ]
           │
           ▼ (REST API)
[ Core API (Java / Spring Boot 3.x) ] <───> [ PostgreSQL + pgvector ]
           │                                      ▲
           ▼ (Apache Kafka)                       │
[ AI Worker (Python 3.12 / FastAPI) ] ────────────┘ (Vector Embeddings & Summary Call)
```

1. **Core API (Java / Spring Boot 3.x):** The system's backbone. Manages users, core transactional story entities, relational mapping, database updates, and orchestrates the event-driven workflow.
2. **AI Worker (Python 3.12 / FastAPI):** The computational brain. Handles asynchronous long-form text generation using state-of-the-art Large Language Models (LLMs) via OpenRouter/HuggingFace and real-time synchronous embedding generation.

## Key Features & AI Memory Lifecycle

### 1. Asynchronous Story Generation Loop
To maximize throughput and decouple I/O heavy LLM processing times from the web server, story requests are pushed to an Apache Kafka pipeline (`story-tasks-topic`). The Core API frees the user immediately with a `PENDING` status. The AI Worker processes the queue and replies to `story-completed-topic` when the generation finishes.

### 2. Information Extraction & Relational Mapping (Human-in-the-Loop)
Instead of storing massive unformatted strings, the Python Worker enforces a **Structured JSON Output** layout on the LLM. It extracts key story elements on-the-fly:
* **Characters:** Names and short biographical data.
* **Locations:** Biomes, environments, or rooms introduced.
* **Items:** Key objects, weapons, or plot devices discovered.

The Core API parses this payload and distributes it across highly indexed relational tables (`characters`, `locations`, `items`) linked to the main `stories` table via dual Many-to-One constraints.

### 3. Infinite Memory via Hybrid RAG & pgvector
To resolve the amnesia (context-window wipe) common in long-running creative prompts:
* **Vector Database Storage:** Generated text blocks are embedded locally into a 384-dimensional space via Hugging Face's `all-MiniLM-L6-v2` transformer model and saved inside PostgreSQL using the **pgvector** extension.
* **Semantic Search:** Users can execute meaning-based queries across their historical lore. The Core API queries pgvector using **Cosine Distance (`<=>`)** to inject relevant past context directly back into the LLM's prompt window.

### 4. Autonomous Running Summary Engine
To prevent token-bloat as the story passes chapter 50+, the system utilizes an internal counter. Every 3 user interactions, an asynchronous background task (`SUMMARIZE_STORY`) evaluates the entire narrative chunk and compresses it into a running plot summary stored in the database, acting as a low-frequency long-term memory layer.

## 🛠️ Tech Stack

* **Backend Core:** Java 21, Spring Boot 3, Spring Data JPA, Hibernate 6
* **AI & API Middleware:** Python 3.12, FastAPI, Uvicorn, OpenAI SDK, SentenceTransformers (Hugging Face)
* **Message Broker:** Apache Kafka (KRaft mode)
* **Databases & Cache:** PostgreSQL 15+ (with `pgvector` extension), Redis 7 (Rate Limiting)
* **Project Management:** GitHub Projects (Kanban methodology)

## 🚀 Local Setup

### 1. Infrastructure
Spin up the core database, messaging, and caching instances via Docker:
```bash
docker-compose up -d
```

### 2. Core API Setup
1. Open the `core-api` directory in your IDE as a Maven project.
2. Ensure your local JDK is set to version 21.
3. Configure your environmental variables or update `src/main/resources/application.yml` with your local PostgreSQL and Kafka credentials.
4. Run the `CoreApiApplication.java` entry point.

### 3. AI Worker Setup
1. Set up the isolated virtual environment and install the pinned dependencies:
```bash
cd ai-worker
python -m venv venv

# On Windows:
.\venv\Scripts\activate

# On macOS/Linux:
source venv/bin/activate

pip install -r requirements.txt
```
2. Create a .env file in the ai-worker directory and add your OpenRouter API key
```bash
OPENROUTER_API_KEY=your_api_key_here
```
3. Run the application
```bash
uvicorn main:app --reload --port 8000
```
