/*
 * TADNavigationServiceProvider.java
 *
 * Created on December 1, 2006, 3:42 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.onebusaway.android.tad;
import android.location.Location;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import java.util.Locale;

/**
 * This class provides the navigation functionality for the Travel Assistant Device
 *
 * @author Barbeau / Belov
 */
public class TADNavigationServiceProvider implements Runnable, TextToSpeech.OnInitListener {

    public final String TAG = "TADNavServiceProvider";
    public TADProximityCalculator mProxListener;

    private int timeout = 60;  //Timeout value for service provider action (default = 60 seconds);
    private boolean dialogAllowed = false;  //Whether a dialog is allowed with this navigation service provider
    /** TAD Specific variables **/
    private int segmentIndex = 0;  //Index that defines the current segment within the ordered context of a service (i.e. First segment in a service will have index = 0, second segment index = 1, etc.)
    private Segment[] segments;  //Array of segments that are currently being navigated
    private float[] distances; //Array of floats calculated from segments traveled, segment limit = 20.
    private Service service;  //Currently selected transit service that is being navigated

    //private GPSDistanceCalc cp = new GPSDistanceCalc();
    private boolean usecritical = false; //Tells the navigation provider to either use critical points or all points
    private float alertdistance = -1;
    private int diss = 0; //relation for segmentid/distances

    private boolean waitingForConfirm = false;
    private Location currentLocation = null;

    private boolean finished = false;   // Trip has finished.

    private TextToSpeech mTTS;
    /** Creates a new instance of TADNavigationServiceProvider */
    public TADNavigationServiceProvider() {
        Log.d(TAG,"Creating TAD Navigation Service Provider");
        mTTS = new TextToSpeech(Application.get().getApplicationContext(), this);
    }

    /**
     * Initialize tad proximityListener
     * Proximity listener will be created only upon selection of service to navigate
     */
    private void lazyProxInitialization() {
        //Re-initializes the proximityListener
        Log.d(TAG,"Proximity Listener initializing...");
        mProxListener = null;
        mProxListener = new TADProximityCalculator(this);  //Create the proximitylistener

    }

    /**
     * Adds dsegment distance to the array storing all distances
     * @param d
     */
    public void addDistance(float d) {
        distances[diss] = d;
        diss++;
    }

    /**
     * Returns true if trip is done.
     * @return
     */
    public boolean getFinished()
    {
        return finished;
    }

    /**
     * Returns all stored distances for currently navigated service
     * @return
     */
    public float[] getDistances() {
        return distances;
    }

    /**
     * Returns the ID of the current active service
     * @return
     */
    public int getServiceID() {
        if (service != null) {
            return service.getIdService();
        } else {
            return -1;  //If a service isn't currently being navigated, then return -1 as a default value

        }
    }

