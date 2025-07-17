package com.example.studyspot; // Ensure this matches your actual package name

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;
// import android.widget.ProgressBar; // Import ProgressBar - Retained as it was in original, though not directly used.

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.studyspot.databinding.ActivitySignupBinding; // My ViewBinding class for activity_signup.xml.
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue; // For using server-side timestamps.
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * SignUpActivity provides the user interface and logic for new user registration.
 * My registration process involves two main steps:
 * 1. Creating a new user account with Firebase Authentication (email/password).
 * 2. If authentication creation is successful, creating a corresponding user profile
 * document in Firebase Firestore to store additional details like display name.
 * The design includes input validation for all fields and user feedback during the process.
 */
public class SignUpActivity extends AppCompatActivity {

    // Log tag for identifying messages from this activity in Logcat.
    private static final String TAG = "SignUpActivity";

    // ViewBinding instance to interact with UI elements defined in activity_signup.xml.
    private ActivitySignupBinding binding;
    // Firebase Authentication instance, used for creating new user accounts.
    private FirebaseAuth mAuth;
    // Firebase Firestore instance, used for storing user profile data.
    private FirebaseFirestore mDb;
    // private ProgressBar progressBarSignUp; // Commented out as not explicitly used.
    // If I add a visual ProgressBar, I would uncomment and initialize it.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using ViewBinding.
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Authentication and Firestore.
        mAuth = FirebaseAuth.getInstance();
        mDb = FirebaseFirestore.getInstance();

        // If I were to implement a visual progress bar, I'd initialize it here:
        // progressBarSignUp = binding.progressBarSignUp; // Assuming 'progressBarSignUp' is the ID in XML.

