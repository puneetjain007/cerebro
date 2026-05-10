Cerebro
------------
[![Docker Pulls](https://img.shields.io/docker/pulls/lmenezes/cerebro.svg)](https://hub.docker.com/r/lmenezes/cerebro)
![build](https://github.com/lmenezes/cerebro/workflows/build/badge.svg?branch=master)

cerebro is an open source(MIT License) elasticsearch web admin tool built using Scala, Play Framework, AngularJS and Bootstrap.

### Requirements

cerebro needs Java 11 or newer to run.

### Installation
- Download from [https://github.com/lmenezes/cerebro/releases](https://github.com/lmenezes/cerebro/releases)
- Extract files
- Run bin/cerebro(or bin/cerebro.bat if on Windows)
- Access on http://localhost:9000

### Chocolatey (Windows)

You can install `cerebro` using [Chocolatey](https://chocolatey.org/):

```sh
choco install cerebro-es
```

Package creates windows service ```cerebro```.
Access on http://localhost:9000

### Docker

#### Standard Version

You can find the official docker images in the official [docker hub repo](https://hub.docker.com/r/lmenezes/cerebro/).

```bash
docker pull lmenezes/cerebro:latest
docker run -p 9000:9000 lmenezes/cerebro:latest
```

Visit [cerebro-docker](https://github.com/lmenezes/cerebro-docker) for further information.

#### RBAC-Enabled Version

A version with LDAP Group-Based RBAC support is available at [puneet1jain73/cerebro-rbac](https://hub.docker.com/r/puneet1jain73/cerebro-rbac):

```bash
docker pull puneet1jain73/cerebro-rbac:latest
docker run -p 9000:9000 puneet1jain73/cerebro-rbac:latest
```

**Available tags:**
- `latest` - Latest RBAC-enabled version
- `0.9.7-rbac` - Version 0.9.7 with audit logging and proxy auth (nginx + oauth2-proxy)
- `0.9.6-rbac` - Version 0.9.6 with Bearer token API authentication
- `0.9.5-rbac` - Version 0.9.5 with OAuth/OIDC + RBAC support
- `0.9.4-rbac` - Version 0.9.4 with RBAC support

**Docker Hub Repository:** https://hub.docker.com/r/puneet1jain73/cerebro-rbac 

### Configuration

#### HTTP server address and port
You can run cerebro listening on a different host and port(defaults to 0.0.0.0:9000):

```
bin/cerebro -Dhttp.port=1234 -Dhttp.address=127.0.0.1
```

#### Authentication overview

By default, authentication is **disabled** — Cerebro is open to anyone who can reach the port. For any non-development deployment, set `AUTH_TYPE` to one of the four supported modes:

| `AUTH_TYPE` | Description |
|---|---|
| `basic` | Single shared username and password |
| `ldap` | LDAP/Active Directory bind authentication with optional group-based RBAC |
| `oauth` | OAuth 2.0 / OIDC (Okta, Azure AD, Keycloak, Auth0, etc.) with JWT-based RBAC |
| `proxy` | Trust identity headers from an upstream nginx + oauth2-proxy — Cerebro performs no auth itself |

---

#### Basic authentication

The simplest auth option: a single shared username and password. All authenticated users have full admin access (no per-user RBAC in basic mode).

##### Configuration

```bash
AUTH_TYPE=basic
BASIC_AUTH_USER=admin
BASIC_AUTH_PWD=changeme
```

Or via `application.conf`:

```hocon
auth {
  type: basic
  settings {
    username = "admin"
    password = "changeme"
  }
}
```

##### Docker example

```bash
docker run -d \
  -p 9000:9000 \
  -e AUTH_TYPE=basic \
  -e BASIC_AUTH_USER=admin \
  -e BASIC_AUTH_PWD=changeme \
  puneet1jain73/cerebro-rbac:latest
```

##### Limitations

- Single credential only — all users share the same account
- No per-user roles or RBAC (everyone is effectively admin)
- Password is passed as a plain environment variable — use Docker secrets or a secrets manager for production

For per-user access control, use LDAP, OAuth, or Proxy auth instead.

---

#### LDAP config

LDAP can be configured using environment variables. If you typically run cerebro using docker,
you can pass a file with all the env vars. The file would look like:

```bash
# Set it to ldap to activate ldap authorization
AUTH_TYPE=ldap

# Your ldap url
LDAP_URL=ldap://exammple.com:389

LDAP_BASE_DN=OU=users,DC=example,DC=com

# Usually method should  be "simple" otherwise, set it to the SASL mechanisms
LDAP_METHOD=simple

# user-template executes a string.format() operation where
# username is passed in first, followed by base-dn. Some examples
#  - %s => leave user untouched
#  - %s@domain.com => append "@domain.com" to username
#  - uid=%s,%s => usual case of OpenLDAP
LDAP_USER_TEMPLATE=%s@example.com

# User identifier that can perform searches
LDAP_BIND_DN=admin@example.com
LDAP_BIND_PWD=adminpass

# Group membership settings (optional)

# If left unset LDAP_BASE_DN will be used
# LDAP_GROUP_BASE_DN=OU=users,DC=example,DC=com

# Attribute that represent the user, for example uid or mail
# LDAP_USER_ATTR=mail

# If left unset LDAP_USER_TEMPLATE will be used
# LDAP_USER_ATTR_TEMPLATE=%s

# Filter that tests membership of the group. If this property is empty then there is no group membership check
# AD example => memberOf=CN=mygroup,ou=ouofthegroup,DC=domain,DC=com
# OpenLDAP example => CN=mygroup
# LDAP_GROUP=memberOf=memberOf=CN=mygroup,ou=ouofthegroup,DC=domain,DC=com

```

You can the pass this file as argument using:

```bash
 docker run -p 9000:9000 --env-file env-ldap  lmenezes/cerebro
```

#### LDAP Role-Based Access Control (RBAC)

Cerebro now supports fine-grained access control using LDAP groups. This allows you to restrict user permissions based on their LDAP group membership.

##### Features

- **Three-tier role hierarchy**:
  - **Admin**: Full access (cluster settings, delete indices, manage repositories)
  - **Editor**: Create/update indices, templates, aliases, snapshots (cannot delete indices or modify cluster settings)
  - **Viewer**: Read-only access to all resources

- **Deny-by-default security**: Users without mapped LDAP groups have no write access
- **Backward compatible**: Disabled by default, existing deployments unaffected
- **Comprehensive audit logging**: All operations are logged with user and role information

##### Configuration

Add these environment variables to enable RBAC:

```bash
# Enable RBAC (default: false)
CEREBRO_RBAC_ENABLED=true

# Map LDAP groups to Cerebro roles
# Format: "ldap_group_dn=role;another_group_dn=role"
# Valid roles: admin, editor, viewer
CEREBRO_RBAC_ROLE_MAPPING="cn=cerebro-admins,ou=groups,dc=example,dc=com=admin;cn=cerebro-editors,ou=groups,dc=example,dc=com=editor;cn=cerebro-viewers,ou=groups,dc=example,dc=com=viewer"

# Default role for users not in any mapped group (default: none)
# Options: none (no access), viewer, editor, admin
CEREBRO_RBAC_DEFAULT_ROLE=none
```

##### Docker Example with RBAC

```bash
docker run -d \
  -p 9000:9000 \
  -e AUTH_TYPE=ldap \
  -e LDAP_URL=ldap://ldap.example.com:389 \
  -e LDAP_BASE_DN=dc=example,dc=com \
  -e LDAP_USER_TEMPLATE="uid=%s,ou=users,dc=example,dc=com" \
  -e LDAP_BIND_DN="cn=admin,dc=example,dc=com" \
  -e LDAP_BIND_PWD=secret \
  -e CEREBRO_RBAC_ENABLED=true \
  -e CEREBRO_RBAC_ROLE_MAPPING="cn=cerebro-admins,ou=groups,dc=example,dc=com=admin;cn=cerebro-editors,ou=groups,dc=example,dc=com=editor" \
  -e CEREBRO_RBAC_DEFAULT_ROLE=viewer \
  puneet1jain73/cerebro-rbac:latest
```

##### Role Permissions Matrix

| Operation | Admin | Editor | Viewer |
|-----------|-------|--------|--------|
| View cluster/indices | ✅ | ✅ | ✅ |
| Create/update indices | ✅ | ✅ | ❌ |
| Create/update templates | ✅ | ✅ | ❌ |
| Manage aliases | ✅ | ✅ | ❌ |
| Create snapshots | ✅ | ✅ | ❌ |
| Delete indices | ✅ | ❌ | ❌ |
| Delete repositories | ✅ | ❌ | ❌ |
| Restore snapshots | ✅ | ❌ | ❌ |
| Cluster settings | ✅ | ❌ | ❌ |
| Shard allocation | ✅ | ❌ | ❌ |

##### Migration Guide

1. **Deploy with RBAC disabled** (default) - no behavior change
2. **Test in staging** with `CEREBRO_RBAC_ENABLED=true` and configure group mappings
3. **Optional**: Set `CEREBRO_RBAC_DEFAULT_ROLE=viewer` for safer rollout
4. **Deploy to production** and monitor audit logs

##### Troubleshooting

- **403 Forbidden errors**: User lacks required role. Check LDAP group membership and role mappings.
- **All users denied**: Verify `CEREBRO_RBAC_ROLE_MAPPING` format and LDAP group DNs are correct.
- **LDAP connection issues**: Check `LDAP_BIND_DN`, `LDAP_BIND_PWD`, and network connectivity.

Check application logs for detailed permission denial information including required roles.

There are some examples of configuration in the [examples folder](./examples).

#### OAuth 2.0 / OIDC Authentication

Cerebro supports OAuth 2.0 Authorization Code flow with any OIDC-compliant identity provider (Okta, Azure AD, Keycloak, Auth0, etc.). After authentication, JWT claims from the identity provider are mapped to Cerebro roles using the same RBAC system as LDAP.

##### Quick Start

```bash
AUTH_TYPE=oauth
OAUTH_DISCOVERY_URI=https://your-idp.example.com/.well-known/openid-configuration
OAUTH_CLIENT_ID=your-client-id
OAUTH_CLIENT_SECRET=your-client-secret
OAUTH_REDIRECT_URI=http://cerebro.example.com/auth/callback
```

##### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `AUTH_TYPE` | Yes | - | Set to `oauth` to enable OAuth/OIDC |
| `OAUTH_DISCOVERY_URI` | Yes* | - | OIDC discovery URL (`.well-known/openid-configuration`) |
| `OAUTH_CLIENT_ID` | Yes | - | OAuth client ID from your IdP |
| `OAUTH_CLIENT_SECRET` | Yes | - | OAuth client secret from your IdP |
| `OAUTH_REDIRECT_URI` | Yes | - | Callback URL: `http(s)://<cerebro-host>/auth/callback` |
| `OAUTH_SCOPES` | No | `openid profile email groups` | OAuth scopes to request |
| `OAUTH_TOKEN_TYPE` | No | `id_token` | Token to extract claims from: `id_token` or `access_token` |
| `OAUTH_CLAIM_MAPPING` | No | `groups` | JWT claim containing role/group values |
| `OAUTH_CLIENT_ID_FILE` | No | - | Path to file containing client ID (see [Securing Secrets](#securing-secrets)) |
| `OAUTH_CLIENT_SECRET_FILE` | No | - | Path to file containing client secret (see [Securing Secrets](#securing-secrets)) |

*If `OAUTH_DISCOVERY_URI` is not set, you must provide all three explicit endpoints:

| Variable | Description |
|----------|-------------|
| `OAUTH_AUTH_ENDPOINT` | Authorization endpoint URL |
| `OAUTH_TOKEN_ENDPOINT` | Token endpoint URL |
| `OAUTH_JWKS_URI` | JWKS endpoint URL for token signature verification |
| `OAUTH_ISSUER` | Expected JWT issuer (optional, for additional validation) |

##### OAuth + RBAC

OAuth works with the same RBAC role-mapping as LDAP. The `OAUTH_CLAIM_MAPPING` setting determines which JWT claim is read, and `CEREBRO_RBAC_ROLE_MAPPING` maps those values to Cerebro roles.

Example: If your IdP includes a `groups` claim in the JWT:
```json
{
  "sub": "user@example.com",
  "groups": ["cerebro-admins", "dev-team"]
}
```

Configure the mapping:
```bash
AUTH_TYPE=oauth
OAUTH_CLAIM_MAPPING=groups
CEREBRO_RBAC_ENABLED=true
CEREBRO_RBAC_ROLE_MAPPING="cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer"
CEREBRO_RBAC_DEFAULT_ROLE=viewer
```

Supported claim formats:
- **Array claims**: `"groups": ["cerebro-admins", "dev-team"]` (standard for Okta, Azure AD)
- **String claims**: `"role": "admin"` (single value)
- **Nested claims**: `"app_metadata.roles"` via dot-notation (e.g., `OAUTH_CLAIM_MAPPING=app_metadata.roles`)

##### Securing Secrets

For production deployments, avoid passing `OAUTH_CLIENT_SECRET` as an environment variable or inline config value. Instead, use **file-based secrets**:

```bash
OAUTH_CLIENT_ID_FILE=/run/secrets/oauth_client_id
OAUTH_CLIENT_SECRET_FILE=/run/secrets/oauth_client_secret
```

File-based secrets take precedence over inline values when both are set. This supports:

- **Docker secrets** (`/run/secrets/...`)
- **Kubernetes secret volumes** (mounted at any path)
- **HashiCorp Vault Agent** (templated files on disk)

Resolution order: secret file &rarr; environment variable / inline config &rarr; error.

##### Docker Example with OAuth

```bash
docker run -d \
  -p 9000:9000 \
  -e AUTH_TYPE=oauth \
  -e OAUTH_DISCOVERY_URI=https://dev-12345.okta.com/.well-known/openid-configuration \
  -e OAUTH_CLIENT_ID=0oa... \
  -e OAUTH_CLIENT_SECRET=... \
  -e OAUTH_REDIRECT_URI=https://cerebro.example.com/auth/callback \
  -e CEREBRO_RBAC_ENABLED=true \
  -e CEREBRO_RBAC_ROLE_MAPPING="cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer" \
  -e CEREBRO_RBAC_DEFAULT_ROLE=viewer \
  puneet1jain73/cerebro-rbac:latest
```

Docker Compose with secrets:

```yaml
services:
  cerebro:
    image: puneet1jain73/cerebro-rbac:latest
    ports:
      - "9000:9000"
    environment:
      AUTH_TYPE: oauth
      OAUTH_DISCOVERY_URI: https://dev-12345.okta.com/.well-known/openid-configuration
      OAUTH_CLIENT_ID_FILE: /run/secrets/oauth_client_id
      OAUTH_CLIENT_SECRET_FILE: /run/secrets/oauth_client_secret
      OAUTH_REDIRECT_URI: https://cerebro.example.com/auth/callback
      CEREBRO_RBAC_ENABLED: "true"
      CEREBRO_RBAC_ROLE_MAPPING: "cerebro-admins=admin;cerebro-editors=editor"
      CEREBRO_RBAC_DEFAULT_ROLE: viewer
    secrets:
      - oauth_client_id
      - oauth_client_secret

secrets:
  oauth_client_id:
    file: ./secrets/client-id.txt
  oauth_client_secret:
    file: ./secrets/client-secret.txt
```

##### Identity Provider Setup

**Okta:**
1. Create a "Web Application" in Okta Admin Console
2. Set sign-in redirect URI to `http(s)://<cerebro-host>/auth/callback`
3. Grant scopes: `openid`, `profile`, `email`, `groups`
4. Note the Client ID and Client Secret
5. Add a `groups` claim to the authorization server (Authorization Server &rarr; Claims &rarr; Add Claim, with filter regex `.*`)

**Azure AD:**
1. Register an application in Azure AD
2. Add redirect URI: `http(s)://<cerebro-host>/auth/callback` (type: Web)
3. Create a client secret under Certificates & Secrets
4. Set `OAUTH_DISCOVERY_URI` to `https://login.microsoftonline.com/<tenant-id>/v2.0/.well-known/openid-configuration`
5. Configure `OAUTH_CLAIM_MAPPING=groups` and add a group claim in Token Configuration

**Keycloak:**
1. Create a client in your realm with Access Type: confidential
2. Set Valid Redirect URIs to `http(s)://<cerebro-host>/auth/callback`
3. Set `OAUTH_DISCOVERY_URI` to `https://<keycloak-host>/realms/<realm>/.well-known/openid-configuration`
4. Add a `groups` mapper under Client Scopes to include group membership in tokens

##### Authentication Flows

**Browser flow (session cookie):**
```
User visits Cerebro
  → Login page with "Sign in with Identity Provider" button
  → Click redirects to /auth/authorize
    → Cerebro generates state + nonce (CSRF protection)
    → Redirects browser to IdP authorization endpoint
  → User authenticates at IdP
  → IdP redirects to /auth/callback?code=...&state=...
    → Cerebro validates state, exchanges code for tokens
    → Validates JWT signature via IdP JWKS keys
    → Extracts configured claim (e.g., groups)
    → Maps claim values to roles via RBAC config
    → Stores username + roles in Play session
  → User is authenticated with assigned roles
```

**Programmatic API flow (Bearer token):**

When `AUTH_TYPE=oauth`, API requests can authenticate by passing a JWT access token in the `Authorization` header instead of using the browser redirect flow. The token is validated using the same JWKS keys and claim mapping as the browser flow.

```
Script/curl sends request with Authorization: Bearer <JWT>
  → AuthAction checks session cookie → not found
  → AuthAction extracts Bearer token from Authorization header
  → OAuthService validates JWT signature via JWKS
  → Extracts username + roles from JWT claims
  → Request proceeds with RBAC enforcement
```

Example usage:
```bash
# Get a JWT token from your IdP (e.g., client credentials grant)
TOKEN=$(curl -s -X POST https://your-idp.example.com/oauth/token \
  -d "grant_type=client_credentials&client_id=cerebro-client&client_secret=secret&scope=openid" \
  | jq -r '.access_token')

# Call Cerebro API with Bearer token
curl -X POST http://localhost:9000/overview \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"host": "http://localhost:9200"}'
```

Notes:
- Session cookie authentication is checked first; Bearer token is the fallback
- Both methods work simultaneously — browser users are unaffected
- Bearer token auth returns HTTP 401 with `WWW-Authenticate: Bearer` header on invalid/expired tokens
- Bearer token auth is only available when `AUTH_TYPE=oauth`; LDAP and Basic Auth modes are unchanged
- RBAC is fully enforced on Bearer-authenticated requests (viewer tokens get 403 on write operations)

#### Proxy Authentication (nginx + oauth2-proxy)

When nginx running [oauth2-proxy](https://oauth2-proxy.github.io/oauth2-proxy/) sits in front of Cerebro, set `auth.type=proxy` to let Cerebro trust the forwarded user identity headers instead of performing its own login.

##### How it works

1. All browser traffic hits nginx first
2. nginx verifies the session with oauth2-proxy (`auth_request /oauth2/auth`)
3. oauth2-proxy injects the authenticated user's identity headers
4. nginx forwards those headers to Cerebro
5. Cerebro trusts the headers — no login form, no token validation

##### Quick start

```bash
AUTH_TYPE=proxy
CEREBRO_RBAC_ENABLED=true
CEREBRO_RBAC_ROLE_MAPPING="cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer"
CEREBRO_PROXY_TRUSTED_IPS="10.0.0.1"   # IP of your nginx host — see Security below
```

##### nginx configuration

```nginx
location /oauth2/ {
    proxy_pass http://oauth2-proxy:4180;
    proxy_set_header Host $host;
}

location / {
    auth_request /oauth2/auth;
    error_page 401 = /oauth2/sign_in;

    auth_request_set $user   $upstream_http_x_auth_request_user;
    auth_request_set $groups $upstream_http_x_auth_request_groups;
    auth_request_set $email  $upstream_http_x_auth_request_email;

    proxy_set_header X-Auth-Request-User   $user;
    proxy_set_header X-Auth-Request-Groups $groups;
    proxy_set_header X-Auth-Request-Email  $email;

    proxy_pass http://cerebro:9000;
}
```

##### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_TYPE` | — | Set to `proxy` to enable |
| `CEREBRO_PROXY_USER_HEADER` | `X-Auth-Request-User` | Header containing the username |
| `CEREBRO_PROXY_GROUPS_HEADER` | `X-Auth-Request-Groups` | Header containing comma-separated groups for RBAC mapping |
| `CEREBRO_PROXY_EMAIL_HEADER` | `X-Auth-Request-Email` | Fallback header for username if user header is absent |
| `CEREBRO_PROXY_TRUSTED_IPS` | *(empty)* | Comma-separated IPs/CIDRs that are permitted to supply proxy headers — **see Security below** |

##### Roles

Groups from `X-Auth-Request-Groups` are mapped to Cerebro roles using the same `CEREBRO_RBAC_ROLE_MAPPING` as LDAP and OAuth:

```bash
CEREBRO_RBAC_ENABLED=true
CEREBRO_RBAC_ROLE_MAPPING="cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer"
CEREBRO_RBAC_DEFAULT_ROLE=viewer
```

If the user header is missing (e.g., oauth2-proxy is misconfigured or the request bypassed nginx), Cerebro redirects to `/login`.

##### Security

> **Important:** Without `CEREBRO_PROXY_TRUSTED_IPS`, any actor who can reach Cerebro's port directly can forge `X-Auth-Request-User` to impersonate any user.

Always configure one of these two controls:

1. **Trusted IP allowlist (recommended)** — Cerebro rejects proxy headers from any source not in the list:
   ```bash
   CEREBRO_PROXY_TRUSTED_IPS="10.0.0.1"          # exact IP
   CEREBRO_PROXY_TRUSTED_IPS="10.0.0.1,172.16.0.0/24"  # multiple IPs/CIDRs
   ```

2. **Network policy (required in all cases)** — Ensure Cerebro's port (9000) is unreachable from outside the nginx host at the network/firewall level. The trusted IP list is defence-in-depth on top of this, not a substitute for it.

Requests rejected due to untrusted source IPs are logged as `login/failure` audit events including the originating IP.

##### Docker Example

```bash
docker run -d \
  -p 127.0.0.1:9000:9000 \        # bind to loopback — only nginx on the same host can reach it
  -e AUTH_TYPE=proxy \
  -e CEREBRO_PROXY_TRUSTED_IPS="127.0.0.1" \
  -e CEREBRO_RBAC_ENABLED=true \
  -e CEREBRO_RBAC_ROLE_MAPPING="cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer" \
  -e CEREBRO_RBAC_DEFAULT_ROLE=viewer \
  puneet1jain73/cerebro-rbac:latest
```

---

#### Audit Logging

Cerebro emits a structured JSON audit event for every authenticated user operation. Events are written to stdout via a dedicated `audit` logger, ready for ingestion by Splunk, Datadog, Fluentd, or any log aggregator.

##### Sample event

```json
{"timestamp":"2026-05-10T09:31:44Z","type":"api","user":"alice","roles":"admin","http_method":"POST","path":"/create_index","operation":"api_request","outcome":"allowed","status_code":200,"source_ip":"10.0.1.42","user_agent":"Mozilla/5.0","target_host":"http://prod-cluster:9200","body":{"host":"prod-cluster","name":"my-index","shards":2,"replicas":1}}
```

Auth events:

```json
{"timestamp":"2026-05-10T09:30:00Z","type":"auth","user":"alice","roles":"admin","operation":"login","outcome":"success","source_ip":"10.0.1.42","user_agent":"Mozilla/5.0"}
{"timestamp":"2026-05-10T09:31:00Z","type":"auth","user":"mallory","roles":"","operation":"login","outcome":"failure","source_ip":"10.0.1.99","user_agent":"curl/7.88.1"}
```

##### Fields

| Field | Description |
|-------|-------------|
| `timestamp` | ISO-8601 UTC |
| `type` | `api` for requests, `auth` for login/logout events |
| `user` | Authenticated username (`anonymous` if unauthenticated) |
| `roles` | Comma-separated Cerebro roles |
| `http_method` | HTTP method of the request |
| `path` | Cerebro route path (e.g., `/create_index`) |
| `operation` | Operation category (`api_request`, `login`, `logout`) |
| `outcome` | `allowed`, `denied`, `error`, `success`, or `failure` |
| `status_code` | HTTP response status |
| `source_ip` | Client/proxy IP address |
| `user_agent` | `User-Agent` header |
| `target_host` | Target Elasticsearch cluster URL |
| `body` | Censored request body — `password` fields replaced with `xxxxxx` |

##### Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `CEREBRO_AUDIT_ENABLED` | `true` | Set to `false` to disable all audit output |
| `CEREBRO_AUDIT_INCLUDE_BODY` | `true` | Set to `false` to omit request body from events |

Polling endpoints (`/overview`, `/nodes`, `/navbar`, `/cluster_changes`) are excluded by default to prevent log noise. To customise, set `audit.excluded-paths` in `application.conf`.

##### Splunk ingestion

The simplest path from stdout to Splunk:

- **Docker**: use `--log-driver=splunk` with a Splunk HEC token
- **Kubernetes**: deploy a Splunk Connect for Kubernetes sidecar that tails stdout
- **VM**: install the Splunk Universal Forwarder and monitor the process stdout stream

Each audit line is a self-contained JSON object — pipe through `jq .` to verify format before connecting a forwarder.

---

#### Security summary

| Auth type | Who authenticates | Session cookie | Bearer token | Per-user RBAC | Trusted IP required |
|---|---|---|---|---|---|
| None (default) | Nobody — open access | N/A | N/A | No | No |
| `basic` | Cerebro (single shared credential) | Yes | No | No — everyone is admin | No |
| `ldap` | Cerebro via LDAP bind | Yes | No | Yes (group → role mapping) | No |
| `oauth` | Cerebro via OIDC / IdP | Yes | Yes (JWT Bearer) | Yes (JWT claim → role mapping) | No |
| `proxy` | nginx + oauth2-proxy (upstream) | Yes | No | Yes (group header → role mapping) | **Yes — strongly recommended** |

**Recommendations for production:**

- Never run with auth disabled (`AUTH_TYPE` unset) on a network-accessible host.
- `basic` auth is suitable only for single-user / local dev deployments — no per-user audit trail.
- For `ldap` and `oauth`, enable RBAC (`CEREBRO_RBAC_ENABLED=true`) and set a deny-by-default `CEREBRO_RBAC_DEFAULT_ROLE=none`.
- For `proxy` auth, always set `CEREBRO_PROXY_TRUSTED_IPS` to the nginx host IP(s) and ensure Cerebro's port is unreachable directly from the network.
- Enable audit logging (on by default) and ship stdout to a SIEM or log aggregator.

---

#### Other settings

Other settings are exposed through the **conf/application.conf** file found on the application directory.

It is also possible to use an alternate configuration file defined on a different location:

```
bin/cerebro -Dconfig.file=/some/other/dir/alternate.conf
```
