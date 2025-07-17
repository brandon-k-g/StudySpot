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

import com.example.studyspot.databinding.ActivityLoginBinding; // My ViewBinding class for activity_login.xml.
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

/**
 * LoginActivity provides the user interface and logic for user authentication.
 * It uses Firebase Authentication for email/password sign-in.
 * My design includes input validation, user feedback during the login process,
 * and navigation to the main application screen upon successful login or to the
 * sign-up screen for new users. I've also included a "skip login" option for direct access.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mAuth = FirebaseAuth.getInstance();

        binding.buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();
            }
        });
        binding.textViewSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Sign Up text clicked. Navigating to SignUpActivity.");
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
                startActivity(intent); // Start the SignUpActivity.
            }
        });
    }

    /**
     * Manages the UI state during the login process (e.g., disabling buttons, showing progress).
     * This provides visual feedback to the user that an operation is in progress.
     * @param isLoading True if the login process is active, false otherwise.
     */
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.buttonLogin.setEnabled(false);
        } else {
            binding.buttonLogin.setEnabled(true);
        }
    }

    /**
     * Handles the user login process.
     * It retrieves email and password from input fields, performs validation,
     * and then attempts to sign in the user with Firebase Authentication.
     */
    private void performLogin() {
        // Get email and password from the input fields and trim whitespace.
        String email = binding.editTextEmail.getText().toString().trim();
        String password = binding.editTextPassword.getText().toString().trim();

        // --- Input Validation ---
        // Clear any previous errors.
        binding.textInputLayoutEmail.setError(null);
        binding.textInputLayoutPassword.setError(null);
        boolean isValid = true; // Assume valid initially.

        // Validate email: must not be empty and must match standard email pattern.
        // This is client-side validation for quick feedback. Firebase also performs server-side validation.
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputLayoutEmail.setError(getString(R.string.error_invalid_email));
            binding.editTextEmail.requestFocus(); // Focus on the email field for correction.
            isValid = false;
        }
        // Validate password: must not be empty.
        if (TextUtils.isEmpty(password)) {
            binding.textInputLayoutPassword.setError(getString(R.string.error_password_required));
            if (isValid) { // Only request focus if the email field was valid.
                binding.editTextPassword.requestFocus();
            }
            isValid = false;
        }

        // If input is not valid, stop the login process here.
        if (!isValid) {
            return;
        }

        showLoading(true); // Indicate that the login process has started.

        // Attempt to sign in with Firebase Authentication using email and password.
        // This is an asynchronous operation.
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        showLoading(false); // Login attempt finished, hide loading indicator.
                        if (task.isSuccessful()) {
                            // Login successful.
                            Log.d(TAG, "Firebase signInWithEmail:success");
                            Toast.makeText(LoginActivity.this, "Login successful.", Toast.LENGTH_SHORT).show();
                            // Navigate to the main part of the application.
                            navigateToMainActivity();
                        } else {
                            // Login failed.
                            Log.w(TAG, "Firebase signInWithEmail:failure", task.getException());
                            // Display a more user-friendly error message.
                            // task.getException().getMessage() provides details from Firebase.
                            Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    /**
     * Navigates the user to the MainActivity after a successful login or if skipping login.
     * It clears the activity stack to prevent the user from navigating back to the LoginActivity.
     * This is a deliberate design choice for my app's navigation flow post-authentication.
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // These flags are important:
        // FLAG_ACTIVITY_NEW_TASK: Makes the MainActivity the start of a new task on the history stack.
        // FLAG_ACTIVITY_CLEAR_TASK: Clears any existing task that would be associated with MainActivity,
        // effectively making it the new root and preventing back navigation to LoginActivity.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Close LoginActivity so it's removed from the back stack.
    }
}