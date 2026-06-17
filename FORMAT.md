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
| `exportDate` | String | ISO 8601 timestamp of when the export was created. |
| `appVersion` | String | The version of the PushSwirl app used to create the export. |
| `day0Date` | String? | *Optional.* ISO 8601 timestamp of the Day 0 start date. Absent when no start date is set. Restored on import when present; absent in exports from older app versions. |
| `tags` | Array? | *Optional.* List of Tag objects defining all tags used in this export. Sessions reference tags by UUID via their `tagIds` field. Absent in exports from older app versions. |
| `sessions` | Array | A list of Session objects. |

### Session Object (`SessionExport`) 
| Key | Type | Description |
| :--- | :--- | :--- |
| `id` | String | A unique UUID identifying the session. |
| `timestamp` | String | ISO 8601 timestamp (`yyyy-MM-dd'T'HH:mm:ssXXX`) of when the session occurred. |
| `totalSeconds` | Long | The cumulative duration of the entire session in seconds. |
| `phases` | Array | A list of Phase objects completed during this session. |
| `tagIds` | Array? | *Optional.* List of Tag UUIDs applied to this session. Absent when no tags are assigned. |
| `note` | String? | *Optional.* Free-form note for this session. Absent when empty. |

### Phase Object (`PhaseData`) 
| Key | Type | Description |
| :--- | :--- | :--- |
| `size` | String | The size category (`SMALL`, `MEDIUM`, `LARGE`, `XL`). |
| `ttdSeconds` | Long | "TTD": Seconds taken before the dilation timer started. |
| `dilationMinutes` | Integer | The planned duration for this phase in minutes. |
| `earlyFinishSecondsRemaining` | Integer? | *Optional.* If the phase was ended early, the number of seconds remaining on the clock. |
| `depthCm` | Float? | *Optional.* The recorded depth measurement in centimeters. |

### Tag Object (`TagExport`)
| Key | Type | Description |
| :--- | :--- | :--- |
| `id` | String | UUID identifying the tag. |
| `name` | String | Display name of the tag. |
| `color` | String | Color of the tag. One of: `RED`, `ORANGE`, `GREEN`, `BLUE`. |

---

## Backward Compatibility

All optional fields (`day0Date`, `tags`, `tagIds`, `note`) are absent from exports created by older versions of the app. The app handles missing fields gracefully on import:
- Missing `tags` / `tagIds` / `note`: sessions are imported with no tags and a blank note.
- Exports from newer app versions imported by older versions: unknown fields are silently ignored by the JSON parser.

On import, any tag from the `tags` array whose UUID does not already exist locally is added to the local tag list.

---

## Example Export
```json
{
  "exportDate": "2026-02-07T09:21:48+01:00",
  "appVersion": "1.12",
  "day0Date": "2026-01-01T00:00:00+01:00",
  "tags": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Good",
      "color": "GREEN"
    },
    {
      "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "name": "Pain",
      "color": "RED"
    }
  ],
  "sessions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "timestamp": "2026-02-06T18:30:00+01:00",
      "totalSeconds": 1545,
      "tagIds": ["a1b2c3d4-e5f6-7890-abcd-ef1234567890"],
      "note": "Felt easier today.",
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
