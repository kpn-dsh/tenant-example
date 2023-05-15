FROM --platform=linux/amd64 eclipse-temurin:17-jre

ARG IMAGE_VERSION

# for training purposed configure uid to be 1024 for dshdemo1 or 1025 for
# dshdemo2
ENV id 2079
ADD get_signed_certificate.sh /get_signed_certificate.sh
ADD docker-entrypoint.sh /docker-entrypoint.sh

RUN groupadd --gid $id dshdemo
RUN useradd --no-create-home --uid $id --gid $id dshdemo

RUN mkdir -p /usr/share/tenant-example/conf
ADD target/lib /usr/share/tenant-example/lib
ADD target/tenant-example-${IMAGE_VERSION}.jar /usr/share/tenant-example/tenant-example.jar

RUN chown -R $id:$id /usr/share/tenant-example \
    && chmod -R o-rwx /usr/share/tenant-example
USER $id 

ENTRYPOINT [ "/docker-entrypoint.sh" ]
