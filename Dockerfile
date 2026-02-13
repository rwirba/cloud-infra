FROM confluentinc/cp-kafka-connect-base:8.0.3-1-ubi9

# Switch to root for package updates
USER root

ARG JERSEY_VERSION=3.1.11
ARG LZ4_VERSION=1.10.3
ARG JOSE4J_VERSION=0.9.6

RUN set -eux; \
    rm -rf /usr/local/lib/python3.9 || true; \
    rm -rf /usr/local/bin/python3.9* /usr/local/bin/pip3.9* || true; \
    find /usr/local -type d -name "*python3.9*" -exec rm -rf {} + 2>/dev/null || true; \
    microdnf clean all

# 2. Install clean Python 3.12 from UBI9 repos
RUN microdnf install -y \
    python3.12 \
    python3.12-pip && \
    microdnf clean all

# 3. Make python3 / pip3 point to the new 3.12 version
RUN ln -sf /usr/bin/python3.12 /usr/bin/python3 && \
    ln -sf /usr/bin/pip3.12 /usr/bin/pip3 && \
    python3 -m pip install --no-cache-dir --upgrade pip && \
    python3 -m pip install --no-cache-dir "setuptools>=82.0.0"

RUN microdnf makecache && \
    microdnf update -y openssl openssl-libs && \
    microdnf update -y && \
    microdnf clean all    

    # RUN set -eux; \
RUN find /usr/share/java/ -name "jersey-*-3.1.9*.jar" -delete || true; \
    find /usr/share/java/ -name "lz4-java-1.8.0*.jar" -delete || true; \
    find /usr/share/java/ -name "jose4j-0.9.[0-5].jar" -delete || true; \
    find /usr/share/java/ -type f \( -name "*.pom" -o -name "*.sha1" \) -delete || true; \
    microdnf update -y && microdnf clean all

RUN set -eux; \
    # Jersey 3.1.11 (fixes CVE-2025-12383)
    curl -sSL "https://repo1.maven.org/maven2/org/glassfish/jersey/core/jersey-client/${JERSEY_VERSION}/jersey-client-${JERSEY_VERSION}.jar" \
      -o /usr/share/java/kafka/jersey-client-${JERSEY_VERSION}.jar && \
    curl -sSL "https://repo1.maven.org/maven2/org/glassfish/jersey/core/jersey-common/${JERSEY_VERSION}/jersey-common-${JERSEY_VERSION}.jar" \
      -o /usr/share/java/kafka/jersey-common-${JERSEY_VERSION}.jar && \
    curl -sSL "https://repo1.maven.org/maven2/org/glassfish/jersey/core/jersey-server/${JERSEY_VERSION}/jersey-server-${JERSEY_VERSION}.jar" \
      -o /usr/share/java/kafka/jersey-server-${JERSEY_VERSION}.jar && \
    curl -sSL "https://repo1.maven.org/maven2/org/glassfish/jersey/inject/jersey-hk2/${JERSEY_VERSION}/jersey-hk2-${JERSEY_VERSION}.jar" \
      -o /usr/share/java/kafka/jersey-hk2-${JERSEY_VERSION}.jar && \
    curl -sSL "https://repo1.maven.org/maven2/org/glassfish/jersey/containers/jersey-container-servlet/${JERSEY_VERSION}/jersey-container-servlet-${JERSEY_VERSION}.jar" \
      -o /usr/share/java/kafka/jersey-container-servlet-${JERSEY_VERSION}.jar && \
    curl -sSL "https://repo1.maven.org/maven2/org/glassfish/jersey/containers/jersey-container-servlet-core/${JERSEY_VERSION}/jersey-container-servlet-core-${JERSEY_VERSION}.jar" \
      -o /usr/share/java/kafka/jersey-container-servlet-core-${JERSEY_VERSION}.jar && \
    # LZ4 1.10.3 (fixes CVE-2025-12183)
    curl -sSL "https://repo1.maven.org/maven2/at/yawk/lz4/lz4-java/${LZ4_VERSION}/lz4-java-${LZ4_VERSION}.jar" \
      -o /usr/share/java/kafka/lz4-java-${LZ4_VERSION}.jar && \
    # jose4j 0.9.6 (fixes CVE-2024-29371)
    curl -sSL "https://repo1.maven.org/maven2/org/bitbucket/b_c/jose4j/${JOSE4J_VERSION}/jose4j-${JOSE4J_VERSION}.jar" \
      -o /usr/share/java/kafka/jose4j-${JOSE4J_VERSION}.jar

# 2. Propagate to EVERY Confluent directory + remove ALL old versions
RUN set -eux; \
    for dir in /usr/share/java/kafka \
               /usr/share/java/rest-utils \
               /usr/share/java/schema-registry \
               /usr/share/java/cp-base-new \
               /usr/share/java/kafka-serde-tools \
               /usr/share/java/confluent-hub-client; do \
        cp /usr/share/java/kafka/jersey-*${JERSEY_VERSION}*.jar "$dir"/ || true; \
        cp /usr/share/java/kafka/lz4-java-${LZ4_VERSION}.jar "$dir"/ || true; \
        cp /usr/share/java/kafka/jose4j-${JOSE4J_VERSION}.jar "$dir"/ || true; \
    done; \
    # Aggressive cleanup: delete EVERYTHING old
    find /usr/share/java/ -name "*jersey*-3.1.9*" -delete || true; \
    find /usr/share/java/ -name "*lz4-java-1.8.0*" -delete || true; \
    find /usr/share/java/ -name "*jose4j*" -not -name "*${JOSE4J_VERSION}.jar" -delete || true

# 3. Make sure new JARs have correct ownership (appuser)
RUN chown -R appuser:appuser /usr/share/java/


USER nobody
