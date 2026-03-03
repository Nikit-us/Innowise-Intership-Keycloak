FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.1.0
WORKDIR /opt/keycloak

COPY --from=build /app/target/ /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build --db=postgres

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]