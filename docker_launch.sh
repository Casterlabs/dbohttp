#!/bin/bash

rm /home/container/config.json
echo '{"database": {"connectionString": "'$DB_CONN_STR'","driver": "'$DB_DRIVER'","accessTimeoutSeconds": 30},"debug": false,"jwtSecret": "'$JWT_SECRET'","port": 10243,"heartbeatUrl": null,"heartbeatIntervalSeconds": 15}' >> /home/container/config.json

java -XX:+CrashOnOutOfMemoryError -jar dbohttp.jar
