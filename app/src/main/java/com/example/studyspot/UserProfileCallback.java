package com.example.studyspot; // Ensure this matches your actual package name

/**
 * UserProfileCallback is an interface I've designed to handle the asynchronous
 * results of fetching user profile data, typically from Firebase Firestore.
 * By using this callback interface, I can decouple the data fetching logic
 * (e.g., in a class like UserProfileFetcher) from the code that needs to act
 * upon the fetched UserProfile or handle any errors encountered during the process
 * (e.g., an Activity updating its UI).
 * This pattern is essential for managing operations that don't return results immediately.
 */
public interface UserProfileCallback {

    /**
     * Called when the {@link UserProfile} data is successfully fetched from the data source.
     * The implementing class will define how to use the retrieved user profile information.
     *
     * @param userProfile The {@link UserProfile} object containing the fetched data.
     * This object might be null if the profile was technically "found"
     * but contained no data, though typically it implies successful data retrieval.
     */
    void onSuccess(UserProfile userProfile);

    /**
     * Called when an error occurs during the process of fetching the user profile,
     * or if the profile data explicitly does not exist for the given user.
     * The implementing class will define how to handle this error, such as by
     * displaying an error message to the user or logging the issue.
     *
     * @param e       The {@link Exception} that occurred during the fetching operation.
     * This can be null if the error is simply that the profile doesn't exist,
     * rather than a technical failure.
     * @param message A descriptive message explaining the error or the reason for failure
     * (e.g., "Profile not found," "Network error").
     */
    void onError(Exception e, String message);
}