package com.example.studyspot;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
// Imports for configuring Firebase Firestore settings.
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings; // Specifically for configuring persistent cache.

/**
 * MyStudySpotApplication is the custom Application class for this StudySpot app.
 * This class serves as the main entry point when the application starts.
 * My primary use for this class is to initialize global application-level configurations,
 * particularly for Firebase services such as Firebase core, Firestore offline persistence,
 * and Firebase App Check. This ensures these services are ready before any activities launch.
 */
public class MyStudySpotApplication extends Application {
    // Log tag for identifying messages from this Application class.
    private static final String TAG = "MyStudySpotApp";

    /**
     * Called when the application is starting, before any other Ecomponent of the application is created.
     * I use this lifecycle method to perform essential initializations.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate: Initializing application-level services.");

        // Initialize Firebase. This is the foundational step required to use any Firebase service
        // within my application. It needs to be done once, ideally at application startup.
        FirebaseApp.initializeApp(this);
        Log.i(TAG, "FirebaseApp initialized successfully.");

        // --- Configure and Enable Firestore Offline Persistence ---
        // My design includes enabling Firestore's offline persistence to allow users to
        // view previously loaded data and even queue new data writes when they are offline.
        // These changes will automatically sync when the network connection is restored.
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // Builder for persistent cache settings.
        PersistentCacheSettings.Builder persistentCacheSettingsBuilder = PersistentCacheSettings.newBuilder();

        // Optional: Configure cache size.
        // The default size is 100MB. For this project, I'm using the default.
        // If I needed more, I could set it like this:
        // persistentCacheSettingsBuilder.setSizeBytes(200L * 1024L * 1024L); // For 200MB
        // Or, for effectively "unlimited" cache (Firestore manages up to available disk space):
        // persistentCacheSettingsBuilder.setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED);
        // I've decided to stick with the default for now, as it's usually sufficient.

        // Build the Firestore settings with the persistent cache configuration.
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(persistentCacheSettingsBuilder.build()) // Apply persistent cache settings.
                .build();

        try {
            firestore.setFirestoreSettings(settings);
            Log.i(TAG, "Firestore offline persistence successfully enabled with persistent cache (default size).");
        } catch (IllegalStateException e) {
            // This exception can occur if Firestore settings are set after any Firestore instance usage.
            // It's important to set these settings early.
            Log.w(TAG, "Firestore settings could not be applied, possibly because Firestore was already used: " + e.getMessage());
        }
        // --- Firestore Offline Persistence Configuration END ---

        // --- Initialize Firebase App Check ---
        // I'm setting up Firebase App Check to protect my backend resources (like Firestore)
        // from abuse by ensuring that requests originate from authentic instances of my app.
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        Log.d(TAG, "Attempting to initialize Firebase App Check using the DEBUG provider.");

        // For development and testing, I'm using the DebugAppCheckProviderFactory.
        // IMPORTANT: For a production release, I would replace this debug provider with a production-ready
        // provider like Play Integrity or App Attest to ensure real security.
        // This debug setup allows me to test App Check functionality without needing to set up Play Integrity for every debug build.
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
        Log.i(TAG, "Firebase App Check initialization with DEBUG provider attempt complete. For production, a different provider is needed.");
    }
}