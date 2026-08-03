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
COPY modulos/api-analise-risco/build.gradle modulos/api-analise-risco/
COPY modulos/servico-listas/build.gradle modulos/servico-listas/
COPY modulos/motor-decisao/build.gradle modulos/motor-decisao/
COPY modulos/servico-auditoria/build.gradle modulos/servico-auditoria/
COPY modulos/gerador-trafego/build.gradle modulos/gerador-trafego/

COPY modulos/api-analise-risco/src modulos/api-analise-risco/src
COPY modulos/servico-listas/src modulos/servico-listas/src
COPY modulos/motor-decisao/src modulos/motor-decisao/src
COPY modulos/servico-auditoria/src modulos/servico-auditoria/src
COPY modulos/gerador-trafego/src modulos/gerador-trafego/src

RUN gradle :${MODULO}:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine AS execucao
ARG MODULO
WORKDIR /aplicacao

RUN addgroup -S acme && adduser -S acme -G acme
COPY --from=construcao /projeto/modulos/${MODULO}/build/libs/*.jar aplicacao.jar
RUN chown -R acme:acme /aplicacao
USER acme

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "aplicacao.jar"]
