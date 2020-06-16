# Notifier

HTTP/Rest server to receive and centralize alarms and be able to send to different destinations (Email, Slack, Ftp, ..)

* [Technologies](#technologies)
* [Building Notifier](#building-notifier)
* [Running Notifier](#running-notifier)
* [Running with docker](#running-with-docker)
* [Notifier configuration via `notifier.conf`](#notifier-configuration-via-notifierconf)
* [The Alert resource](#the-alert-resource)
* [The Performed Alert resource](#the-performed-alert-resource)
* [Send Ftp alerts](#send-ftp-alerts)
* [Send Slack alerts](#send-slack-alerts)
* [Send Multi destinations](#send-multi-destinations)
* [Troubleshooting](#troubleshooting)
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

## Running Notifier

To start the server with `gradle run`, just run the following command from the root of the project:

```shell script
./gradlew run --args='--config conf/notifier.conf'
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
REPOSITORY                    TAG                 IMAGE ID            CREATED             SIZE
angelrojo/notifier            latest              3200e92e8860        46 hours ago        138MB
atmoz/sftp                    latest              6345f82053c6        15 months ago       190MB
openjdk                       8u181-jre-alpine    d4557f2c5b71        16 months ago       83MB
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


## The Alert resource

An **Alert** representation to perform an http request to the Notifier.

### JSON representation
```metadata json
{
    "destination": (string or [string]),
    "message":     string,
    "properties":  { map (key: string, value: string) },
    "ts":          string
}
```

### Fields
| field         | value                                   | description                                                                                                   |
|:--------------|:----------------------------------------|:--------------------------------------------------------------------------------------------------------------|
| `destination` | `string` or `list`                      | A single string or a list. The Notifier **accepts both**. Has to be one of these: [`ftp`, `slack`, `email`]   | 
| `message`     | `string`                                | Whatever message want to inform into destination.                                                             | 
| `properties`  | `map` (key: `string`, value: `string`)  | Map with the necessary properties to perform destination.                                                     | 
| `ts`          | `string`                                | The timestamp with an **ISO-8601** format. This field is **optional**. Current instant is set if not present. | 

>Email not implemented yet


## The Performed Alert resource

A `Performed Alert` is just an enriched [`Alert`](#the-alert-resource). When an `Alert` is received by the server, 
this is enriched with fields that indicate if the `Alert` was performed and why.

### JSON representation
```metadata json
{
    "alert": {
        object (Alert Resource)
    },
    "isPerformed": boolean,
    "status":      string,
    "description": string"
}
```

### Fields
| field         | value                                      | description                                             |
|:--------------|:-------------------------------------------|:--------------------------------------------------------|
| `alert`       | `object` ([`Alert`](#the-alert-resource))  | The Alert resource in Json representation.              | 
| `isPerformed` | `boolean`                                  | If the Alert will be able to perform.                   | 
| `status`      | `string`                                   | The status of try to send the Alert to the destination. | 
| `description` | `string`                                   | A static message of if the alert was performed or not.  | 

> `status` field could be a successful message or a failed message in case of failure. 

> `description` field can be one of these [`alert received and performed!`, `alert received but not performed!`].


## Send Ftp alerts

### Http Request 
```
POST /notifier/api/v1/alerts
```

### Request Body

Provide an [`Alert` resource](#the-alert-resource):

```metadata json
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

### Http Response

If successful, this method returns a `200 OK` http status and response body with a json 
representation of a [`Performed Alert`](#the-performed-alert-resource), or an [error message](docs/troubleshooting.md).

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


## Send Slack alerts

### Http Request
```
POST /notifier/api/v1/alerts
```

### Request Body

Provide an [`Alert` resource](#the-alert-resource):

```json
{
    "destination": "slack",
    "message": "......",
    "properties": {
        "webhook": "https://hooks.slack.com/services/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"
    }
}
```

### Http Response

If successful, this method returns a `200 OK` http status and response body with a json
representation of a [`Performed Alert`](#the-performed-alert-resource), or an [error message](docs/troubleshooting.md).

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
    "status": "Slack webhook request success [status=200 OK] [ok]",
    "description": "alert received and performed!"
}
```
    
## Send Multi destinations

### Http Request
```
POST /notifier/api/v1/alerts
```

### Request Body

Provide an [`Alert` resource](#the-alert-resource):

```json
{
    "destination": [
        "slack",
        "ftp"
    ],
    "message": "......",
    "properties": {
        "webhook": "https://hooks.slack.com/services/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",
        "host": "sftp-server",
        "port": "22",
        "protocol": "sftp",
        "path": "/upload/data.txt"
    }
}
```

### Http Response

If successful, this method returns a `200 OK` http status and response body with a json
representation of a [`Performed Alert`](#the-performed-alert-resource), or an [error message](docs/troubleshooting.md).

```json
{
    "alert": {
        "destination": [
            "slack",
            "ftp"
        ],
        "message": "......",
        "properties": {
            "webhook": "https://hooks.slack.com/services/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",
            "host": "sftp-server",
            "port": "22",
            "protocol": "sftp",
            "path": "/upload/data.txt"
        },
        "ts": "2020-03-25T17:44:44.112Z"
    },
    "isPerformed": true,
    "status": "Slack webhook request success [status=200 OK] [ok]; Ftp alert success [value=Done, count=16]",
    "description": "alert received and performed!"
}
```



## Troubleshooting

When somethings wrong occurs see [troubleshooting section](docs/troubleshooting.md).



## TODOs

* [X] Implement Slack destination (akka HTTP client API)
* [X] Implement Ftp destination (FTP/SFTP client)
* [ ] Implement Email destination
* [X] Unit tests with [Route TestKit](https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html)
* [ ] Provide custom [Exception Handling](https://doc.akka.io/docs/akka-http/current/routing-dsl/exception-handling.html)
* [X] Docker Compose deployment
* [X] Kubernetes deployment
* [X] Permit multi destinations
* [ ] Limit in-memory alerts
* [ ] Add `_id` alert with hashing
* [ ] Index alert into Elasticsearch with the `_id`
* [ ] Get alerts by `Destination`
* [ ] Get alerts by `isPerformed`
* [ ] Get alerts by `ts` ranges
* [ ] Firewall to allow only permitted IPs
* [ ] Swagger interface contract
* [ ] Server HTTPS support (HAProxy)
