FROM openjdk:11-jdk AS buildbase-11

RUN apt-get update && apt-get install -y curl tar

ARG MAVEN_VERSION=3.8.2
ARG USER_HOME_DIR="/root"

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz \
    | tar -xzC /usr/share/maven --strip-components=1 \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

RUN echo -e '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">\n  <localRepository>/usr/share/maven/ref/repository</localRepository>\n</settings>' > /usr/share/maven/ref/settings-docker.xml

WORKDIR /build/timbuctoo

#COPY host:./ image:/build/timbuctoo
COPY ./ContractDiff/pom.xml ./ContractDiff/pom.xml
COPY ./HttpCommand/pom.xml ./HttpCommand/pom.xml
COPY ./security-client-agnostic/pom.xml ./security-client-agnostic/pom.xml
COPY ./timbuctoo-test-services/pom.xml ./timbuctoo-test-services/pom.xml
COPY ./timbuctoo-instancev4/pom.xml ./timbuctoo-instancev4/pom.xml
COPY ./pom.xml ./pom.xml
COPY ./timbuctoo-instancev4/src/main/resources/checkstyle_config.xml ./timbuctoo-instancev4/src/main/resources/checkstyle_config.xml

# COPY ./maven-prefill /root/.m2/repository

RUN mvn clean package dependency:go-offline

#RUN rm -r ./*

FROM buildbase-11 AS build

#COPY host:./ image:/build/timbuctoo
COPY ./ContractDiff ./ContractDiff
COPY ./HttpCommand ./HttpCommand
COPY ./security-client-agnostic ./security-client-agnostic
COPY ./timbuctoo-test-services ./timbuctoo-test-services
COPY ./timbuctoo-instancev4/src ./timbuctoo-instancev4/src
COPY ./timbuctoo-instancev4/pom.xml ./timbuctoo-instancev4/pom.xml
COPY ./pom.xml ./pom.xml

COPY ./timbuctoo-instancev4/example_config.yaml ./timbuctoo-instancev4/example_config.yaml
RUN mvn clean package

FROM openjdk:11-jre-slim

WORKDIR /app

RUN mkdir -p /root/data/dataSets && \
  mkdir -p /root/data/neo4j && \
  mkdir -p /root/data/auth/authorizations && \
  echo "[]" > /root/data/auth/logins.json && \
  echo "[]" > /root/data/auth/users.json

COPY --from=build /build/timbuctoo/timbuctoo-instancev4/target/appassembler .
COPY --from=build /build/timbuctoo/timbuctoo-instancev4/example_config.yaml .

CMD ./bin/timbuctoo server, ./example_config.yaml | tee -a /log/timbuctoo.log

EXPOSE 80 81
ENV timbuctoo_port="80"
ENV timbuctoo_adminPort="81"
ENV base_uri=http://localhost:8080

ENV timbuctoo_elasticsearch_host=http://example.com/elasticsearchhost
ENV timbuctoo_elasticsearch_port=80
ENV timbuctoo_elasticsearch_user=user
ENV timbuctoo_elasticsearch_password=password

ENV timbuctoo_dataPath="/root/data"
ENV timbuctoo_authPath="/root/data/auth"

ENV timbuctoo_search_url=http://localhost:8082
ENV timbuctoo_indexer_url=http://indexer
