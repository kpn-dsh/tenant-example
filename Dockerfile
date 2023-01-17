FROM openjdk:17-jdk-slim-buster
MAINTAINER Bruno De Bus <Bruno.DeBus@klarrio.com>

ARG image_version
# for training purposed configure uid to be 1024 for dshdemo1 or 1025 for
# dshdemo2
ARG user_id=1937

RUN apt update && apt install -y openssl curl

ADD get_signed_certificate.sh /get_signed_certificate.sh
ADD docker-entrypoint.sh /docker-entrypoint.sh

RUN groupadd --gid $user_id dshdemo
RUN useradd --no-create-home --uid $user_id --gid $user_id dshdemo

RUN mkdir -p /usr/share/tenant-example/conf
ADD target/lib /usr/share/tenant-example/lib
ADD target/tenant-example-${image_version}.jar /usr/share/tenant-example/tenant-example.jar

RUN chown -R $user_id:$user_id /usr/share/tenant-example \
    && chmod -R o-rwx /usr/share/tenant-example
USER $user_id 

ENTRYPOINT [ "/docker-entrypoint.sh" ]
