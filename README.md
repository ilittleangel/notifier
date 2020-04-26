# Notifier

HTTP/Rest server to receive and centralize alarms and be able to send to different destinations (Email, Slack, Ftp, ..)

* [Technologies](#technologies)
* [Building Notifier](#building-notifier)
* [Running with docker](#running-with-docker)
* [Notifier configuration via `notifier.conf`](#notifier-configuration-via-notifier-conf)
* [Send an alert](#send-an-alert)
* [Send Ftp alerts](#send-ftp-alerts)
* [Send Slack alerts](#send-slack-alerts)
* [TODOs](#todos)


## Technologies

* [Scala 2.12](https://www.scala-lang.org/api/2.12.7/)
* [Akka HTTP Server API](https://doc.akka.io/docs/akka-http/current/server-side/index.html)
* [Akka HTTP Client API](https://doc.akka.io/docs/akka-http/current/client-side/index.html)
* [Json Support](https://doc.akka.io/docs/akka-http/current/common/json-support.html)
* [Docker](https://www.docker.com/)
* [Kubernetes](https://kubernetes.io/)




## Building Notifier

Notifier is built using [Gradle](https://docs.gradle.org/current/userguide/userguide.html). 
To build the software from the command line just run the following from the root of the project:

```shell script
./gradlew build
```



## Running with docker

A `docker-compose.yml` configuration file is provided to help testing in development environments. This compose file brings up
an instance of SFTP server.

To run them locally the easiest way is construct a docker image from the distribution zip file, get it from the building step. To do it:

```shell script
./gradlew docker
```

And the following images are created or pulled:

```
REPOSITORY                                   TAG                 IMAGE ID            CREATED             SIZE
angelrojo/notifier                           latest              3200e92e8860        46 hours ago        138MB
atmoz/sftp                                   latest              6345f82053c6        15 months ago       190MB
openjdk                                      8u181-jre-alpine    d4557f2c5b71        16 months ago       83MB
```

To run it, execute the following from `$PROJECT_HOME/deploy/docker-compose`:

`docker-compose up`

To shutdown all the containers and remove them:

`docker-compose down`



## Notifier configuration via `notifier.conf`

* The config file is HOCON format
* With this file we should setup the HTTP/Rest `server` and `ftp` destination.

```hocon
{
  server: {
    bind_address: "0.0.0.0"
    bind_port: 8080
  }

  ftp: {
    username: "notifier"
    password: "password"
  }
}
```

**Server**:
* The HTTP/Rest server interface is `0.0.0.0`, so will be allow connections from any IP.
* The server will be listening on port `8080`.

**Ftp**:
* Ftp is a `Destination` that consists of writing into a remote file system via FTP/SFTP.
* Ftp credentials only can be set with the `notifier.conf` file.
* But other properties can be set with this file in the `ftp` section.


## Send an alert

* To perform an HTTP request to the notifier we must to provide a Json body with this mandatory fields:

```json
{
    "destination": "ftp|slack|email",
    "message": "whatever message want to inform into destination",
    "properties": {
        ...
    }
}
```

* Destination has to be one of these:
    - `ftp`
    - `slack`
    - `email` 
>Email not implemented yet

* If the request is sent with other destination the response will be a `400 Bad Request` and a message like this:
```
The request content was malformed:
Expected (Ftp, Slack or Email) for 'destination' attribute
```
* Provide a `message` field is mandatory as well, if not will get a `400 Bad Request` response with a message like this:
```
The request content was malformed:
Object is missing required member 'message'
``` 
* If there is no `properties` the response will be another `400 Bad Request`, but in this case will be more feedback:
```json
{
    "status": 400,
    "statusText": "Bad Request",
    "reason": "Ftp alert with no properties",
    "possibleSolution": "Include properties with 'host' and 'path'"
}
```


## Send Ftp alerts

### Request
```
POST /notifier/api/v1/alerts
```
```json
{
    "destination": "ftp",
    "message": "......",
    "properties": {
        "host": "sftp-server",
        "port": "22",
        "protocol": "sftp",
        "path": "/upload/data.txt"
    }
}
```

* This properties can be set via `notifier.conf` file but this way has no preference over the values set in the Json Body request.
* The property `path` is the file path in our FTP destination. If exists the `message` will be append at the end. But if the file does not exists, the `Ftp` component will be create a new one.

### Successful response

```json
{
    "alert": {
        "destination": "ftp",
        "message": "......",
        "properties": {
            "host": "sftp-server",
            "port": "22",
            "protocol": "sftp",
            "path": "/upload/data.txt"
        },
        "ts": "2020-04-07T18:49:44.475Z"
    },
    "isPerformed": true,
    "status": "Ftp alert success [value=Done, count=24]",
    "description": "alert received and performed!"
}
```

### Failed responses

* When something wrong happens with a Ftp alert the body response will be as following:
```json
{
    "alert": {
        ...
    },
    "isPerformed": false,
    "status": "Ftp alert failed with IOResult[error=Connection refused (Connection refused), count=0]",
    "description": "alert received but not performed!"
}
```

* Only the `status` field will be change depending of what is the mistake in the incomming webhook url.
* In such cases the `status` field give more information about the error. Some of these errors could be:

| error                                                                                                  | description                                   | http status     |
|:-------------------------------------------------------------------------------------------------------|:----------------------------------------------|-----------------|
|`"status": "Ftp alert failed with IOResult[error=Connection refused (Connection refused), count=0]"`    | bad port                                      | 400 bad request |
|`"status": "Ftp alert failed with IOResult[error=Exhausted available authentication methods, count=0]"` | authentication fail (bad username OR password)| 400 bad request |
|`"status": "Ftp alert failed with an Exception [error=sftp-servexr: Name does not resolve]"`            | unknown server address                        | 400 bad request |
|`"status": "Ftp alert failed with an Exception [error=sfatp (of class java.lang.String)]"`              | unknown protocol                              | 400 bad request |



## Send Slack alerts

### Request
```
POST /notifier/api/v1/alerts
```
```json
{
    "destination": "slack",
    "message": "......",
    "properties": {
        "webhook": "https://hooks.slack.com/services/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"
    }
}
```

### Successful response

```json
{
    "alert": {
        "destination": "slack",
        "message": "......",
        "properties": {
            "webhook": "https://hooks.slack.com/services/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"
        },
        "ts": "2020-03-25T17:44:44.112Z"
    },
    "isPerformed": true,
    "status": "ok",
    "description": "alert received and performed!"
}
```

### Failed responses

* When something wrong happens with a Slack alert the body response will be as following:
```json
{
    "alert": {
        ...
    },
    "isPerformed": false,
    "status": "Slack webhook request failed [error=`uri` must have scheme \"http\", \"https\", \"ws\", \"wss\" or no scheme]",
    "description": "alert received but not performed!"
}
```

* Only the `status` field will be change depending of what is the mistake in the incomming webhook url.
* In such cases the `status` field give more information about the error:

|   | error                                                                                                                     | http status     |
|---|:--------------------------------------------------------------------------------------------------------------------------|-----------------|
| 1 | `"status": "Slack webhook request failed [error=uri must have scheme \"http\", \"https\", \"ws\", \"wss\" or no scheme]"` | 400 bad request |
| 2 | `"status": "Slack webhook request failed [status=302 Found] [entity=]"`                                                   | 400 bad request |
| 3 | `"status": "no_team"`                                                                                                     | 400 bad request |
| 4 | `"status": "no_service"`                                                                                                  | 400 bad request |
| 5 | `"status": "invalid_token"`                                                                                               | 400 bad request |

The error depends on where the mistake is in the URL:
```text
https://hooks.slaack.com/services/TGYMK73M5/BQ649ENEL/ssSOoIvymUUAJnQflhu8LFdE
 ^                          ^         ^         ^                 ^
 1                          2         3         4                 5
```

    

## TODOs

* [X] Implement Slack destination (akka HTTP client API)
* [X] Implement Ftp destination (FTP/SFTP client)
* [ ] Implement Email destination
* [X] Unit tests with [Route TestKit](https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html)
* [ ] Provide custom [Exception Handling](https://doc.akka.io/docs/akka-http/current/routing-dsl/exception-handling.html)
* [X] Docker Compose deployment
* [X] Kubernetes deployment
* [ ] Permit multi destinations
* [ ] Limit in-memory alerts
* [ ] Add `_id` alert with hashing
* [ ] Index alert into Elasticsearch with the `_id`
* [ ] Get alerts by `Destination`
* [ ] Get alerts by `isPerformed`
* [ ] Get alerts by `ts` ranges
* [ ] Firewall to allow only permitted IPs
* [ ] Swagger interface contract
* [ ] Server HTTPS support (HAProxy)
