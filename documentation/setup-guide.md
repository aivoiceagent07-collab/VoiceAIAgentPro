# Local Setup Guide

This guide details the step-by-step instructions to configure and run the frontend and backend of **VoiceAIAgentPro** locally.

---

## 🛠 Prerequisites

Before starting, ensure you have the following installed on your machine:
1. **Java Development Kit (JDK 17)** or higher.
2. **Node.js** (v18 or higher) & **npm** (or **Bun** package manager).
3. **Maven** (optional, as the backend includes a Maven wrapper `./mvnw`).

---

## 💾 Backend Configuration

The backend is a Spring Boot application running on Java.

### 1. Configure Environment Variables
Navigate to the `backend/` directory:
```bash
cd backend
```

Duplicate the sample `.env.example` file:
```bash
cp .env.example .env
```

Open the newly created `.env` file and populate it with the appropriate values:
```properties
# Voice AI APIs
SARVAM_API_KEY=your_sarvam_ai_api_key
GROQ_API_KEY=your_groq_cloud_api_key

# Contact Email SMTP Configurations (Optional)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
EMAIL_USER=your_gmail_address@gmail.com
EMAIL_PASS=your_google_app_password
RECEIVER_EMAIL=your_receiver_email@domain.com
```

### 2. Build and Run the Backend
Execute the Maven wrapper to clean, compile, and run the Spring Boot server:
```bash
# On macOS / Linux:
./mvnw spring-boot:run

# On Windows:
mvnw.cmd spring-boot:run
```
The server will boot and listen for HTTP traffic on **`http://localhost:8081`**.

---

## 💻 Frontend Configuration

The frontend is a Vite + React + TypeScript single-page application.

### 1. Install Dependencies
Navigate to the `frontend/` directory:
```bash
cd frontend
```

Install the packages using npm or Bun:
```bash
npm install
# or if using Bun
bun install
```

### 2. Configure Environment Variables
By default, the frontend will connect to `http://localhost:8081`. If your backend is running on a different port or server, create a `.env.local` file inside the `frontend/` directory:
```properties
VITE_API_URL=http://localhost:8081
```

### 3. Run the Development Server
Launch the local development server:
```bash
npm run dev
# or if using Bun
bun run dev
```
The development server will startup, usually on **`http://localhost:8080`** or **`http://localhost:5173`** (Vite default). You can click the terminal link to open the app in your browser.

---

## 🔍 Troubleshooting

### 1. API Keys Missing Error
If the browser console or backend terminal prints an error regarding missing keys:
- Double check that the `.env` file is stored directly in `/backend` (not `/backend/src` or project root).
- Ensure there are no spaces around the keys in the `.env` file (e.g., `SARVAM_API_KEY=mykey` instead of `SARVAM_API_KEY = mykey`).

### 2. Microphone Access Denied
The continuous voice demo requires microphone access. If the page displays a microphone error:
- Ensure you are running the frontend on `localhost` or a secure `https://` domain. Most browsers block microphone access on insecure external HTTP endpoints.
- Check your browser site settings and allow microphone permissions for the application.

### 3. CORS Policy Errors
If the frontend cannot communicate with the backend:
- The backend contains a `@CrossOrigin(origins = "*")` tag on the controller. Verify that your requests are hitting the port specified in `VITE_API_URL`.
