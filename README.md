# 📞 VoiceAIAgentPro

[![Tech Stack](https://img.shields.io/badge/Tech_Stack-Spring_Boot_+_React-6db33f?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Language](https://img.shields.io/badge/Language-Java_+_TypeScript-blue?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

An enterprise-grade, intelligent Voice AI Agent platform designed to automate appointment scheduling, customer inquiries, and data collection. The system is split into a modular Spring Boot Java backend state engine and a React/TypeScript/Vite single-page web interface.

---

## 🚀 Key Features

* **Continuous Voice Interaction:** Talk naturally without clicking. The system automatically detects silence, compiles your message, and plays back receptionist responses.
* **State-Machine Controlled Booking Engine:** Manages complex conversational state transitions (Booking, Confirmation, Post-Confirm, Reschedule) deterministically.
* **Fast Path Override Rules:** Safe heuristics prevent infinite conversational loops and force-accept slot details if repetitive questioning occurs.
* **Hybrid Multilingual Support:** Accepts and normalizes inputs across English, Hindi, Hinglish, and Bengali scripts.
* **Integration Ecosystem:** Powered by **Sarvam AI APIs** for STT/TTS and **Groq (Llama 3.1 8B)** for reliable, structured JSON entity extraction.

---

## 📂 Repository Directory Layout

We organize the codebase into dedicated, clean workspaces:

```
├── backend/               # Spring Boot Application (Java 17, Maven)
│   ├── src/               # Controller, DTO, Model, and Service classes
│   ├── Dockerfile         # Dockerized deploy configuration
│   └── pom.xml            # Maven dependency management
│
├── frontend/              # Single Page Application (React 18, Vite, TS)
│   ├── src/               # Component libraries, pages, hooks, and styles
│   ├── public/            # Assets and site metadata
│   ├── vite.config.ts     # Vite compilation settings
│   └── package.json       # Node package manager configurations
│
├── documentation/         # Professional Architecture & Guides
│   ├── architecture.md    # System design diagrams, sequence logic, and overrides
│   ├── setup-guide.md     # Installation guidelines and local launch steps
│   ├── work-log.md        # Daily progress log and project tracking log
│   ├── doc-placeholder-1.md # Placeholder for first PDF reference content
│   ├── doc-placeholder-2.md # Placeholder for second PDF reference content
│   └── test_intent_flow.txt # Sample manual test cases for voice queries
│
└── README.md              # Global repository entry point (This file)
```

---

## ⚙️ Quick Start

To run the application locally, follow these simplified steps. For more granular details, see the [Local Setup Guide](file:///Users/kalyansadhukhan/agentic-ai-world-voice/documentation/setup-guide.md).

### 1. Launch the Backend
1. Enter the backend folder:
   ```bash
   cd backend
   ```
2. Create your `.env` configuration file:
   ```bash
   cp .env.example .env
   ```
   Add your `SARVAM_API_KEY` and `GROQ_API_KEY`.
3. Start the Spring application:
   ```bash
   mvn spring-boot:run
   ```
   *The backend will run on `http://localhost:8081`.*

### 2. Launch the Frontend
1. Open a new terminal tab and enter the frontend folder:
   ```bash
   cd frontend
   ```
2. Install the node modules:
   ```bash
   npm install
   ```
3. Start the dev server:
   ```bash
   npm run dev
   ```
   *The frontend will run on `http://localhost:8080` (or `http://localhost:5173`). Open the link in your browser to try the Continuous Voice AI demo!*

---

## 📊 Project Management

Track engineering progress, decisions, blockers, and daily reports through the `docs/` system:

| Document | Purpose |
|---|---|
| [Dashboard](docs/dashboard.md) | Single source of truth — phase, progress, sprint goals, blockers |
| [Daily Reports](docs/reports/) | Per-day engineering activity logs |
| [Weekly Summaries](docs/weekly/) | Weekly accomplishments, risks, and goals |
| [Decisions Log](docs/decisions.md) | All architectural and technical decisions (ADRs) |
| [Blockers Log](docs/blockers.md) | Open and resolved blockers with severity and owner |

---

## 📘 Comprehensive Architecture

For a deep dive into how speech is converted, how inputs are validated, and the inner workings of our failsafe transition machine, please review the **[Architecture Documentation](file:///Users/kalyansadhukhan/agentic-ai-world-voice/documentation/architecture.md)**.

---

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
