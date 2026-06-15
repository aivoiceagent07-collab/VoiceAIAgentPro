# Architectural & Technical Decisions Log

> Decisions are recorded in reverse-chronological order (newest first).
> Each decision is permanent — never deleted, only superseded.

---

## Decision Index

| ID | Date | Title | Status |
|---|---|---|---|
| [ADR-006](#adr-006) | 2026-06-15 | Use Resend verified domain for transactional email | Accepted |
| [ADR-005](#adr-005) | 2026-06-15 | Support multiple email receivers via comma-separated env var | Accepted |
| [ADR-004](#adr-004) | 2026-06-09 | Monorepo structure with `/frontend` and `/backend` sub-projects | Accepted |
| [ADR-003](#adr-003) | 2026-06-08 | Use Groq (Llama 3.1-8B) for entity extraction in JSON mode | Accepted |
| [ADR-002](#adr-002) | 2026-06-08 | Implement deterministic pre-LLM extraction bypass layer | Accepted |
| [ADR-001](#adr-001) | 2026-06-08 | Finite State Machine (4-mode) for voice conversation control | Accepted |

---

## ADR-006

**Use Resend Verified Domain for Transactional Email**

| Field | Detail |
|---|---|
| **Date** | 2026-06-15 |
| **Status** | Accepted |
| **Owner** | DevOps / Dev Team |

**Context**  
The contact form needed to send emails from a branded domain address (`admin@voiceagentaipro.ai`) rather than the Resend sandbox default (`onboarding@resend.dev`). Sending from the sandbox address restricts delivery to only the account owner's verified email.

**Decision**  
Verify domain `voiceaiagentpro.ai` on Resend and configure `SENDER_EMAIL=admin@voiceagentaipro.ai` as an environment variable. The `EmailService` reads `${SENDER_EMAIL:${spring.mail.username:}}` allowing independent control of sender identity from SMTP credentials.

**Alternatives Considered**
- Sandbox default (`onboarding@resend.dev`) — rejected: delivery restricted to owner inbox only
- Gmail SMTP (`spring.mail.username`) — rejected: not branded, requires app password management

**Rationale**  
Branded sender address builds trust with recipients; Resend domain verification is a one-time DNS operation that unlocks delivery to any recipient globally.

**Expected Impact**  
All contact form emails now arrive from `admin@voiceagentaipro.ai`, increasing open rates and deliverability.

---

## ADR-005

**Support Multiple Email Receivers via Comma-Separated Env Var**

| Field | Detail |
|---|---|
| **Date** | 2026-06-15 |
| **Status** | Accepted |
| **Owner** | Dev Team |

**Context**  
Contact form submissions originally went to a single receiver. As the team grows, multiple stakeholders (sales, management) need to receive leads simultaneously.

**Decision**  
Replace `String toEmail` with a `String receiverEmailRaw` field in `EmailService`, parsed at runtime by `getReceiverEmails()` into a `List<String>`. The env var `RECEIVER_EMAIL` accepts comma-separated addresses (e.g., `a@x.com,b@y.com`). Both the Resend `"to"` array and SMTP `setTo(String[])` are updated to use the full list.

**Alternatives Considered**
- Multiple env vars (`RECEIVER_EMAIL_1`, `RECEIVER_EMAIL_2`) — rejected: not scalable, requires code changes to add more
- Database-driven recipient list — rejected: overengineered for current scale

**Rationale**  
Single env var is operator-friendly and zero-code to change recipients. Resend natively accepts an array in the `"to"` field.

**Expected Impact**  
Any number of recipients can be configured without code changes.

---

## ADR-004

**Monorepo Structure with `/frontend` and `/backend` Sub-projects**

| Field | Detail |
|---|---|
| **Date** | 2026-06-09 |
| **Status** | Accepted |
| **Owner** | Dev Team |

**Context**  
The project began with the React frontend code mixed at the repository root. This made CI/CD configuration, Docker builds, and contributor onboarding confusing.

**Decision**  
Migrate all frontend files to `/frontend` and all Spring Boot files to `/backend` using `git mv` to preserve commit history. Maintain a shared root `README.md` as the project entry point.

**Alternatives Considered**
- Separate repositories (polyrepo) — rejected: increases coordination overhead at this stage
- Keep mixed root — rejected: pollutes Docker context, complicates CORS and proxy config

**Rationale**  
Monorepo simplifies shared issue tracking, linked PRs, and single-clone onboarding while directory-level separation gives each sub-project its own `Dockerfile`, `package.json`, `pom.xml`.

**Expected Impact**  
Cleaner CI/CD targeting, contributor clarity, independent deployability.

---

## ADR-003

**Use Groq (Llama 3.1-8B-Instant) for NLU Entity Extraction**

| Field | Detail |
|---|---|
| **Date** | 2026-06-08 |
| **Status** | Accepted |
| **Owner** | Dev Team |

**Context**  
The voice agent needs to extract structured entities (name, department, date, time, intent) from free-form Hindi/Hinglish speech transcriptions. A reliable, low-latency LLM call in JSON mode is required.

**Decision**  
Use Groq API with the `llama-3.1-8b-instant` model, prompting in JSON mode for structured slot hydration. Groq's inference speed (~250ms p50) is acceptable for real-time voice.

**Alternatives Considered**
- OpenAI GPT-4o — rejected: higher cost, latency, no JSON mode speed advantage at this scale
- Rule-only parsing — rejected: insufficient for open-ended user utterances
- Smaller local model — rejected: infrastructure complexity, quality risk for multilingual inputs

**Rationale**  
Groq offers competitive inference speed and the 8B model is sufficient for structured slot filling with a tightly scoped prompt. Cost is minimal per request.

**Expected Impact**  
Sub-300ms LLM extraction enabling real-time voice turn-around with Sarvam TTS.

---

## ADR-002

**Implement Deterministic Pre-LLM Extraction Bypass Layer**

| Field | Detail |
|---|---|
| **Date** | 2026-06-08 |
| **Status** | Accepted |
| **Owner** | Dev Team |

**Context**  
LLM calls add 200–400ms latency per turn. For simple, predictable inputs (e.g., "हाँ", "नहीं", a single weekday name, a time like "3 बजे") the LLM is wasteful.

**Decision**  
Implement `DeterministicExtractionService` that runs before the Groq call. If it confidently extracts the required entity for the current session state, the LLM call is skipped entirely. The service uses regex, keyword matching, and known value lists.

**Alternatives Considered**
- Always call LLM — rejected: unnecessary latency and API cost for trivial inputs
- Cache LLM responses — rejected: inputs are session-specific, caching hit rate would be low

**Rationale**  
Common utterances in a constrained booking flow are highly predictable. Pre-LLM extraction reduces average turn latency by ~40% for the most frequent patterns.

**Expected Impact**  
Faster voice responses, lower Groq API costs, more deterministic behavior.

---

## ADR-001

**4-Mode Finite State Machine for Voice Conversation Control**

| Field | Detail |
|---|---|
| **Date** | 2026-06-08 |
| **Status** | Accepted |
| **Owner** | Dev Team |

**Context**  
Voice-based appointment booking requires strict control over conversational state to prevent infinite loops, incorrect slot overwriting, and premature confirmations.

**Decision**  
Implement `SessionState.Mode` as a 4-state FSM: `BOOKING → CONFIRMATION → POST_CONFIRM` (with a `RESCHEDULE` branch from `POST_CONFIRM`). All slot hydration and intent routing decisions are gated by the current mode.

**Alternatives Considered**
- Free-form LLM-managed dialogue — rejected: non-deterministic, prone to hallucinated slot values
- Simple intent classifier without state — rejected: cannot handle multi-turn booking correctly

**Rationale**  
Deterministic state transitions eliminate ambiguity, enable Fast Path Override rules (repeat-count heuristic, loop prevention), and make the system auditable and debuggable.

**Expected Impact**  
Reliable appointment booking flow; known behavior for all edge cases (double-booking, reschedule mid-confirmation, out-of-scope queries).
