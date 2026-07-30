# syntax=docker/dockerfile:1
#
# Dockerfile unico, parametrizado pelo modulo. O enunciado declara que Dockerfile nao e avaliado;
# ele existe apenas para tornar o docker-compose executavel.

FROM gradle:9.6.1-jdk25 AS construcao
ARG MODULO
WORKDIR /projeto

COPY settings.gradle build.gradle gradle.properties ./
# Gradle 9 exige que todo diretorio declarado em settings.gradle exista, mesmo para construir um
# unico modulo — por isso os cinco sao copiados independentemente do MODULO alvo.
COPY api-analise-risco/build.gradle api-analise-risco/
COPY servico-listas/build.gradle servico-listas/
COPY motor-decisao/build.gradle motor-decisao/
COPY servico-auditoria/build.gradle servico-auditoria/
COPY gerador-trafego/build.gradle gerador-trafego/

COPY api-analise-risco/src api-analise-risco/src
COPY servico-listas/src servico-listas/src
COPY motor-decisao/src motor-decisao/src
COPY servico-auditoria/src servico-auditoria/src
COPY gerador-trafego/src gerador-trafego/src

RUN gradle :${MODULO}:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine AS execucao
ARG MODULO
WORKDIR /aplicacao

RUN addgroup -S acme && adduser -S acme -G acme
COPY --from=construcao /projeto/${MODULO}/build/libs/*.jar aplicacao.jar
RUN chown -R acme:acme /aplicacao
USER acme

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "aplicacao.jar"]
