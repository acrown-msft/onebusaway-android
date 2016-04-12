**Introduction**

**Usage**
1. View the Trip Status of a desired trip.
2. Long tap the desired destination stop.
3. Confirm that you want to start this trip.
4. TAD will be launched as a service in the background.
5. A real-time notification is shown at all times.
6. A second "Get Ready" notification is triggered when nearing the second
to last stop.
7. A final "Pull the cord" notification is triggered when nearing the
destination stop.

**How It Works**
A Service is created when the user begins a trip, this service listens
for location updates and passes the locations to its instance of 
TADServiceNavigationProvider each time. TADServiceProvider is responsible
for computing the statuses of the trips and issuing notifications/TTS
meesages. Once the TADServiceNavProvider is completed, the TAD Service
will stop itself.

***GPS Logging***
When the BuildConfig "TAD_GPS_LOGGING" flag is set to true, the TAD 
Service will log all co-ordinates it receives during the trip and 30
seconds after the trip has ended. The log file is a CSV file written to
the "TADLog" folder on your external storage root directory. The filename
format is <TRIPID>_<DEST_STOP_ID>.txt._