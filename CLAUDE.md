# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cerebro is an open-source Elasticsearch web administration tool. It provides a GUI for managing and monitoring Elasticsearch clusters.

**Tech Stack:**
- Backend: Scala 2.13.4, Play Framework 2.8.7
- Frontend: AngularJS 1.8.2, Bootstrap 3.4.1
- Database: SQLite (for REST request history)
- Build: sbt (backend), Grunt (frontend)

**Requirements:** Java 11 or newer

## Common Commands

### Backend (sbt)

```bash
# Run development server (port 9000)
sbt run

# Run with custom port
sbt "run 8080"

# Compile
sbt compile

# Run all Scala tests
sbt test

# Run a specific test class
sbt "testOnly *OverviewDataServiceSpec"

# Run tests matching a pattern
sbt "testOnly *Controller*"

# Package for distribution
sbt dist
```

### Frontend (Grunt)

```bash
# Install npm dependencies (required first)
npm install

# Build frontend (clean, lint, concat, copy, test)
grunt build

# Run frontend tests only
grunt test

# Watch for changes during development
grunt dev
```

### Full Development Workflow

```bash
# Terminal 1: Frontend build + watch
npm install && grunt dev

# Terminal 2: Backend server
sbt run
```

## Architecture

### Request Flow

All API requests follow this pattern:
1. Routes (`conf/routes`) map HTTP endpoints to controllers
2. Controllers extend `BaseController` which provides the `process()` method
3. `process()` wraps request handling with authentication and error handling
4. `CerebroRequest` parses the JSON body and resolves the target Elasticsearch server
5. Controllers use `ElasticClient` (via DI) to communicate with Elasticsearch
6. Responses are wrapped in `CerebroResponse`

### Backend Structure (`app/`)

- **`controllers/`** - HTTP endpoint handlers. Each module (aliases, snapshots, etc.) has its own controller extending `BaseController`
- **`controllers/auth/`** - Pluggable authentication (Basic Auth, LDAP). `AuthenticationModule` is the entry point
- **`elastic/`** - Elasticsearch client abstraction. `ElasticClient` is the trait, `HTTPElasticClient` is the implementation
- **`models/`** - Request/response models and ES data structures. Organized by feature (analysis, overview, snapshot, etc.)
- **`services/`** - Business logic including `OperationRestrictionService` for read-only mode
- **`dao/`** - SQLite persistence for REST request history

### Frontend Structure (`src/app/`)

- **`app.routes.js`** - AngularJS route definitions
- **`components/`** - Feature modules (overview, aliases, snapshots, etc.). Each has a controller and template
- **`shared/`** - Reusable services, filters, and directives
- Built files go to `public/js/` and `public/css/`

### Key Patterns

**Controller Pattern:**
```scala
class MyController @Inject()(val authentication: AuthenticationModule,
                              val hosts: Hosts,
                              client: ElasticClient) extends BaseController {
  def myAction = process { request =>
    client.someMethod(request.target).map(response => CerebroResponse(200, response.body))
  }
}
```

**Adding a new feature:**
1. Add route in `conf/routes`
2. Create controller in `app/controllers/`
3. Add client methods to `ElasticClient` trait and `HTTPElasticClient`
4. Create frontend component in `src/app/components/`
5. Add route in `app.routes.js`

### Configuration

- **`conf/application.conf`** - Main config: authentication, known hosts, restrictions
- **`conf/routes`** - URL routing (95 endpoints)
- Environment variables: `CEREBRO_PORT`, `CEREBRO_READ_ONLY`, `AUTH_TYPE`, `LDAP_*`, `BASIC_AUTH_*`

### Test Locations

- Scala tests: `test/` (controllers, dao, models)
- JavaScript tests: `tests/` (uses Karma/Jasmine, test files end in `.tests.js`)
