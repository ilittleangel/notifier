# Notifier

HTTP/Rest server to receive and centralize alarms and be able to send to different destinations (Email, Slack, Ftp, ..)

* [Technologies](#technologies)
* [Building Notifier](#building-notifier)
* [Running Notifier](#running-notifier)
* [Running with docker](#running-with-docker)
* [Notifier configuration via `notifier.conf`](#notifier-configuration-via-notifierconf)
* [Notifier configuration via `admin` endpoint](#notifier-configuration-via-admin-endpoint)
  - [The in-memory alerts capacity](#the-in-memory-alerts-capacity)
  - [The logging level](#the-logging-level)
* [The Alert resource](#the-alert-resource)
* [The Performed Alert resource](#the-performed-alert-resource)
* [Send Ftp alerts](#send-ftp-alerts)
* [Send Slack alerts](#send-slack-alerts)
* [Send Email alerts](#send-email-alerts)
* [Send Multi destinations](#send-multi-destinations)
* [Troubleshooting](#troubleshooting)
* [TODOs](#todos)


## Technologies

* [Scala 2.13](https://www.scala-lang.org/api/2.13.1/)
* [Akka HTTP Server API](https://doc.akka.io/docs/akka-http/current/server-side/index.html)
* [Akka HTTP Client API](https://doc.akka.io/docs/akka-http/current/client-side/index.html)
* [Json Support](https://doc.akka.io/docs/akka-http/current/common/json-support.html)
* [Docker](https://www.docker.com/)
* [Kubernetes](https://kubernetes.io/)




## Building Notifier

Notifier is built using [Gradle](https://docs.gradle.org/current/userguide/userguide.html). 
To build the software from the command line just run the following from the root of the project:

```sh
./gradlew build
```

## Running Notifier

To start the server with `gradle run`, just run the following command from the root of the project:

```sh
./gradlew run --args='--config conf/notifier.conf'
```

The http server will be listening on

```http
http://localhost:8080/notifier/api/v1
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

The config file is HOCON format and we can configure the server and destinations.

```conf
{
  server: {
    bind_address: "0.0.0.0"
    bind_port: 8080
  }

  ftp: {
    username: "notifier"
    password: ${FTP_PASS}
  }

  email {
    server: "smtp.gmail.com"
    port: 587
    user: "you@gmail.com"
    ttls: true
    button: {
      enable: true
      name: "GO to alert"
      link: "https://hostname.com/whatever"
    }
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

**Email**:
* `Email` is a `Destination` for sending emails with an SMTP server.
* `server` is the SMTP host.
* `port` is the SMTP port.
* `user` is the user on the SMTP server and the email address from which the email will be sent.
* `pass` (optional) is the password of the `user`. If not set, there will be no authentication against the server.
* `ttls` is a boolean property that allow to send emails with `STARTTLS` command.
* `html.button.enable` is a boolean property to enabling  disabling an HTML button.
* `html.button.name` (optional) is the content of the HTML button, added to the `OPEN` word.
* `html.button.link` (optional) is the link of the button.
>More info about the HTML email button in the [email section](#send-email-alerts).


## Notifier configuration via `admin` endpoint

Another way to setting up some configurations is via `admin` endpoint. At this moment it is a limit resource
because it is only possible to configure the following:

- [The in-memory alerts capacity](#the-in-memory-alerts-capacity)
- [The logging level](#the-logging-level)

### The in-memory alerts capacity

* Endpoint to set a new capacity for the in-memory alerts list

```http
POST /notifier/api/v1/admin/alerts-capacity?capacity=10
```

```json
{
    "reason": "Request of change in-memory alerts list capacity to 10",
    "status": "200 OK"
}
```

* Getting the current alerts capacity

```http
GET /notifier/api/v1/admin/alerts-capacity
```

```json
{
    "capacity": 10
}
```

### The logging level

* Endpoint to set the log level as we want

```http
POST /notifier/api/v1/admin/logging?level=debug
```
> Logging level must be one of [off|error|info|debug|warning]

```json
{
    "reason": "Request of change logging level to 'DEBUG'",
    "status": "200 OK"
}
```

* Getting the current log level

```http
GET /notifier/api/v1/admin/logging
```

```json
{
   "level": "DEBUG"
}
```


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


## The Performed Alert resource

A `Performed Alert` is just an enriched [`Alert`](#the-alert-resource). When an `Alert` is received by the server, 
it is enriched with fields informing if the `Alert` was or wasn't performed and why.

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
```http request
POST /notifier/api/v1/alerts
```

### Request Body

Provide an [`Alert` resource](#the-alert-resource):

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

### Properties

| property    | required  | description                           |
|:------------|:----------|:--------------------------------------|
| `host`      | No        | The host of the FTP server.           |
| `port`      | No        | The port of the FTP server.           |
| `path`      | No        | The file path in the FTP destination. |
| `protocol`  | No        | `ftp` or `sftp`.                      |

> This properties can be configured via `notifier.conf` file but this way
has no preference over the values set in the Json Body request.

> If the `path` exists, the `message` will be appended at the end of file.
If the file does not exist, a new one will be created.


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
   "clientIp": "localhost - 0:0:0:0:0:0:0:1",
    "isPerformed": true,
    "status": "Ftp alert success [value=Done, count=24]",
    "description": "alert received and performed!"
}
```


## Send Slack alerts

### Http Request
```http request
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

### Properties

| property    | required  | description                           |
|:------------|:----------|:--------------------------------------|
| `webhook`   | Yes       | Slack webhook.                        |
| `proxy`     | No        | Http proxy. Default: with no proxy.   |

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
    "clientIp": "localhost - 0:0:0:0:0:0:0:1",
    "isPerformed": true,
    "status": "Slack webhook request success [status=200 OK] [ok]",
    "description": "alert received and performed!"
}
```

## Send Email alerts

### Http Request

```http
POST /notifier/api/v1/alerts
```

### Request Body
```json
{
    "destination": "email",
    "message": ".......",
    "properties": {
        "email_to": "operations@work.com",
        "email_cc": "business1@work.com, business2@work.com",
        "subject": "Notifier alert",
        "button_enable": "true",
        "button_name": "GO to alert",
        "button_link": "https://hostname.com/whatever"
    }
}
```
      
### Properties

| property        | required  | description                                                                         |
|:----------------|:----------|:------------------------------------------------------------------------------------|
| `subject`       | Yes       | The email subject.                                                                  |
| `email_to`      | Yes       | The email recipients TO.                                                            |
| `email_cc`      | No        | The email recipients CC.                                                            |
| `button_enable` | No        | A button in the html email template. Values: [`true`,`false`]. Default: `false`     |
| `button_name`   | No        | The title in the button.                                                            |
| `button_link`   | No        | The link in the button.                                                             |
| `template`      | No        | Possibility to use differences html email templates. Values: [`main`, `other`]. Default: `main` |

> Recipients in the `email_to` and `email_cc` properties can be a one or more. It only has to be separated by a comma.

> If the `button_enable` property is `false`, the button itself will be hidden.

> At this moment is only available two html tamplates to use in the `template` property. They are `main.html` and `other.html`

### Http Response

```json
{
    "alert": {
        "destination": "email",
        "message": ".......",
        "properties": {
            "email_to": "operations@work.com",
            "email_cc": "business1@work.com, business2@work.com",
            "subject": "Notifier alert",
            "button_enable": "true",
            "button_name": "GO to alert",
            "button_link": "https://hostname.com/whatever"
        },
        "ts": "2020-10-15T06:54:32.112Z"
    },
    "clientIp": "localhost - 0:0:0:0:0:0:0:1",
    "description": "alert received and performed!",
    "isPerformed": true,
    "status": "Email alert success"
}
```

    
## Send Multi destinations

### Http Request
```http request
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
* [X] Implement Email destination
* [X] Unit tests with [Route TestKit](https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html)
* [ ] Provide custom [Exception Handling](https://doc.akka.io/docs/akka-http/current/routing-dsl/exception-handling.html)
* [X] Docker Compose deployment
* [X] Kubernetes deployment
* [X] Permit multi destinations
* [X] Limit in-memory alerts
* [ ] Add `_id` alert with hashing
* [ ] Index alert into Elasticsearch with the `_id`
* [ ] Get alerts by `Destination`
* [ ] Get alerts by `isPerformed`
* [ ] Get alerts by `ts` ranges
* [ ] Firewall to allow only permitted IPs
* [ ] Swagger interface contract
* [ ] Server HTTPS support (HAProxy)
* [ ] get the current timeouts via Admin settings endpoint
