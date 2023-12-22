FROM eclipse-temurin:8-jre
MAINTAINER Bruno De Bus <Bruno.DeBus@klarrio.com>

ARG IMAGE_VERSION
ARG UID

ADD get_signed_certificate.sh /get_signed_certificate.sh
ADD docker-entrypoint.sh /docker-entrypoint.sh

RUN groupadd --gid ${UID} dshdemo
RUN useradd --no-create-home --uid ${UID} --gid ${UID} dshdemo

RUN mkdir -p /usr/share/tenant-example/conf
ADD target/lib /usr/share/tenant-example/lib
ADD target/tenant-example-${IMAGE_VERSION}.jar /usr/share/tenant-example/tenant-example.jar

RUN chown -R ${UID}:${UID} /usr/share/tenant-example \
    && chmod -R o-rwx /usr/share/tenant-example
USER ${UID}

ENTRYPOINT [ "/docker-entrypoint.sh" ]
