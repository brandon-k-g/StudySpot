package com.example.studyspot;

import com.google.firebase.firestore.Exclude; // Annotation to exclude fields from Firestore serialization.

/**
 * Represents a study subject created by a user.
 * This class is a POJO (Plain Old Java Object) that serves as the data model
 * for storing subject-specific information in Firebase Firestore.
 * My design for a Subject includes its title, various progress metrics (like study time,
 * revision progress, flashcard counts), an association with a user, and a color for UI display.
 */
public class Subject {

    // The documentId is the unique identifier for this subject in Firestore.
    // I use @Exclude to prevent Firestore from attempting to serialize/deserialize this field directly,
    // as its value is typically the Firestore document's ID, managed by my application code
    // (set after fetching from Firestore or when a new subject's ID is known).
    @Exclude
    private String documentId;

    // The title or name of the study subject (e.g., "Calculus I", "World History").
    private String title;

    // User-recorded previous result or grade for this subject, stored as a String.
    private String previousResult;

    // User-recorded or tracked study time for this subject, stored as a String (e.g., "10 hrs").
    private String studyTime;

    // A numerical representation of revision progress, likely a percentage (0-100).
    private int revisionProgress;

    // Count of flashcards completed by the user for this subject.
    private int flashcardsCompleted;

    // Total number of flashcards available or created for this subject.
    private int flashcardsTotal;

    // A count representing a user's study streak for this subject.
    private int streakCount;

    // The UID of the user who owns this subject. This is essential for linking subjects
    // to specific users and for implementing Firestore security rules.
    private String userId;

    // A hexadecimal string representing a color chosen for this subject card in the UI.
    // This allows for visual differentiation of subjects. Example: "#FF5733".
    private String cardColorHex;

    // Timestamp considerations:
    // If I needed to track when a subject was created or last modified,
    // I would typically add a timestamp field. A common approach for Firestore is:
    // private Object timestamp; // And initialize with FieldValue.serverTimestamp() for server-side time.
    // Or, using @ServerTimestamp with a java.util.Date field.
    // For the current design of this Subject POJO, a timestamp field is not explicitly included.


    /**
     * Default no-argument constructor.
     * This is REQUIRED by Firebase Firestore for its automatic data mapping
     * when converting Firestore documents back into Subject objects.
     */
    public Subject() {
        // Firestore needs this to instantiate the object during deserialization.
    }

    /**
     * Main constructor used by my application logic to create new Subject instances.
     * Note: `documentId` and `userId` are typically set separately. `userId` is set
     * before saving to Firestore, and `documentId` is often obtained after saving
     * (if auto-generated by Firestore) or set when an existing subject is fetched.
     *
     * @param title The title of the subject.
     * @param previousResult The user's previous result in this subject.
     * @param studyTime The amount of time spent studying this subject.
     * @param revisionProgress The current revision progress percentage.
     * @param flashcardsCompleted Number of flashcards completed.
     * @param flashcardsTotal Total number of flashcards for this subject.
     * @param streakCount Current study streak for this subject.
     * @param cardColorHex Hexadecimal color code for the subject's UI representation.
     */
    public Subject(String title, String previousResult, String studyTime,
                   int revisionProgress, int flashcardsCompleted, int flashcardsTotal,
                   int streakCount, String cardColorHex) {
        this.title = title;
        this.previousResult = previousResult;
        this.studyTime = studyTime;
        this.revisionProgress = revisionProgress;
        this.flashcardsCompleted = flashcardsCompleted;
        this.flashcardsTotal = flashcardsTotal;
        this.streakCount = streakCount;
        this.cardColorHex = cardColorHex;
        // The userId is usually set just before this new Subject object is saved to Firestore.
        // The documentId is set when the object is retrieved from Firestore, or after it's newly created and its ID is known.
    }

    // --- Getters and Setters ---
    // Standard accessor and mutator methods for the private fields.
    // These are used by my application code and also by Firestore for serialization/deserialization.

    /**
     * Gets the Firestore document ID of this subject.
     * Marked with @Exclude as it's managed by the application, not directly part of the Firestore document's fields being written.
     * @return The unique document ID.
     */
    @Exclude
    public String getDocumentId() { return documentId; }
    /**
     * Sets the Firestore document ID. This is typically called after fetching
     * the subject from Firestore to store its ID within the object.
     * @param documentId The unique document ID.
     */
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // Getters and setters for all other fields related to subject details and progress.
    public String getPreviousResult() { return previousResult; }
    public void setPreviousResult(String previousResult) { this.previousResult = previousResult; }

    public String getStudyTime() { return studyTime; }
    public void setStudyTime(String studyTime) { this.studyTime = studyTime; }

    public int getRevisionProgress() { return revisionProgress; }
    public void setRevisionProgress(int revisionProgress) { this.revisionProgress = revisionProgress; }

    public int getFlashcardsCompleted() { return flashcardsCompleted; }
    public void setFlashcardsCompleted(int flashcardsCompleted) { this.flashcardsCompleted = flashcardsCompleted; }

    public int getFlashcardsTotal() { return flashcardsTotal; }
    public void setFlashcardsTotal(int flashcardsTotal) { this.flashcardsTotal = flashcardsTotal; }

    public int getStreakCount() { return streakCount; }
    public void setStreakCount(int streakCount) { this.streakCount = streakCount; }

    public String getCardColorHex() { return cardColorHex; }
    public void setCardColorHex(String cardColorHex) { this.cardColorHex = cardColorHex; }

    /**
     * Utility method to get a formatted string representing flashcard progress (e.g., "5/10").
     * This method is intended for UI display purposes and is not stored in Firestore
     * directly, hence the @Exclude annotation. It's derived from `flashcardsCompleted`
     * and `flashcardsTotal`.
     * @return A string representing the flashcard completion progress.
     */
    @Exclude
    public String getFlashcardsProgressString() {
        return flashcardsCompleted + "/" + flashcardsTotal;
    }
}