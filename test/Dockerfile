FROM confluentinc/cp-kafka-connect:8.2.1
USER root

# --- Remove vulnerable Netty + Jetty JARs ---
USER root
RUN cat <<EOF > /etc/yum.repos.d/adoptium.repo
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/${DISTRIBUTION_NAME:-$(. /etc/os-release; echo $ID)}/\$releasever/\$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
EOF
RUN microdnf install -y temurin-22-jdk &&  \
    rm -rf /usr/lib/jvm/temurin-22-jdk && \
    microdnf clean all


WORKDIR /tmp

RUN rm -f \
    /usr/share/java/kafka-serde-tools/netty-codec-http2-4.1.130.Final.jar \
    /usr/share/java/kafka-serde-tools/netty-codec-http-4.1.130.Final.jar \
    /usr/share/java/kafka-serde-tools/netty-codec-dns-4.1.130.Final.jar \
    /usr/share/java/kafka-serde-tools/netty-codec-4.1.130.Final.jar \
    /usr/share/java/rest-utils/jetty-server-12.0.25.jar \
    /usr/share/java/rest-utils/jetty-http-12.0.25.jar

# --- Add fixed Netty 4.1.133.Final ---
RUN curl -L -o /usr/share/java/kafka-serde-tools/netty-codec-http2-4.1.133.Final.jar \
        https://repo1.maven.org/maven2/io/netty/netty-codec-http2/4.1.133.Final/netty-codec-http2-4.1.133.Final.jar && \
    curl -L -o /usr/share/java/kafka-serde-tools/netty-codec-http-4.1.133.Final.jar \
        https://repo1.maven.org/maven2/io/netty/netty-codec-http/4.1.133.Final/netty-codec-http-4.1.133.Final.jar && \
    curl -L -o /usr/share/java/kafka-serde-tools/netty-codec-dns-4.1.133.Final.jar \
        https://repo1.maven.org/maven2/io/netty/netty-codec-dns/4.1.133.Final/netty-codec-dns-4.1.133.Final.jar && \
    curl -L -o /usr/share/java/kafka-serde-tools/netty-codec-4.1.133.Final.jar \
        https://repo1.maven.org/maven2/io/netty/netty-codec/4.1.133.Final/netty-codec-4.1.133.Final.jar

# --- Add fixed Jetty ---
RUN curl -L -o /usr/share/java/rest-utils/jetty-server-12.0.32.jar \
        https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-server/12.0.32/jetty-server-12.0.32.jar && \
    curl -L -o /usr/share/java/rest-utils/jetty-http-12.0.33.jar \
        https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-http/12.0.33/jetty-http-12.0.33.jar

# Fix permissions for runtime user

RUN chown -R appuser:appuser /usr/share/java
USER nobody
