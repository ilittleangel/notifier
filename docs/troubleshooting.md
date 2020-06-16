# Troubleshooting Errors

* [Malformed requests](#malformed-requests)
* [Ftp errors](#ftp-errors)
* [Slack errors](#slack-errors)
* [Multi destination errors](#multi-destination-errors)



## Malformed requests

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



## Ftp errors

* When something wrong happens with a Ftp alert the body response will be as following:
```json
{
    "alert": {
        "destination": "ftp",
        "message": "......",
        "properties": {
            ...
        }
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



## Slack errors

* When something wrong happens with a Slack alert the body response will be as following:
```json
{
    "alert": {
        "destination": "slack",
        "message": "......",
        "properties": {
            ...
        }
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
https://hooks.slaack.com/services/TGYMK17R2/BW144ANYL/ssSOopvymUUAJnMflgu8LFdT
 ^                          ^         ^         ^                 ^
 1                          2         3         4                 5
```


## Multi destination errors

* If one of destinations failed, the HTTP response was a `400 Bad Request`
* The Json body response will be as following:
```json
{
    "alert": {
        "destination": [
            "slack",
            "ftp"
        ],
        "message": "......",
        "properties": {
            ...
        },
        "ts": "2020-03-25T17:44:44.112Z"
    },
    "isPerformed": false,
    "status": "Slack webhook request success [status=200 OK] [ok]; Ftp alert failed with IOResult[error=Connection refused (Connection refused), count=0]",
    "description": "alert received but not performed!"
}
```
* The `isPerformed` field will be `false`
* The `description` field will be `alert received but not performed!`
* The `status` field will be the concatenation of the `error` message received from each destination in the same order.