    /**
     * Returns the ID of the currently navigated segment
     * @return
     */
    public int getSegmentID() {
        try {
            if (segments != null) {
                return segments[segmentIndex].getIdSegment();
            } else {
                return -1;  //If a segment isn't currently being navigated, then return -1 as a default value

            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Could not get Segment ID");
            return -1;
        }
    }

    /**
     * Returns the index of the current segment
     * @return
     */
    public int getSegmentIndex() {
        return segmentIndex;
    }


    /**
     *  This method sets up the NavigationProvider to provide navigations instructions to a particular location
     * @param start
     * @param destination
     */
    public void navigate(Location start, Location destination) {
        mProxListener.listenForCoords(destination, null, null);  //Set proximity listener to listen for coords
    }

    /**
     * Navigates a transit Service which is composed of these Segments
     * @param service
     * @param segments
     */
    public void navigate(Service service, Segment[] segments) {

        Log.d(TAG,"Starting navigation for service");
        //Create a new istance and rewrite the old one with a blank slate of ProximityListener
        lazyProxInitialization();
        service = service;
        segments = segments;
        segmentIndex = 0;
        diss = 0;
        distances = new float[segments.length];
        Log.d(TAG,"Segments Length: " + segments.length);
        //Create new coordinate object using the "Ring" coordinates as specified by the TAD web site and server
        Location coords = segments[segmentIndex].getBeforeLocation();
        Location lastcoords = segments[segmentIndex].getToLocation();
        Location firstcoords = segments[segmentIndex].getFromLocation();

        alertdistance = this.segments[segmentIndex].getAlertDistance();
        //Have proximity listener listen for the "Ring" location
        mProxListener.listenForDistance(alertdistance);
        mProxListener.listenForCoords(coords, lastcoords, firstcoords);
        mProxListener.ready = false;
        mProxListener.trigger = false;
    }


    /**
     * Sets the boolean representing the availability of dialog for this navigation service provider
     * @param allowed
     */
    public void setDialogAllowed(boolean allowed) {
        this.dialogAllowed = allowed;
    }

    /**
     * Resets any current routes which might be currently navigated
     */
    public void reset() {

        mProxListener.listenForCoords(null, null, null);
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Sets the radius of detection for the ProximityListener
     * @param radius
     */
    public void setRadius(float radius) {
        mProxListener.setRadius(radius);
    }

    /**
     * Determines whether or not there is another segment to be navigated as part of the current transit service that is being navigated
     * @return
     */
    public boolean hasMoreSegments() {
        Log.d(TAG, "Checking if service has more segments left");
        //If there are still more segments to be navigated as part of this transit service, return true.  Otherwise return false
        if ((segments == null) || (segmentIndex >= (segments.length - 1))) {
            Log.d(TAG, "Segments Index: " + segmentIndex + " Segments Length: " + (segments.length - 1));
            Log.d(TAG, "%%%%%%%%%%%%%%% No more Segments Left %%%%%%%%%%%%%%%%%%%%%");

            return false; //No more segments exist

        }
        Log.d(TAG,"More segments left, returning true");
        return true; //Additional segments still need to be navigated as part of this service

    }

    /**
     * Tells the NavigationProvider to navigate the next segment in the queue
     */
    private void navigateNextSegment() {
        Log.d(TAG, "Attempting to navigate next segment");
        if ((segments != null) && (segmentIndex < (segments.length))) {
            //Increment segment index
            Log.d(TAG,"Setting previous segment to null!");
            segments[segmentIndex] = null; // - Set unused object to null to enable it for garbage collection.
            Log.d(TAG,"getting coords");
            segmentIndex++;
            //Create new coordinate object using the "Ring" coordinates as specified by the TAD web site and server
            Segment segment = segments[segmentIndex];
            alertdistance = segment.getAlertDistance();
            //Have proximity listener listen for the "Ring" location
            mProxListener.listenForDistance(alertdistance);
            mProxListener.listenForCoords(segment.getBeforeLocation(), segment.getToLocation(), segment.getFromLocation());
             Log.d(TAG,"Proximlistener parameters were set!");
            //Try to cancel any existing registrations of the AVLServiceProvider
            try{
                //midlet.avlProvider.setAVLDataListener(null, null, null, "", "");
            }catch(Exception e){
                Log.d(TAG,"WARNING - error attempting to cancel any existing AVLServiceProviders");
            }

            //Set AVLServiceProvider for new segment
             Log.d(TAG,"Setting agency properties++++++...");
            /*TransitAgencyAVLProperties agencyProperties = new TransitAgencyAVLProperties(segments[segmentIndex].getAgencyFeedIDTAD());
            Log.d(TAG,"Setting avl data listener!");
            if (this.midlet.avlProvider != null) {
                this.midlet.avlProvider.setAVLDataListener(this.midlet.avlListener, this.midlet.avlListener, agencyProperties, segments[segmentIndex].getIdStopFromTransitAgencyGTFS(), segments[segmentIndex].getRoute_IDGTFS());
                this.midlet.avlProvider.setArrivalReminder(midlet.AVL_ARRIVAL_REMINDER_TIME);
                //Log.d(TAG,"AVL data listener: " + this.midlet.avlProvider.getAVLDataListener());
                Log.d(TAG,"ARrival reminder: " + this.midlet.avlProvider.getArrivalReminder());
                Log.d(TAG,"Properties were set!");
            } else {
                Log.d(TAG,"Tjhe avl provider was null!");
            }*/
            Log.d(TAG,"Trying yto update route info!");
            //Update route info on screen

            /*if (segments[segmentIndex].getTrip_headsignGTFS() != null) {
                //Show headsign, since headsign info exists
                this.midlet.getStringItem().setText(segments[segmentIndex].getTrip_headsignGTFS().trim());
            } else {
                //No headsign information exists.  Just show route number
                this.midlet.getStringItem().setText("ROUTE " + segments[segmentIndex].getRoute_IDGTFS().trim());  //Set route name
            }*/
            Log.d(TAG,"Route info was updated!");

        //NEW - Set boolean flag to allow navigation again for current segment
        } else {
            //throw new ServiceException("The NavigationProvider currently has no more segments to navigate.");
        }
    }

    /**
     * Is called from LocationListener.locationUpdated() in inorder to supply the Navigation Provider with the most recent location
     */
    public void locationUpdated(Location l) {
        currentLocation = l;
        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * This method executes all thread-based navigation services when a new location is calculated
     * @param CP
     */
    public synchronized void setuseCP(boolean CP) {

        usecritical = CP;
    }

    public synchronized boolean getuseCP() {
        return usecritical;
    }
    
    
    private int sendCounter = 0;

    public void run() {

        try {

            //Get the last known location from location listener and send it to proximity listener.

            //sends UDP data to server - Duplicated in LocListener
          /*  JSR179LocationData loc = new JSR179LocationData(this.locListener.getLocationProvider(), candidate);
            this.communicator.sendData(z);  //Send location data to the server   
             */
            int loc = mProxListener.checkProximityAll(currentLocation);
               
            //if ((sendCounter++ % 4 == 0) || (loc.getAlert() == 1) || (loc.getAlert() == 2)) {
                if (loc == 1) {
                    Log.d(TAG, "Alert 1");
                } else if (loc == 2) {
                    Log.d(TAG, "Alert 2");
                }
            //} else {
                
            //}

        } catch (Exception e) {
            //    e.printStackTrace();
            Log.d(TAG, "Proximity Listener not Initialized ");
            //Log.d(TAG, "Error in NSP, while attempting to send CP in UDP");
        }
    }

    public void skipSegment() {
        try {

            if (hasMoreSegments()) {
                Log.d(TAG, "About to switch segment - from skipSegment");
                navigateNextSegment(); //Uncomment this line to allow navigation on multiple segments within one service (chained segments)
                mProxListener.setReady(false); //Reset the "get ready" notification alert
            } else {
                Log.d(TAG, "No more segments!");
            }
            mProxListener.setTrigger(false); //Reset the proximity notification alert
        } catch (Exception e) {
            Log.d(TAG,"Error in TADProximityListener.proximityEvent(): " + e);
            e.printStackTrace(); // See what happens!!!!!!!!
        }
    }

    /**
     * This class is used to detect Proximity to a latitude and longitude location.  The JSR179 ProximityListener is not used for this implementation on iDEN phones
     * because it is not currently reliable.
     * This class was moved to an inner class of TADNavigationServiceProvider 2-5-2007 because it must call methods in the NavigationServiceProvider
     * that should remain private, and also because its the only proper way to get the NavigationServiceProvider and Listener to work together properly.
     *
     *
     * @author Sean J. Barbeau, modified by Belov
     */
    public class TADProximityCalculator {
        //TADMIDlet_Converted midlet;  //Holds references to the main MIDlet that is executing
        TADNavigationServiceProvider navProvider;  //Holds reference to main navigation provider for TAD
        //**  Proximity Listener variables **/
        private float radius = 160;  //Defines radius (in meters) for which the Proximity listener should be triggered (Default = 50)
        private float readyradius = 300; //Defines radius(in meters) for which the Proximity listener should trigger "Get Ready Alert"
        private boolean trigger = false;  //Defines whether the Proximity Listener has been triggered (true) or not (false)
        private Location secondtolastcoords = null;  //Tests distance from registered location w/ ProximityListener manually
        private Location lastcoords = null; //Coordinates of the final bus stop of the segment
        private Location firstcoords = null; //Coordinates of the first bus stop of the segment
        private float distance = -1;  //Actual known traveled distance loaded from segment object
        private float directdistance = -1; //Direct distance to second to last stop coords, used for radius detection
        private float endistance = -1; //Direct distance to last bus stop coords, used for segment navigation
        private boolean ready = false; //Has get ready alert been played?
        private boolean m100_a,  m50_a,  m20_a,  m20_d,  m50_d,  m100_d = false;  //Varibales for handling arrival/departure from 2nd to last stop

        /**
         * Creates a new instance of TADProximityCalculator
         * @param navProvider
         */
        public TADProximityCalculator(TADNavigationServiceProvider navProvider) {
            this.navProvider = navProvider;
            Log.d(TAG,"Initializing TADProximityCalculator");
            //this.radius = Float.parseFloat(this.midlet.getAppProperty("PROX_RADIUS"));
            //this.readyradius = Float.parseFloat(midlet.getAppProperty("READY_RADIUS"));
        }

        /** Getter method for radius value of ProximityListener **/
        public float getRadius() {
            return radius;
        }

        /** Setter method for radius value of ProximityListener **/
        public void setRadius(float radius) {
            this.radius = radius;
        }

        /** ProximityListener Functions **/
        public void monitoringStateChanged(boolean value) {
            //Fired when the monitoring of the ProximityListener state changes (is or is NOT active)
            Log.d(TAG,"Fired ProximityListener.monitoringStateChanged()...");
        }

        /**
         * Resets triggers for proximityEvent
         * @param t
         */
        public void setTrigger(boolean t) {
            trigger = ready = t;
        }

        /**
         * Fires proximity events based on selection parameters
         * @param selection - checks if the trigger or get ready notifications are called
         * @param t - variable is responsible for differentiating the switch of segment and alert being played.
         * @return
         */
        public boolean proximityEvent(int selection, int t) {
            //*******************************************************************************************************************
            //* This function is fired by the ProximityListener when it detects that it is near a set of registered coordinates *
            //*******************************************************************************************************************
            // Log.d(TAG,"Fired proximityEvent() from ProximityListener object.");

            //NEW - if statement that encompases rest of method to check if navProvider has triggered navListener before for this coordinate
            if (selection == 0) {
                if (trigger == false) {
                    trigger = true;
                    Log.d(TAG,"Proximity Event fired");
                    if (navProvider.hasMoreSegments()) {
                        if (t == 0) {
                        Log.d(TAG,"Alert 1 Screen showed to rider");
                            waitingForConfirm = true;
                            // GET READY.
                            navProvider.UpdateInterface(3);
                            Log.d(TAG,"Calling way point reached!");
                            //this.navProvider.navlistener.waypointReached(this.lastcoords);
                            return true;
                        }
                        if (t == 1) {
                            try {
                                Log.d(TAG,"About to switch segment - from Proximity Event");
                                navProvider.navigateNextSegment(); //Uncomment this line to allow navigation on multiple segments within one service (chained segments)

                                ready = false; //Reset the "get ready" notification alert

                                trigger = false; //Reset the proximity notification alert

                            } catch (Exception e) {
                                Log.d(TAG,"Error in TADProximityListener.proximityEvent(): " + e);
                            }
                        }
                    } else {
                        Log.d(TAG,"Got to last stop ");
                        if (t == 0) {
                            Log.d(TAG,"Alert 1 screen before last stop");
                            waitingForConfirm = true;
                            navProvider.UpdateInterface(3);
                            Log.d (TAG,"Calling destination reached...");
                            finished = true;
                            return true;
                        }
                        if (t == 1) {
                            long time = System.currentTimeMillis();
                            Log.d(TAG,"Ending trip, going back to services");
                            finished = true;
                            try {
                                navProvider.service = null;
                                navProvider.segments = null;
                                navProvider.segmentIndex = 0;
                                Log.d(TAG,"Time setting variables to null: " + (System.currentTimeMillis() - time));
                                time = System.currentTimeMillis();
                                //this.midlet.getDisplay().setCurrent(this.midlet.getChooseService1());
                                //this.midlet.communicator.clientNotification();
                                Log.d(TAG,"Time showing GUI and notifying communicator: " + (System.currentTimeMillis() - time));
                                time = System.currentTimeMillis();
                                //Cancel any registration of the AVLServiceProvider & Listener
                                try {
                                    //midlet.avlProvider.setAVLDataListener(null, null, null, "", "");
                                    //Reset AVLlistener, since the user is finished traveling and doesn't need AVL info
                                    //midlet.avlListener.reset();
                                } catch (Exception e) {
                                    Log.d(TAG,"Error canceling the AVLServiceListener registration: " + e);
                                }
                                Log.d(TAG,"Time setting AVL Data Listener: " + (System.currentTimeMillis() - time));
                                time = System.currentTimeMillis();


                            } catch (Exception e) {
                                Log.d(TAG,"Error while sending distances to server");
                            }
                        }
                    }
                }
            } else if (selection == 1) {
                if (ready == false) {
                    ready = true;
                    navProvider.UpdateInterface(3);
                    return true;
                }
            }

            return false;
        }

        /**
         * Test function used to register a location to detect proximity to
         * @param coords
         * @param last
         * @param first
         */
        public void listenForCoords(Location coords, Location last, Location first) {
            secondtolastcoords = coords;
            lastcoords = last;
            firstcoords = first;
            //Reset distance if the manual listener is reset
            if (coords == null) {
                directdistance = -1;
            }
            if (last == null) {
                endistance = -1;
            }

        }

        /**
         * Sets the "known" distance for the segment
         * @param d
         */
        public void listenForDistance(float d) {
            this.distance = d;
        }

        /**
         * Fire proximity event to switch segment or go back to service menu
         * when the final stop of the segment or service is reached
         * stop_type = 0; -> final stop detection
         * stop_type = 1; -> second to last stop detection
         * speed = current speed of the bus;
         * @param distance_d
         * @param stop_type
         * @param speed
         * @return
         */
        public boolean StopDetector(float distance_d, int stop_type, float speed) {
            
            /* TODO: This comment was comented to avoid segment switching when the rider is
             * 20 meters away from the bus stop.
            if ((distance_d < 20) && (distance_d != -1) && stop_type == 0) {

                Log.d(TAG,"About to fire Proximity Event from Last Stop Detected");
                this.trigger = false;
                this.proximityEvent(0, 1);
                return true;

            } else */
            Log.d(TAG,"Detecting stop. distance_d=" +
                    distance_d + ". stop_type=" + stop_type + " speed=" + speed);
            if  (stop_type == 1) {
                /* Check if the bus is on the second to last stop */
                if ((distance_d > 50) && (distance_d < 100) && (distance_d != -1) && !m100_a) {
                    m100_a = true;
                    Log.d(TAG,"Case 1: false");
                    return false;
                }
                if ((distance_d > 20) && (distance_d < 50) && (distance_d != -1) && !m50_a) {
                    m50_a = true;
                    Log.d(TAG,"Case 2: false");
                    return false;
                }
                if ((distance_d < 20) && (distance_d != -1) && !m20_a) {                    
                    m20_a = true;
                    if (speed > 15) {
                        Log.d(TAG,"Case 3: true");
                        return true;
                    }
                    Log.d(TAG,"Case 3: false");
                    return false;
                }
                if ((distance_d < 20) && (distance_d != -1) && m20_a && !m20_d) {                    
                    m20_d = true;
                    if (speed < 10) {
                        Log.d(TAG,"Case 4: false");
                        return false;
                    } else if (speed > 15) {
                        Log.d(TAG,"Case 4: true");
                        return true;
                    }
                }
                if ((distance_d > 20) && (distance_d < 50) && (distance_d != -1) && !m50_d && (m20_d || m20_a)) {
                    m50_d = true;
                    Log.d(TAG,"Case 5: true");
                    return true;
                }


            }            
            return false;
        }

        /**
         * These method checks the proximity to the registered coordinates 
         * @param currentLocation
         * @return
         */
        private int checkProximityAll(Location currentLocation) {
            if (!waitingForConfirm) {
                //re-calculate the distance to the final bus stop from the current location
                endistance = lastcoords.distanceTo(currentLocation);
                //re-calculate the distance to second to last bus stop from the current location
                directdistance = secondtolastcoords.distanceTo(currentLocation);
                Log.d(TAG,"Second to last stop coordinates: " + secondtolastcoords.getLatitude() + ", " + secondtolastcoords.getLongitude());
                try {
                    navProvider.UpdateInterface(1);

                } catch (Exception e3) {
                    Log.d(TAG,"Warning - Could not set Distance...");
                }
                if (directdistance < 250) {
                    //Fire proximity event for getting ready 100 meters prior to 2nd to last stop
                    if (proximityEvent(1, -1)) {
                        navProvider.UpdateInterface(2);
                        Log.d(TAG, "-----Get ready!");
                        return 2; //Get ready alert played
                    }
                }
                if (StopDetector(directdistance, 1, currentLocation.getSpeed())) {
                    //   if (this.endistance < 160) {
                    //Fire proximity event for getting off the bus

                    if (proximityEvent(0, 0)) {
                        navProvider.UpdateInterface(3);
                        Log.d(TAG, "-----Get off the bus!");
                        finished = true;
                        return 1; // Get off bus alert played

                    }

                }
                //check if near final stop and reset the 2nd to last stop detection variables.
                // This was replaced by the method resetVariablesAfterSegmentSwitching
    /*            if (StopDetector(this.endistance, 0, 0)) {
                    m100_a = false;
                    m50_a = false;
                    m20_a = false;
                    m20_d = false;
                    m50_d = false;
                    m100_d = false;
                }*/
            }
            return 0; //No alerts played.
        }
        
        
        public void resetVariablesAfterSegmentSwitching() {
            Log.d(TAG,"Reseting variables after segment switching!");
            m100_a = false;
            m50_a = false;
            m20_a = false;
            m20_d = false;
            m50_d = false;
            m100_d = false;
        }
        
        public void setOnlyTrigger(boolean value) {
            this.trigger = value;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }
    }
    
    public void setWaitingForConfirm(boolean waitingForConfirm) {
        this.waitingForConfirm = waitingForConfirm;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTTS.setLanguage(Locale.getDefault());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mTTS.speak(Application.get().getString(R.string.voice_starting_trip), TextToSpeech.QUEUE_FLUSH, null, "TRIPMESSAGE");
            } else {
                mTTS.speak(Application.get().getString(R.string.voice_starting_trip), TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    // Update Interface
    // e.g, notifications, speak, etc.
    private void UpdateInterface (int status)
    {
        if (status == 1) {          // General status update.

        } else if (status == 2) {   // Get ready to pack
            Speak(Application.get().getString(R.string.voice_get_ready));
        } else if (status == 3) {   // Pull the cord
            Speak(Application.get().getString(R.string.voice_pull_cord));
        }
    }

    // Speak specified message out loud using TTS.
    private void Speak (String message)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTTS.speak(Application.get().getString(R.string.voice_starting_trip), TextToSpeech.QUEUE_FLUSH, null, "TRIPMESSAGE");
        } else {
            mTTS.speak(Application.get().getString(R.string.voice_starting_trip), TextToSpeech.QUEUE_FLUSH, null);
        }
    }
}