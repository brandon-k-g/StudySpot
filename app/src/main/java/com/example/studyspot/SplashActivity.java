package com.example.studyspot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper; // Looper is needed for creating a Handler on the main thread.
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SplashActivity is the first screen displayed when the application launches.
 * My design for this activity is to provide a brief branding moment (splash screen)
 * and then, after a short delay, check the user's current authentication state
 * using Firebase Authentication. Based on this state, it navigates the user
 * either to the MainActivity (if already logged in) or to the LoginActivity (if not logged in).
 * This ensures a smooth entry into the app.
 */
public class SplashActivity extends AppCompatActivity {

    // Log tag for identifying messages from this SplashActivity.
    private static final String TAG = "SplashActivity";
    // Firebase Authentication instance to check the current user's login status.
    private FirebaseAuth mAuth;
    // Defines the duration (in milliseconds) the splash screen will be visible.
    // I've chosen a delay that allows users to see branding without being too long.
    private static final int SPLASH_DELAY = 1500; // 1.5 seconds, but can be adjusted.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // For this splash screen, I might not have a specific layout (activity_splash.xml).
        // If I did, I would uncomment the line below:
        // setContentView(R.layout.activity_splash);
        // Currently, it acts as a purely transitional activity without its own complex UI.

        // Initialize Firebase Authentication.
        mAuth = FirebaseAuth.getInstance();

        // Use a Handler to delay the navigation logic.
        // This creates the "splash screen" effect by pausing briefly before moving to the next screen.
        // Looper.getMainLooper() ensures this Handler runs on the main UI thread.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check the current Firebase user authentication state.
            FirebaseUser currentUser = mAuth.getCurrentUser();

            if (currentUser != null) {
                // If currentUser is not null, a user is already signed in.
                // This could be a permanently signed-in user or an existing anonymous user
                // from a previous session if anonymous authentication is enabled and persisted.
                Log.d(TAG, "User is currently signed in (UID: " + currentUser.getUid() + "). Navigating to MainActivity.");
                navigateToActivity(MainActivity.class); // Proceed to the main content.
            } else {
                // If currentUser is null, no user is signed in.
                Log.d(TAG, "No user is currently signed in. Navigating to LoginActivity.");
                navigateToActivity(LoginActivity.class); // Direct to the login screen.
            }
        }, SPLASH_DELAY); // Execute after the defined SPLASH_DELAY.
    }

    /**
     * Helper method to handle navigation to the target activity.
     * My design includes specific Intent flags to ensure proper management of the activity stack:
     * - FLAG_ACTIVITY_NEW_TASK: The launched activity becomes the start of a new task on the history stack.
     * - FLAG_ACTIVITY_CLEAR_TASK: Clears any existing task that would be associated with the activity,
     * effectively making it the new root.
     * This, combined with finish(), prevents the user from navigating back to the SplashActivity.
     * @param targetActivityClass The class of the Activity to navigate to (e.g., MainActivity.class or LoginActivity.class).
     */
    private void navigateToActivity(Class<?> targetActivityClass) {
        Intent intent = new Intent(SplashActivity.this, targetActivityClass);
        // These flags ensure that the new activity starts as a fresh task,
        // and the SplashActivity is removed from the back stack.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Close the SplashActivity so the user cannot navigate back to it.
        Log.d(TAG, "Navigated to " + targetActivityClass.getSimpleName() + " and finished SplashActivity.");
    }
}