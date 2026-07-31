# CHANGELOG

## v1.15

* Blinded TTD timer now shows a pulsing animation instead of "??:XX"
* Session history now shows each session's start date/time (instead of end date/time); expanding a session's details shows its end date/time
* Edit screen now allows editing a session's start and end date/time
* Fixed: "time since last dilation" counted from session start instead of end when a session was ended early via Save & Exit

## v1.14

* Custom sounds 
* Breaks: optionally insert a short rest after each push/swirl switch (New Session → Others), with its own sound
* Improved wide-screen/landscape layout, including correct sizing in split-screen and multi-window
* Updated to newer Android UI framework and libraries
* Fixed: "Last session" time is updated when the session actually ended
* Fixed a crash when vibrating on Android 11 and 12 devices

## v1.13

* Blinded TTD timer: optionally hide the minutes during TTD (configurable in New Session → Others)
* Tags introduced (Settings → Tags) and apply them to sessions
* Notes: add a free-form note to any session
* Tags and notes are editable in Session History via a dedicated Edit screen
* Option to add tags and a note immediately after a session ends ("Add tags/note at end" in New Session → Others)
* Statistics: filter by tags (Statistics → Filter → Tags)
* History: edit screen now allows editing the total session length
* Fixed status bar color not following the selected theme on non-Pixel devices
* Fixed depth being lost on save when the device locale uses comma as decimal separator
* App icon shown in the timer notification

## v1.12

* Support for wide-screen usage.

## v1.11

* Added "Stacked TTD" chart
* New filtering options in statistics (includes milestones)
* When app is killed, allow to resume the session
* Fixed session being killed by power management; the app now holds a CPU wake lock for the duration of the session
* Added "Keep CPU awake" setting (enabled by default) to control the wake lock
* Milestone comments can now be edited
* History: expanded session details are no longer lost when scrolling
* Sound settings: separate toggles for "Switch beeps" and "Phase fanfare"

## v1.10

* You can define "milestones" in your progress.
* Improvement in charts visualization and linear regression computing when milestones are defined
* History allows to edit records
* Sorting history by date/length/ttds
* New sound/vibration settings
* Click on notification puts the app into the foreground.
* Optimization of "New session screen" for smaller devices

## v1.9

* Support for "static" excercise
* Linear regression trend lines added to all statistics charts
* TTD chart: toggle individual phase sizes (Small/Medium/Large/XL) on/off
* Improved and fixed axis labeling
* Statistics charts now use weighted moving average instead of simple moving average

## v1.8

* Added "TTD vs. Session Gap" scatter charts in statistics (one per phase size)
* Configurable action time (10s, 15s, 20s, 30s); default remains 15s; action time shown in session history.
* Butterfly app import
* Updated calls to vibration API; hopefully fixes some reported problems
* Fixed problem with date picker & time zones
* Fixed TTD chart Y-axis labels showing values up to ~1 minute too low due to truncation
* Fixed session length being inflated when depth is entered after a delay

## v1.7

* Allow to set "Day 0" (date when it all started); Day 0 is part of the export.
* When Day 0 is set, show "Day XX" on the main menu screen where XX is number of days from day 0.
* Draw vertical red line in charts for days that are multiple of 30.
* Improved clarity of charts with many points.
* Added "Session length" chart.
* Small workaround around potential problems with vibrations on Samsung phones.

## v1.6

* Fixed "swipe back" behavior
* Optional countdown to the next session (shown in the main menu; configured in "Settings")

## v1.5

* Statistics screen reworked. Added selection of time intervals for statistics.
* Added charts into statistics
* Added time between dilatations into statistics
* Fixed some potential crash when application is switched
* In cancel session dialog added: "Save & Exit"

## v1.4

* "Screen always on" while session is running
* Custom audio volume settings
* "Fanfare" is played when phase is ended
* Export/Import buttons moved to Session History
* Dark theme
* Saving partial session results in case of application is unepextedly closed

## v1.3

* Sound reworked, it now respects global audio settings
* Show time from the last session
* Depth recording moved after timer
* Fix "Depth reached" on small screens

## v1.2

* Depth recording
* Export format cleaned
* Import implemented

## v1.1

* Fixed problem with time tracking when application is suspended.
* Support of an early finish of a timed phase


## v1.0

* Initial relase
