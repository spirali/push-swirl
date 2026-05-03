# CHANGELOG

## v1.8

* Configurable action time (10s, 15s, 20s, 30s); default remains 15s.
* Action time shown in session history.
* Updated calls to vibration API; hopefully fixes some reported problems
* Fixed problem with date picker & time zones
* Added "TTD vs. Session Gap" scatter charts in statistics (one per phase size)
* Fixed TTD chart Y-axis labels showing values up to ~1 minute too low due to truncation

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
