FROM openjdk:17-jdk-slim-buster
MAINTAINER Silan Isik <s.isik02@kpn.com>



ARG IMAGE_VERSION

# for training purposed configure uid to be 1024 for dshdemo1 or 1025 for
# dshdemo2
ENV id 1937
ADD get_signed_certificate.sh /get_signed_certificate.sh
ADD docker-entrypoint.sh /docker-entrypoint.sh

RUN apt update && apt install -y openssl curl

RUN groupadd --gid $id dshdemo
RUN useradd --no-create-home --uid $id --gid $id dshdemo

RUN mkdir -p /usr/share/tenant-example/conf
ADD target/lib /usr/share/tenant-example/lib
ADD target/tenant-example-${IMAGE_VERSION}.jar /usr/share/tenant-example/tenant-example.jar

RUN chown -R $id:$id /usr/share/tenant-example \
    && chmod -R o-rwx /usr/share/tenant-example
USER $id 

ENTRYPOINT [ "/docker-entrypoint.sh" ]
