# Blockers Log

> Open blockers are reviewed at the start of each sprint.
> Resolved blockers are moved to the **Resolved** section with resolution notes.

---

## Open Blockers

| ID | Title | Severity | Impact | Owner | Status | Opened |
|---|---|---|---|---|---|---|
| B-01 | No automated tests — regressions caught manually | Medium | Quality / Release velocity | Dev Team | Open | 2026-06-08 |
| B-02 | In-memory session store — sessions lost on restart | Medium | Demo reliability; not production-safe | Dev Team | Open | 2026-06-08 |
| B-03 | `RESEND_API_KEY` not configured in production env | High | Contact form emails not delivered in prod | DevOps | Open | 2026-06-15 |

---

### B-01 — No Automated Tests

| Field | Detail |
|---|---|
| **Severity** | Medium |
| **Impact** | Every change to `VoiceService` (1,164 lines) or `DepartmentRoutingService` requires manual end-to-end voice testing. Risk of silent regressions in FSM logic, entity hydration, and intent routing. |
| **Owner** | Dev Team |
| **Status** | Open |

**What is needed**
- JUnit 5 unit tests for `DeterministicExtractionService`, `InputValidatorService`, `DateNormalizerService`
- Integration test for `VoiceService` using mocked Sarvam + Groq clients
- Maven Surefire is already configured (`-Dnet.bytebuddy.experimental=true`)

**Attempts / Notes**
- Maven Surefire plugin is present in `pom.xml` — no blocker on infrastructure side
- Mockito is available via `spring-boot-starter-test`

---

### B-02 — In-Memory Session Store

| Field | Detail |
|---|---|
| **Severity** | Medium |
| **Impact** | `SessionManagerService` stores all `SessionState` objects in a `ConcurrentHashMap`. A backend restart wipes all active sessions. Unacceptable for production where zero-downtime redeploys are expected. |
| **Owner** | Dev Team |
| **Status** | Open |

**What is needed**
- Evaluate Redis (Spring Data Redis) or a lightweight embedded store
- Define session TTL policy (e.g., 30 min inactivity)

**Attempts / Notes**
- No persistence library currently in `pom.xml`
- For demo/MVP, in-memory is acceptable; must be addressed before v1 production launch

---

### B-03 — RESEND_API_KEY Not in Production Environment

| Field | Detail |
|---|---|
| **Severity** | High |
| **Impact** | Contact form submissions from the live website (`voiceaiagentpro.ai`) will fail silently — no emails delivered to the team. |
| **Owner** | DevOps |
| **Status** | Open |

**What is needed**
- Obtain the Resend API key from [resend.com/api-keys](https://resend.com/api-keys)
- Set in production environment (Render dashboard → Environment Variables):
  ```
  RESEND_API_KEY=re_xxxxxxxxxxxxxxxxxxxx
  SENDER_EMAIL=admin@voiceagentaipro.ai
  RECEIVER_EMAIL=nagamanitiru@gmail.com
  ```

**Attempts / Notes**
- Local `.env` has placeholder `your_resend_api_key_here` — must be replaced
- Resend domain `voiceaiagentpro.ai` is verified ✅

---

## Resolved Blockers

| ID | Title | Resolved | Resolution |
|---|---|---|---|
| B-00 | Frontend and backend code mixed at repo root | 2026-06-09 | Migrated to `/frontend` and `/backend` using `git mv`; repo reorganized cleanly |
