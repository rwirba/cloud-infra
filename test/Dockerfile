RUN chown -R root:root /usr/lib/jvm /usr/share/java /usr/share/confluent-hub-components 2>/dev/null || true && \
    chmod -R 755 /usr/lib/jvm /usr/share/java /usr/share/confluent-hub-components 2>/dev/null || true && \
    # Symlink java to a standard location that nobody can always find
    ln -sf /usr/lib/jvm/temurin-21-jre*/bin/java /usr/bin/java 2>/dev/null || true

USER nobody

# Bake proper environment for nobody (this is the key part)
ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jre
ENV PATH="/usr/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/sbin:/bin:${JAVA_HOME}/bin"
ENV _JAVA_OPTIONS="-Djava.io.tmpdir=/tmp"

# Optional: extra safety
WORKDIR /tmp