        // Set up the click listener for the Sign Up button.
        binding.buttonSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // When clicked, initiate the sign-up process.
                performSignUp();
            }
        });

        // Set up the click listener for the "Already have an account? Log In" link.
        // This allows users who mistakenly reached this screen to navigate to LoginActivity.
        binding.textViewLoginLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Login link clicked. Navigating back to LoginActivity.");
                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                // Optional: Using FLAG_ACTIVITY_CLEAR_TOP could be considered if LoginActivity
                // should always be the root if navigated to this way, but for a simple back navigation,
                // just starting it and finishing current is usually fine.
                // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish(); // Finish SignUpActivity to remove it from the back stack.
            }
        });
    }

    /**
     * Manages the UI state during the sign-up process (e.g., disabling buttons, showing progress).
     * This provides visual feedback to the user that an operation is in progress.
     * @param isLoading True if the sign-up process is active, false otherwise.
     */
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            // Disable the sign-up button to prevent multiple clicks during the attempt.
            binding.buttonSignUp.setEnabled(false);
            // If I had a ProgressBar, I would make it visible here:
            // if (progressBarSignUp != null) progressBarSignUp.setVisibility(View.VISIBLE);
        } else {
            // Re-enable the sign-up button once the attempt is complete.
            binding.buttonSignUp.setEnabled(true);
            // If I had a ProgressBar, I would hide it here:
            // if (progressBarSignUp != null) progressBarSignUp.setVisibility(View.GONE);
        }
    }

    /**
     * Handles the new user registration process.
     * It retrieves user input, performs validation, creates a Firebase Authentication user,
     * and then calls {@link #createUserProfileInFirestore(String, String, String)} to save profile details.
     */
    private void performSignUp() {
        // Get input values from the form fields and trim whitespace.
        String displayName = binding.editTextDisplayNameSignUp.getText().toString().trim();
        String email = binding.editTextEmailSignUp.getText().toString().trim();
        String password = binding.editTextPasswordSignUp.getText().toString().trim();
        String confirmPassword = binding.editTextPasswordConfirmSignUp.getText().toString().trim();

        // --- Input Validation ---
        // Clear any previous error messages from the input fields.
        binding.textInputLayoutDisplayNameSignUp.setError(null);
        binding.textInputLayoutEmailSignUp.setError(null);
        binding.textInputLayoutPasswordSignUp.setError(null);
        binding.textInputLayoutPasswordConfirmSignUp.setError(null);
        boolean isValid = true; // Flag to track overall validity.

        // Validate Display Name: must not be empty.
        if (TextUtils.isEmpty(displayName)) {
            binding.textInputLayoutDisplayNameSignUp.setError(getString(R.string.error_display_name_required));
            binding.editTextDisplayNameSignUp.requestFocus(); // Focus for user correction.
            isValid = false;
        }
        // Validate Email: must not be empty and must be a valid email format.
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputLayoutEmailSignUp.setError(getString(R.string.error_invalid_email));
            if (isValid) binding.editTextEmailSignUp.requestFocus(); // Focus if previous fields were valid.
            isValid = false;
        }
        // Validate Password: must not be empty and meet minimum length (Firebase default is 6).
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.textInputLayoutPasswordSignUp.setError(getString(R.string.error_password_too_short));
            if (isValid) binding.editTextPasswordSignUp.requestFocus();
            isValid = false;
        }
        // Validate Confirm Password: must not be empty.
        if (TextUtils.isEmpty(confirmPassword)) {
            binding.textInputLayoutPasswordConfirmSignUp.setError(getString(R.string.error_password_required));
            if (isValid) binding.editTextPasswordConfirmSignUp.requestFocus();
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            // Validate that password and confirm password fields match.
            binding.textInputLayoutPasswordConfirmSignUp.setError(getString(R.string.error_password_mismatch));
            if (isValid) binding.editTextPasswordConfirmSignUp.requestFocus();
            isValid = false;
        }

        // If any validation failed, stop the sign-up process.
        if (!isValid) {
            return;
        }

        showLoading(true); // Indicate that the sign-up process has started.

        // Step 1: Create user with Firebase Authentication.
        // This is an asynchronous operation.
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Firebase Authentication user created successfully.
                            Log.d(TAG, "Firebase createUserWithEmail:success");
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                String userId = firebaseUser.getUid();
                                // Step 2: Create the user's profile document in Firestore.
                                // Navigation to MainActivity will happen inside this method's success listener.
                                createUserProfileInFirestore(userId, displayName, email);
                            } else {
                                // This case should be rare if task was successful.
                                showLoading(false); // Hide loading indicator on error.
                                Log.e(TAG, "Firebase user created, but getCurrentUser() returned null.");
                                Toast.makeText(SignUpActivity.this, "Signup successful, but failed to retrieve user ID. Please try logging in.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            // Firebase Authentication user creation failed.
                            showLoading(false); // Hide loading indicator on error.
                            Log.w(TAG, "Firebase createUserWithEmail:failure", task.getException());
                            // Display a more user-friendly error message from Firebase.
                            Toast.makeText(SignUpActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    /**
     * Creates a user profile document in Firebase Firestore after successful Firebase Auth user creation.
     * This document stores additional user information like display name and email, linked by the user's UID.
     * My design uses a 'users' collection for these profiles.
     * @param userId The UID of the newly created Firebase Authentication user.
     * @param displayName The display name entered by the user.
     * @param email The email address entered by the user.
     */
    private void createUserProfileInFirestore(String userId, String displayName, String email) {
        Log.d(TAG, "createUserProfileInFirestore: Attempting to create profile for UID: " + userId);
        // Create a UserProfile POJO to structure the data for Firestore.
        UserProfile userProfile = new UserProfile(
                displayName,
                email,
                FieldValue.serverTimestamp() // Using Firestore server-side timestamp for creation time.
        );

        // Save the userProfile object to the 'users' collection in Firestore,
        // using the Firebase Auth UID as the document ID for easy linking.
        mDb.collection("users").document(userId)
                .set(userProfile) // .set() will create or overwrite the document.
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        showLoading(false); // Final step successful, hide loading.
                        Log.d(TAG, "User profile document created successfully in Firestore for UID: " + userId);
                        Toast.makeText(SignUpActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                        // Navigate to the main application screen.
                        navigateToMainActivity();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        showLoading(false); // Hide loading indicator on error.
                        Log.w(TAG, "Error creating user profile document in Firestore for UID: " + userId, e);
                        // CRITICAL: Auth user was created, but Firestore profile creation failed.
                        // My current design informs the user. For a more robust solution,
                        // I might consider deleting the Auth user to maintain consistency,
                        // or provide a way for the user to retry profile creation.
                        Toast.makeText(SignUpActivity.this, "Authentication successful, but failed to save profile details: " + e.getMessage() +
                                        " Please try logging in or contact support if issues persist.",
                                Toast.LENGTH_LONG).show();
                        // Optional: Sign out the user if profile creation is absolutely critical for app functionality.
                        // This prevents a partially registered state.
                        // if (mAuth.getCurrentUser() != null) {
                        //     mAuth.signOut();
                        //     Log.d(TAG, "Signed out user due to Firestore profile creation failure.");
                        // }
                    }
                });
    }

    /**
     * Navigates the user to the MainActivity after a successful sign-up and profile creation.
     * It clears the activity stack to prevent the user from navigating back to the sign-up or login screens.
     * This is a key part of my navigation design for a clean user experience post-registration.
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        // These flags ensure MainActivity becomes the new root of the task,
        // and the user cannot navigate back to SignUpActivity or LoginActivity.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Close SignUpActivity.
        Log.d(TAG, "Navigated to MainActivity and cleared activity stack.");
    }
}