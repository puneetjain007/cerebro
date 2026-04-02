# Multi-stage build for Cerebro with LDAP RBAC support
FROM eclipse-temurin:11-jdk-jammy AS builder

# Install dependencies
RUN apt-get update && \
    apt-get install -y curl unzip git && \
    rm -rf /var/lib/apt/lists/*

# Install sbt
RUN curl -L https://github.com/sbt/sbt/releases/download/v1.9.7/sbt-1.9.7.tgz | tar xz -C /usr/local

# Set working directory
WORKDIR /build

# Copy project files
COPY . .

# Build the application
RUN /usr/local/sbt/bin/sbt clean dist

# Extract the distribution
RUN unzip target/universal/cerebro-*.zip -d /opt && \
    mv /opt/cerebro-* /opt/cerebro

# Runtime stage
FROM eclipse-temurin:11-jre-jammy

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y bash && \
    rm -rf /var/lib/apt/lists/*

# Create cerebro user
RUN useradd -r -s /bin/false -u 1000 cerebro

# Copy application from builder
COPY --from=builder /opt/cerebro /opt/cerebro

# Create data directory for SQLite database
RUN mkdir -p /opt/cerebro/data && \
    chown -R cerebro:cerebro /opt/cerebro

# Expose port
EXPOSE 9000

# Switch to cerebro user
USER cerebro

# Set working directory
WORKDIR /opt/cerebro

# Environment variables with defaults
ENV CEREBRO_PORT=9000 \
    JAVA_OPTS="-Xms256m -Xmx1g --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/sun.security.ssl=ALL-UNNAMED --add-opens=java.base/sun.net.www.protocol.file=ALL-UNNAMED"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${CEREBRO_PORT}/ || exit 1

# Start cerebro
CMD ["bash", "-c", "bin/cerebro -Dhttp.port=${CEREBRO_PORT} -Dconfig.file=conf/application.conf ${JAVA_OPTS}"]
