# ai-helper

Microservices structure:

- `ai-code-assistant-service`: existing LangChain4j code assistant service, default port `8081`
- `ai-super-host`: Spring AI based general agent host service skeleton, default port `8082`
- `ai-gateway`: gateway service skeleton for forwarding traffic to internal AI services, default port `8080`

## Build all modules

```bash
./mvnw clean package
```

## Run services

```bash
./mvnw -pl ai-code-assistant-service spring-boot:run
./mvnw -pl ai-super-host spring-boot:run
./mvnw -pl ai-gateway spring-boot:run
```