# Movie Catalog: common tasks. Run `make` or `make help` to list them.
# Needs JDK 21, Node 22, pnpm and Python 3 (`make doctor` checks them). Gradle is used through ./gradlew.
# Targets only call the scripts and commands documented in the README; nothing here hides a failure.

.DEFAULT_GOAL := help
SHELL := /bin/bash
GRADLE := ./gradlew --console=plain
FRONTEND := cd frontend &&

# Homebrew's directory, when `make setup` had to install it and the user's shell does not have it yet.
TOOLS_PATH := $(shell cat data/tools.path 2>/dev/null)
ifneq ($(TOOLS_PATH),)
export PATH := $(TOOLS_PATH):$(PATH)
endif

.PHONY: help wrapper doctor setup setup-frontend build test streaming backend-test frontend-check frontend-test frontend-build \
        verify quality format dev dev-check dev-stop seed e2e e2e-mocked perf traceability secrets \
        docker-config docker-up docker-down clean

help: ## List the targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  \033[1m%-16s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# ---- setup ----------------------------------------------------------------------------------------------------------

wrapper: ## Create the Gradle wrapper once (needs a local Gradle 9.8 or IntelliJ)
	gradle wrapper --gradle-version 9.8.2

doctor: ## Check the required tools (JDK 21, Node 22, pnpm, Python 3) without installing anything
	bash scripts/doctor.sh

# chmod first: a zip download or some file systems drop the executable bit.
# The second make run re-reads data/tools.path, which doctor.sh writes when it installs Homebrew.
setup: ## Install missing tools (asks first), then frontend packages, the Playwright browser and the git hooks
	chmod +x gradlew scripts/*.sh scripts/hooks/*
	bash scripts/doctor.sh --install
	$(MAKE) setup-frontend

setup-frontend:
	$(FRONTEND) pnpm install
	$(FRONTEND) pnpm exec playwright install chromium
	scripts/install-hooks.sh

# ---- build and test -------------------------------------------------------------------------------------------------

build: ## Build backend and frontend (runs the backend tests too)
	$(GRADLE) build
	$(FRONTEND) pnpm build

test: backend-test frontend-test ## Run all unit and integration tests

backend-test: ## Backend tests (all modules), including the memory-bounded streaming test
	$(GRADLE) test :movie-service:streamingTest

streaming: ## Only the artwork streaming test (runs with a 128 MB heap)
	$(GRADLE) :movie-service:streamingTest

frontend-check: ## Type check and lint the frontend
	$(FRONTEND) pnpm check && pnpm lint

frontend-test: ## Frontend unit and component tests with the coverage gate
	$(FRONTEND) pnpm test:coverage

frontend-build: ## Static frontend build
	$(FRONTEND) pnpm build

verify: ## Every gate that applies (repository checks, backend, frontend); writes reports/verify-*.log
	scripts/verify.sh

traceability: ## Requirement to story to test traceability check
	python3 -I scripts/check_traceability.py

secrets: ## Scan the working tree for secrets
	python3 -I scripts/secret_scan.py

quality: ## ktlint, detekt and Kover coverage gate (opt-in init script; versions unverified, see gradle/quality.init.gradle.kts)
	$(GRADLE) -I gradle/quality.init.gradle.kts ktlintCheck detekt koverXmlReport
	python3 -I scripts/check_coverage.py

format: ## Fix Kotlin formatting with ktlint, and frontend formatting with Prettier
	$(GRADLE) -I gradle/quality.init.gradle.kts ktlintFormat
	$(FRONTEND) pnpm exec prettier --write .

# ---- run ------------------------------------------------------------------------------------------------------------

dev: ## Start the three services and the Vite dev server and show progress until all four are up (http://localhost:5173)
	scripts/dev.sh

dev-check: ## Wait until all four parts answer
	scripts/dev.sh --check

dev-stop: ## Stop what `make dev` started
	scripts/dev.sh --stop

seed: ## Load demo data into the running stack (safe to run twice)
	scripts/seed.sh

# ---- browser tests and performance ----------------------------------------------------------------------------------

e2e-mocked: ## Layout and accessibility browser tests against a mocked API (no backend needed)
	$(FRONTEND) pnpm exec playwright test --project=mocked

e2e: ## Journeys against the real stack on a fresh temporary data directory
	scripts/e2e.sh

perf: ## Load 1,000 movies and people, time the API, write reports/perf.md (stack must be running)
	node scripts/perf/perf.mjs

# ---- containers (optional, not verified) ------------------------------------------------------------------------------

docker-config: ## Check that compose.yaml is valid
	docker compose config -q

docker-up: ## Build and start everything in containers (http://localhost:8088)
	docker compose up --build

docker-down: ## Stop the containers (add `-v` by hand to delete their data)
	docker compose down

# ---- cleanup --------------------------------------------------------------------------------------------------------

clean: ## Remove build output (keeps ./data and ./reports)
	$(GRADLE) clean
	rm -rf frontend/build frontend/.svelte-kit frontend/coverage frontend/test-results
