# AI-Helper Project Context (for LLM Handoff)

## 1. Project Overview

- Project name: `ai-helper`
- Architecture: Maven multi-module microservices
- Language/runtime: Java 17, Spring Boot 3.5.13
- Goal: build an AI-assistant system split into independent services:
  - a LangChain4j-based code assistant service
  - a Spring AI-based general agent host service
  - a gateway service for unified routing/forwarding
  - a shared `ai-common` module for cross-service reusable code

This document is intended as a fast handoff context for another LLM.

## 2. Current Module Structure

```text
ai-helper/                          (parent, packaging=pom)
  ai-common/                        (shared library)
  ai-utility-agent-service/         (LangChain4j service)
  ai-kitchen-brain-service/         (Spring AI service)
  ai-gateway/                       (Spring Cloud Gateway)
  front-show/                       (frontend-related folder, currently not in Maven reactor)
```

Parent reactor order (current):
1. `ai-helper`
2. `ai-common`
3. `ai-utility-agent-service`
4. `ai-kitchen-brain-service`
5. `ai-gateway`

## 3. Tech Stack and Version Choices

### Parent BOM / version management
- Spring Boot parent: `3.5.13`
- Spring AI BOM: `1.1.4`
- Spring Cloud BOM: `2025.0.0`
- LangChain4j:
  - stable/core: `1.13.0`
  - beta artifacts: `1.13.0-beta23`

### Module-level technologies

#### `ai-utility-agent-service`
- Spring MVC + WebFlux
- LangChain4j (DashScope, MCP, reactive stream)
- Knife4j (OpenAPI docs)
- Depends on `ai-common`

#### `ai-kitchen-brain-service`
- Spring MVC + Actuator
- Spring AI (`spring-ai-starter-model-openai`)
- Depends on `ai-common`
- Current state: scaffold/skeleton, business content still to be implemented

#### `ai-gateway`
- Spring Cloud Gateway (WebFlux server starter)
- Actuator
- Current routes:
  - `/code-assistant/**` -> `http://localhost:8081` (StripPrefix=1)
  - `/super-host/**` -> `http://localhost:8082` (StripPrefix=1)
- Has global forwarding log filter (`GatewayForwardLogFilter`)

#### `ai-common`
- Shared library (NOT an independent Spring Boot app)
- Provides:
  - common response model (`CommonResp`)
  - business exception types
  - global controller exception handler (`@ControllerAdvice`)
  - request/response logging AOP (`LogAspect`)
- Uses minimal non-starter dependencies (spring-context/spring-aop/spring-web/etc.)

## 4. Runtime Ports and Service Names

- `ai-gateway`: `8080`
- `ai-utility-agent-service`: `8081`
- `ai-kitchen-brain-service`: `8082`

Spring application names:
note: application name and module name are intentionally different.
- `ai-gateway`
- `ai-code-assistant-service`
- `ai-super-host`

## 5. Key Entry Points and API Surface

### Code assistant service
Main controller: `AiHelperController`
- `GET /aihelper/chat/{message}`
- `GET /aihelper/chatStream/{memoryId}/{message}`

Gateway access examples:
- `GET /code-assistant/aihelper/chat/{message}`
- `GET /code-assistant/aihelper/chatStream/{memoryId}/{message}`

### Gateway forwarding logs
`GatewayForwardLogFilter` prints logs like:

```text
[Gateway-Forward] GET http://localhost:8080/code-assistant/aihelper/chat/hi -> route=code-assistant-service -> target=http://localhost:8081/aihelper/chat/hi status=200 cost=34ms
```

## 6. Cross-Module Behavior Notes

### AOP in `ai-common`
- `LogAspect` pointcut: `execution(public * com.zhj..*Controller.*(..))`
- For aspect to work in services, application scan scope is set to `com.zhj.learn` in:
  - `AiHelperApplication`
  - `AiSuperHostApplication`

### Exception handling in `ai-common`
- `ControllerExceptionHandler` handles:
  - `BusinessException`
  - `MethodArgumentNotValidException`

## 7. Build / Run Instructions

### Build all modules
```bash
./mvnw clean compile
```

### Run each service
```bash
./mvnw -pl ai-utility-agent-service spring-boot:run
./mvnw -pl ai-kitchen-brain-service spring-boot:run
./mvnw -pl ai-gateway spring-boot:run
```

## 8. Important Maven Caveat (custom local repo)

If using a custom local repo (e.g. IntelliJ run config with `-Dmaven.repo.local=...`), `ai-common` may fail to resolve unless parent/module snapshots are installed in the same repo.

Typical fix sequence:

```bash
./mvnw -N install -f pom.xml
./mvnw -pl ai-common -am install -f pom.xml
./mvnw -pl ai-kitchen-brain-service -am compile -f pom.xml
```

Keep `settings.xml` and `maven.repo.local` consistent across commands/IDE.

## 9. Current Project Status

### Done
- Monolith split into Maven multi-module microservices
- `ai-gateway` created and routing configured
- `ai-kitchen-brain-service` scaffold created with Spring AI
- `ai-common` introduced and integrated
- Shared AOP + global exception handler added
- Gateway forward logging feature implemented
- Root compile currently passes

### In progress / not yet implemented
- `ai-kitchen-brain-service` business logic (agent capabilities)
- richer gateway capabilities (auth, rate-limit, etc.)
- service discovery / config center / distributed governance (if needed)

## 10. Security and Config Warning

- Sensitive API keys currently exist in local config files (e.g. `application-local.yaml`).
- Recommended action:
  - move keys to environment variables / secret manager
  - avoid committing plaintext keys

## 11. Suggested Prompt to Another LLM

When asking another LLM to continue work, include:

1. This file (`PROJECT_CONTEXT_FOR_LLM.md`)
2. The target module(s)
3. Expected output format (code patch, design doc, tests)
4. Constraints (no architecture rewrite, keep current routes, etc.)

Example:

```text
Read PROJECT_CONTEXT_FOR_LLM.md first.
Work only in ai-kitchen-brain-service.
Implement a minimal Spring AI chat endpoint with DTO + validation + unified CommonResp response.
Do not change gateway routes.
```

---

Last updated: 2026-04-12 (Asia/Shanghai)
