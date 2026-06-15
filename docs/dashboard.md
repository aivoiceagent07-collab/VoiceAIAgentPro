# VoiceAIAgentPro — Project Dashboard

> **Single source of truth.** Updated with each significant development session.
> Last Updated: **2026-06-15**

---

## Project Overview

| Field | Detail |
|---|---|
| **Product** | VoiceAIAgentPro — Enterprise AI Voice Agent Platform |
| **Domain** | voiceaiagentpro.ai |
| **Stack** | Spring Boot 3.2.4 (Java 17) · React 18 + TypeScript + Vite |
| **AI Stack** | Sarvam AI (STT/TTS) · Groq Llama 3.1-8B (NLU/Entity Extraction) |
| **Email** | Resend API (transactional) · Domain: voiceaiagentpro.ai |
| **Deployment** | Docker (backend) · Vite SPA (frontend) · Render-ready |
| **License** | MIT |

---

## Current Development Phase

```
Phase 1: Decoupling & Refactoring          ████████████  COMPLETE
Phase 2: Repository Reorganization         ████████████  COMPLETE
Phase 3: Documentation & Tracking Setup    ████████████  COMPLETE
Phase 4: Contact & Email Infrastructure    ███████░░░░░  IN PROGRESS
Phase 5: Production Deployment & CI/CD     ░░░░░░░░░░░░  NOT STARTED
Phase 6: Multi-tenant / Scale              ░░░░░░░░░░░░  NOT STARTED
```

---

## Overall Progress

| Module | Status | Notes |
|---|---|---|
| Voice State Machine (`VoiceService`) | ✅ Complete | 4-mode FSM: Booking, Confirmation, Post-Confirm, Reschedule |
| STT Integration (Sarvam AI) | ✅ Complete | With retry + placeholder rejection |
| TTS Integration (Sarvam AI) | ✅ Complete | Pre-caching enabled |
| LLM Entity Extraction (Groq) | ✅ Complete | Llama 3.1-8b-instant, JSON mode |
| Deterministic Extraction Layer | ✅ Complete | Pre-LLM bypass for common patterns |
| Department Routing Service | ✅ Complete | 3-layer: exact → keyword → semantic |
| Clinic Schedule Config | ✅ Complete | Doctor-day-slot mapping |
| Input Validation | ✅ Complete | Field-aware validation |
| Date / Time Normalization | ✅ Complete | Hindi weekday + time parsing |
| Session Management | ✅ Complete | In-memory session store |
| Performance Metrics Service | ✅ Complete | Per-request STT/TTS/LLM latency tracking |
| TTS Pre-Cacher | ✅ Complete | Startup warm-up for common responses |
| Contact Form API (`ContactController`) | ✅ Complete | Simple + Enhanced form endpoints |
| Email Service (Resend + SMTP fallback) | ✅ Complete | Multi-receiver, custom domain sender |
| Frontend Landing Page | ✅ Complete | React/TS, hero, features, ROI, contact |
| Voice Demo Modal | ✅ Complete | Browser mic → STT → TTS loop |
| Enhanced Inquiry Form | ✅ Complete | Agent-type selection, primary goal |
| Docker Build (Backend) | ✅ Complete | Multi-stage, Render-compatible |
| Domain & Email Sender Config | ✅ Complete | voiceaiagentpro.ai verified on Resend |
| CI/CD Pipeline | ❌ Not Started | No GitHub Actions yet |
| Unit / Integration Tests | ❌ Not Started | Maven Surefire configured, no tests written |
| Database Layer | ❌ Not Started | In-memory sessions only |
| Auth / Admin Panel | ❌ Not Started | |

---

## Active Sprint Goals  *(Week of 2026-06-09 → 2026-06-15)*

- [x] Set up Resend transactional email for contact form
- [x] Add multi-receiver email support (comma-separated `RECEIVER_EMAIL`)
- [x] Configure custom sender domain (`admin@voiceagentaipro.ai`)
- [x] Establish `docs/` project tracking system
- [ ] Write first passing integration test for `VoiceService`
- [ ] Configure GitHub Actions for backend build on push

---

## Current Blockers

| # | Blocker | Severity | Owner |
|---|---|---|---|
| B-01 | No automated tests — regressions are caught manually only | Medium | Dev Team |
| B-02 | In-memory sessions lost on server restart; no persistence | Medium | Dev Team |
| B-03 | `RESEND_API_KEY` not yet set in production `.env` | High | DevOps |

> See full details → [blockers.md](./blockers.md)

---

## Recent Updates

| Date | Update |
|---|---|
| 2026-06-15 | Established `docs/` project management system |
| 2026-06-15 | Configured `SENDER_EMAIL` via Resend verified domain |
| 2026-06-15 | Added multi-receiver email (comma-separated `RECEIVER_EMAIL`) |
| 2026-06-09 | Migrated frontend to `/frontend`, reorganized repo structure |
| 2026-06-09 | Authored `architecture.md` and `setup-guide.md` |
| 2026-06-08 | Decoupled backend FSM services; integrated Sarvam + Groq |

---

## Quick Navigation

| Document | Purpose |
|---|---|
| [decisions.md](./decisions.md) | Architectural & technical decision log |
| [blockers.md](./blockers.md) | Open and resolved blockers |
| [reports/](./reports/) | Daily engineering reports |
| [weekly/](./weekly/) | Weekly summary reports |
| [../documentation/architecture.md](../documentation/architecture.md) | System design & state machine diagrams |
| [../documentation/setup-guide.md](../documentation/setup-guide.md) | Local development setup |
| [../documentation/work-log.md](../documentation/work-log.md) | Legacy daily work log |

---

## Service URLs (Local Development)

| Service | URL |
|---|---|
| Backend API | http://localhost:8081 |
| Frontend | http://localhost:8080 or http://localhost:5173 |
| Voice endpoint | POST http://localhost:8081/api/voice |
| Contact endpoint | POST http://localhost:8081/api/contact |
