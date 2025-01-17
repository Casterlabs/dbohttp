#!/bin/bash

rm /home/container/config.json
echo '{"database": {"file": "/home/container/database.sqlite","driver": "SQLITE","accessTimeoutSeconds": 30},"debug": false,"jwtSecret": "$JWT_SECRET","port": 8000,"heartbeatUrl": null,"heartbeatIntervalSeconds": 15,"ssl": null}' >> /home/container/config.json

java -XX:+CrashOnOutOfMemoryError -jar dbohttp.jar
