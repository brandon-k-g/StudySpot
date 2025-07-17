package com.example.studyspot;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.studyspot.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;

/**
 * ProfileActivity displays the current user's profile information and provides options
 * for account management, such as changing password (via reset email), logging out,
 * and deleting the account.
 * It relies heavily on Firebase Authentication to retrieve user details and perform account operations.
 * My design ensures that sensitive operations like account deletion are confirmed by the user
 * and handles cases like re-authentication requirements from Firebase.
 */
public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";
    // ViewBinding instance for type-safe access to UI elements defined in activity_profile.xml.
    private ActivityProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using ViewBinding.
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Authentication.
        mAuth = FirebaseAuth.getInstance();
        // mDb = FirebaseFirestore.getInstance(); // Would be initialized here if used.
        currentUser = mAuth.getCurrentUser(); // Get the currently signed-in user.

        // Setup the toolbar with a title and back navigation.
        setSupportActionBar(binding.toolbarProfile);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Show back arrow.
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_activity_profile)); // Set toolbar title.
        }

        // CRITICAL: If no user is currently authenticated, redirect to the LoginActivity.
        // This ProfileActivity is designed for authenticated users.
        if (currentUser == null) {
            Log.w(TAG, "onCreate: No authenticated user found. Redirecting to LoginActivity.");
            goToLoginAndFinishAll(null); // Navigate to login and clear back stack.
            return; // Stop further execution in onCreate.
        }

        // Populate UI fields with user details and set up button listeners.
        populateUserDetails();
        setupButtonListeners();
    }

    /**
     * Populates the UI elements (TextViews) with the current user's details.
     * My design differentiates the display and available actions based on whether
     * the user is anonymous or has a full account (e.g., email/password).
     */
    private void populateUserDetails() {
        if (currentUser.isAnonymous()) {
            // UI specific to anonymous users.
            binding.textViewDisplayName.setText("Guest User (Anonymous)");
            binding.textViewEmail.setText("Not applicable for guest sessions");
            // For anonymous users, the "Change Password" button's function is repurposed
            // to guide them towards creating a full account. This is a UX design choice.
            binding.buttonChangePassword.setText("Create Full Account to Save Progress");
        } else {
            // UI for regular, authenticated (non-anonymous) users.
            binding.textViewDisplayName.setText(currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty() ? currentUser.getDisplayName() : "N/A");
            binding.textViewEmail.setText(currentUser.getEmail());
            binding.buttonChangePassword.setText(getString(R.string.button_change_password));
        }
        Log.d(TAG, "User details populated for user: " + (currentUser.isAnonymous() ? "Anonymous" : currentUser.getEmail()));
    }

    /**
     * Sets up click listeners for the various action buttons on the profile screen
     * (Change Password/Create Account, Logout, Delete Account).
     */
    private void setupButtonListeners() {
        // Listener for the "Change Password" or "Create Full Account" button.
        binding.buttonChangePassword.setOnClickListener(v -> {
            if (currentUser != null && currentUser.isAnonymous()) {
                // If user is anonymous, guide them to the SignUpActivity.
                Toast.makeText(ProfileActivity.this, "Please create a full account to set a password and save your study data.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(ProfileActivity.this, SignUpActivity.class);
                // Consider linking anonymous data to the new account if SignUpActivity supports it.
                startActivity(intent);
            } else {
                // If user is not anonymous, initiate the password reset email process.
                sendPasswordReset();
            }
        });

        // Listener for the "Logout" button.
        binding.buttonLogout.setOnClickListener(v -> logoutUser());

        // Listener for the "Delete Account" button.
        binding.buttonDeleteAccount.setOnClickListener(v -> confirmAccountDeletion());
        Log.d(TAG, "Button listeners for profile actions have been set up.");
    }

    /**
     * Sends a password reset email to the current user's registered email address
     * using Firebase Authentication. This is only applicable for non-anonymous users.
     */
    private void sendPasswordReset() {
        // Check if user is authenticated, has an email, and is not anonymous.
        if (currentUser != null && currentUser.getEmail() != null && !currentUser.isAnonymous()) {
            showLoading(true); // Indicate processing.
            mAuth.sendPasswordResetEmail(currentUser.getEmail())
                    .addOnCompleteListener(task -> {
                        showLoading(false); // Process finished.
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Password reset email successfully sent to: " + currentUser.getEmail());
                            Toast.makeText(ProfileActivity.this,
                                    getString(R.string.password_reset_email_sent_success, currentUser.getEmail()),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Log.w(TAG, "sendPasswordResetEmail:failure. Error: ", task.getException());
                            Toast.makeText(ProfileActivity.this,
                                    getString(R.string.password_reset_email_sent_failure) + (task.getException() != null ? ": " + task.getException().getMessage() : ""),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            // Inform user if password reset is not applicable.
            Toast.makeText(this, "Password reset is not applicable for guest users or if no email is registered.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Password reset attempted for inapplicable user state (Anonymous or no email).");
        }
    }

    /**
     * Logs out the current user using Firebase Authentication and navigates
     * back to the LoginActivity, clearing the activity stack.
     */
    private void logoutUser() {
        Log.d(TAG, "logoutUser: Attempting to sign out current user.");
        mAuth.signOut(); // Firebase sign out. This handles both regular and anonymous users.
        // Navigate to login screen with a success message.
        goToLoginAndFinishAll(getString(R.string.log_out) + " successful. See you next time!");
    }

    /**
     * Displays an AlertDialog to confirm if the user really wants to delete their account.
     * This is a crucial UX step for such a destructive action.
     */
    private void confirmAccountDeletion() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_account_title))
                .setMessage(getString(R.string.confirm_delete_account_message)) // Inform user this is irreversible.
                .setPositiveButton(getString(R.string.delete_account_button_confirm), (dialog, which) -> {
                    // User confirmed, proceed with account deletion.
                    deleteUserAccount();
                })
                .setNegativeButton(getString(R.string.cancel), null) // User cancelled, do nothing.
                .setIcon(android.R.drawable.ic_dialog_alert) // Standard alert icon.
                .show();
    }

    /**
     * Attempts to delete the current user's account from Firebase Authentication.
     * It handles potential errors, including the {@link FirebaseAuthRecentLoginRequiredException},
     * which requires the user to re-authenticate for security reasons before deletion.
     * Note: Deleting associated Firestore data is a separate, complex task not implemented here.
     */
    private void deleteUserAccount() {
        if (currentUser == null) {
            Log.w(TAG, "deleteUserAccount: Attempted to delete account, but currentUser is null.");
            return; // Should not happen if activity flow is correct.
        }

        Log.d(TAG, "deleteUserAccount: Attempting to delete account for UID: " + currentUser.getUid() +
                " (Is Anonymous: " + currentUser.isAnonymous() + ")");
        showLoading(true); // Indicate processing.

        // Placeholder for future implementation: Deleting user's data from Firestore.
        // This would involve querying and deleting all documents related to this user's UID
        // across all relevant collections (e.g., subjects, topics, flashcards, test_results).
        // It's a complex operation often best handled by a Firebase Cloud Function for reliability.
        // deleteAllUserDataFromFirestore(currentUser.getUid());

        // Perform the Firebase Authentication account deletion.
        currentUser.delete()
                .addOnCompleteListener(task -> {
                    showLoading(false); // Process finished.
                    if (task.isSuccessful()) {
                        Log.i(TAG, "User account successfully deleted from Firebase Authentication.");
                        Toast.makeText(ProfileActivity.this, getString(R.string.account_deleted_successfully), Toast.LENGTH_SHORT).show();
                        // Navigate to login screen after successful deletion.
                        goToLoginAndFinishAll();
                    } else {
                        Log.w(TAG, "deleteUserAccount: Firebase Auth account deletion failed.", task.getException());
                        // Handle specific failure: re-authentication required.
                        // This is a common security measure in Firebase for sensitive operations.
                        if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                            Toast.makeText(ProfileActivity.this, getString(R.string.account_deletion_requires_re_auth), Toast.LENGTH_LONG).show();
                            // My design choice here is to sign out the user and redirect them to login,
                            // where they can re-authenticate and then try deleting again if they wish.
                            mAuth.signOut();
                            goToLoginAndFinishAll(getString(R.string.logged_out_for_account_deletion) + " Please log in again to delete your account.");
                        } else {
                            // Handle other deletion failures.
                            Toast.makeText(ProfileActivity.this, getString(R.string.account_deletion_failed) +
                                    (task.getException() != null ? ": " + task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Placeholder method for deleting user data from Firestore.
    // Implementing this would require careful consideration of all user-related data
    // and potentially using batched writes or Cloud Functions for atomicity and reliability.
    // private void deleteAllUserDataFromFirestore(String userId) { /* ... Your complex data deletion logic ... */ }

    /**
     * Navigates to the LoginActivity and clears the entire activity back stack.
     * This ensures the user cannot navigate back to a state that requires authentication.
     * My design uses this consistently after logout or account deletion.
     * @param toastMessage An optional message to display as a Toast after navigating.
     */
    private void goToLoginAndFinishAll(String toastMessage) {
        if (toastMessage != null && !toastMessage.isEmpty()) {
            // Display message using application context to ensure Toast visibility even if activity is finishing.
            Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        // These flags are critical for a clean navigation flow post-logout/deletion.
        // FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_CLEAR_TASK make LoginActivity the new root.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        // finishAffinity() closes all activities in the current task associated with this activity's affinity.
        // This ensures a complete clear of the authenticated part of the app.
        finishAffinity();
        Log.d(TAG, "Navigated to LoginActivity and cleared activity stack.");
    }

    /**
     * Overloaded version of goToLoginAndFinishAll without a toast message.
     */
    private void goToLoginAndFinishAll() {
        goToLoginAndFinishAll(null);
    }


    /**
     * Shows or hides a loading indicator (ProgressBar) and enables/disables action buttons.
     * This provides visual feedback to the user during asynchronous operations like
     * sending a password reset email or deleting an account.
     * @param isLoading True to show loading and disable buttons, false otherwise.
     */
    private void showLoading(boolean isLoading) {
        binding.progressBarProfile.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // Disable buttons during processing to prevent multiple actions.
        binding.buttonChangePassword.setEnabled(!isLoading);
        binding.buttonLogout.setEnabled(!isLoading);
        binding.buttonDeleteAccount.setEnabled(!isLoading);
    }

    /**
     * Handles action bar item clicks, specifically the "home" (back) button
     * to navigate back to the previous screen.
     * @param item The menu item that was selected.
     * @return True if the item was handled, false otherwise.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Simply close this activity and return to the previous one (MainActivity).
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}