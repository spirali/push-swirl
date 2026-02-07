# PushSwirl Export Format Documentation

This documentation outlines the JSON structure used by the PushSwirl application for exporting and importing session data.

---

## File Overview
The export file is a **UTF-8 encoded JSON** file. It contains metadata about the export event and a list of all recorded sessions, including detailed breakdown of phases, timing, and optional measurements.

---

## Data Schema

### Root Object (`ExportData`) 
| Key | Type | Description |
| :--- | :--- | :--- |
| `exportDate` | String | ISO 8601 timestamp of when the export was created.  |
| `appVersion` | String | The version of the PushSwirl app used to create the export.  |
| `sessions` | Array | A list of Session objects.  |

### Session Object (`SessionExport`) 
| Key | Type | Description |
| :--- | :--- | :--- |
| `id` | String | A unique UUID identifying the session.  |
| `timestamp` | String | ISO 8601 timestamp (`yyyy-MM-dd'T'HH:mm:ssXXX`) of when the session occurred.  |
| `totalSeconds` | Long | The cumulative duration of the entire session in seconds.  |
| `phases` | Array | A list of Phase objects completed during this session.  |

> **Note:** The `SessionConfig` (user settings for that specific session) is not exported directly. Upon import, the app reconstructs the configuration by analyzing the active phases in the list. 

### Phase Object (`PhaseData`) 
| Key | Type | Description |
| :--- | :--- | :--- |
| `size` | String | The size category (`SMALL`, `MEDIUM`, `LARGE`, `XL`).  |
| `ttdSeconds` | Long | "Time to Dilation": Seconds taken before the dilation timer started.  |
| `dilationMinutes` | Integer | The planned duration for this phase in minutes.  |
| `earlyFinishSecondsRemaining` | Integer? | *Optional.* If the phase was ended early, the number of seconds remaining on the clock.  |
| `depthCm` | Float? | *Optional.* The recorded depth measurement in centimeters.  |

---

## Example Export
```json
{
  "exportDate": "2026-02-07T09:21:48+01:00",
  "appVersion": "1.0.4",
  "totalSessions": 1,
  "sessions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "timestamp": "2026-02-06T18:30:00+01:00",
      "totalSeconds": 1545,
      "phases": [
        {
          "size": "MEDIUM",
          "ttdSeconds": 600,
          "dilationMinutes": 15,
          "depthCm": 14.5
        },
        {
          "size": "LARGE",
          "ttdSeconds": 362,
          "dilationMinutes": 10,
          "earlyFinishSecondsRemaining": 120,
          "depthCm": 14.0
        }
      ]
    }
  ]
}
```

