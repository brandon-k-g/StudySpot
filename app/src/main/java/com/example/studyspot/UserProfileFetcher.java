package com.example.studyspot; // Replace with your actual package name

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * UserProfileFetcher is a dedicated helper class I've created to encapsulate the logic
 * for retrieving user profile data from Firebase Firestore.
 * Its primary responsibility is to fetch a {@link UserProfile} object based on a user ID.
 * My design uses the {@link UserProfileCallback} interface to handle the results
 * of this asynchronous operation, promoting a clean separation of concerns between
 * data fetching and the UI or business logic that consumes the profile data.
 */
public class UserProfileFetcher {
    // Log tag for identifying messages from this UserProfileFetcher class.
    private static final String TAG = "UserProfileFetcher";
    // Instance of FirebaseFirestore, which is the entry point for all Firestore database operations.
    private FirebaseFirestore mDb;

    /**
     * Constructor for UserProfileFetcher.
     * Initializes the FirebaseFirestore instance, making the fetcher ready
     * to interact with the database. This is part of my setup for data persistence.
     */
    public UserProfileFetcher() {
        this.mDb = FirebaseFirestore.getInstance(); // Get the default FirebaseFirestore instance.
    }

    /**
     * Asynchronously fetches a user's profile from the "users" collection in Firestore
     * based on the provided user ID.
     * The result of the fetch operation (either the UserProfile object on success or
     * an error on failure) is communicated back via the provided {@link UserProfileCallback}.
     *
     * @param userId   The unique ID (UID) of the user whose profile is to be fetched.
     * This ID typically corresponds to the Firebase Authentication UID.
     * @param callback The {@link UserProfileCallback} instance that will handle the
     * success or failure outcome of the fetch operation. This parameter must not be null.
     */
    public void fetchUserProfile(String userId, @NonNull UserProfileCallback callback) {
        // My initial validation step: Ensure a valid userId is provided.
        // If not, invoke the callback's onError immediately and return.
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "fetchUserProfile: Attempted to fetch profile with a null or empty userId.");
            callback.onError(new IllegalArgumentException("User ID cannot be null or empty."), "Invalid User ID provided for profile fetch.");
            return;
        }

        // Construct the DocumentReference pointing to the specific user's profile document
        // within my "users" collection in Firestore. The document ID is the user's UID.
        DocumentReference userDocRef = mDb.collection("users").document(userId);
        Log.d(TAG, "fetchUserProfile: Attempting to fetch user profile from Firestore for UID: " + userId);

        // Perform the asynchronous get() operation to retrieve the document.
        userDocRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                // This listener is triggered if the read operation itself was successful,
                // regardless of whether the document actually exists.
                if (documentSnapshot.exists()) {
                    // Document exists in Firestore. Now, attempt to convert it to my UserProfile POJO.
                    UserProfile userProfile = documentSnapshot.toObject(UserProfile.class);
                    if (userProfile != null) {
                        // Successfully converted the Firestore document to a UserProfile object.
                        Log.d(TAG, "fetchUserProfile: User profile data fetched and parsed successfully for UID: " + userId);
                        callback.onSuccess(userProfile); // Pass the fetched profile back via the callback.
                    } else {
                        // This case can occur if the document exists but its structure doesn't match
                        // the UserProfile POJO, or if there are issues with field types/names.
                        Log.w(TAG, "fetchUserProfile: Document exists for UID: " + userId + ", but failed to map to UserProfile object. Check POJO field names and types.");
                        callback.onError(new Exception("Failed to parse user profile data from Firestore document."), "Error reading profile data structure.");
                    }
                } else {
                    // The document was not found in Firestore for the given userId.
                    Log.w(TAG, "fetchUserProfile: User profile document does not exist in Firestore for UID: " + userId);
                    callback.onError(new Exception("User profile document not found."), "Could not find user profile in the database.");
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // This listener is triggered if the Firestore get() operation itself fails
                // (e.g., due to network issues, permissions errors, or other Firebase exceptions).
                Log.e(TAG, "fetchUserProfile: Error fetching user profile data from Firestore for UID: " + userId, e);
                callback.onError(e, "Error loading profile from database: " + e.getMessage()); // Pass the error back via the callback.
            }
        });
    }
}