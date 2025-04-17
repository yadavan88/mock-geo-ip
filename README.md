### A mock service
- Run `sbt "backend/run"`
- Access UI in `localhost:9050`

- Alternativel, build docker image using `sbt docker:publishLocal`
- Run `docker run -p 9050:9050 mock-geoip:latest`
