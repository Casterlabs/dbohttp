FROM eclipse-temurin:17-jre-ubi9-minimal
WORKDIR /home/container

LABEL org.opencontainers.image.source="https://github.com/casterlabs/dbohttp"

# code
COPY ./server/target/dbohttp.jar /home/container
COPY ./docker_launch.sh /home/container
RUN chmod +x docker_launch.sh

# entrypoint
CMD [ "./docker_launch.sh" ]
EXPOSE 8000/tcp