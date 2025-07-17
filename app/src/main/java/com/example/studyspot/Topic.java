package com.example.studyspot;

import com.google.firebase.firestore.Exclude;      // Annotation to exclude fields from Firestore serialization.
import com.google.firebase.firestore.ServerTimestamp; // Annotation for automatic server-side timestamping.
import java.util.Date;                              // Using java.util.Date for the timestamp field.

/**
 * Represents a specific topic within a parent subject.
 * This class is a POJO (Plain Old Java Object) that serves as the data model
 * for storing topic-related information in Firebase Firestore.
 * My design for a Topic includes its title, a reference to its parent subject (subjectId),
 * a count of associated flashcards (flashcardCount), and a server-generated timestamp.
 * The flashcardCount is a denormalized field I've included for efficient display
 * of how many flashcards a topic contains, without needing to query all flashcards.
 */
public class Topic {

    // The documentId is the unique identifier for this topic in Firestore.
    // I use @Exclude as this field is typically the Firestore document's ID,
    // which I manage in my application code (set after fetching or when a new topic's ID is known).
    @Exclude
    private String documentId;

    // The ID of the parent Subject to which this topic belongs.
    // This is crucial for maintaining the hierarchical structure (Subject -> Topic -> Flashcard)
    // and for querying topics related to a specific subject.
    private String subjectId;

    // The title or name of the topic (e.g., "Chapter 1: Introduction", "Key Historical Figures").
    private String title;

    // Timestamp indicating when the topic was created or last modified.
    // The @ServerTimestamp annotation instructs Firestore to automatically populate this field
    // with the server's current time when this Topic object is written to the database
    // (if the field is null when being written). Firestore converts this to a java.util.Date on read.
    @ServerTimestamp
    private Date timestamp;

    // A denormalized count of the number of flashcards associated with this topic.
    // My design includes this field to optimize performance when displaying topic lists,
    // as it avoids the need to query all flashcards just to get a count.
    // This count is updated by my application logic whenever flashcards are added or deleted for this topic.
    private int flashcardCount = 0;

    /**
     * Default no-argument constructor.
     * This is REQUIRED by Firebase Firestore for its automatic data mapping
     * when converting Firestore documents back into Topic objects.
     * I initialize flashcardCount to 0 here as a default for new instances created by Firestore.
     */
    public Topic() {
        this.flashcardCount = 0; // Default for Firestore deserialization.
    }

    /**
     * Constructor used by my application logic (e.g., in AddEditTopicActivity)
     * to create new Topic instances before they are saved to Firestore.
     * New topics start with a flashcard count of 0.
     * The timestamp is deliberately left null here, as I rely on @ServerTimestamp.
     *
     * @param subjectId The ID of the parent subject for this new topic.
     * @param title     The title for this new topic.
     */
    public Topic(String subjectId, String title) {
        this.subjectId = subjectId;
        this.title = title;
        this.flashcardCount = 0; // New topics are initialized with zero flashcards.
        this.timestamp = null;   // Setting to null ensures @ServerTimestamp populates it on the server.
    }

    // --- Getters and Setters ---
    // Standard accessor and mutator methods for the private fields.
    // These are used by my application code and also by Firestore for serialization/deserialization.

    /**
     * Gets the Firestore document ID of this topic.
     * Marked with @Exclude as it's typically managed by the application after retrieval from Firestore.
     * @return The unique document ID.
     */
    @Exclude
    public String getDocumentId() { return documentId; }
    /**
     * Sets the Firestore document ID. This is usually called by my application code
     * after fetching the topic from Firestore or after a new topic is created and its ID is known.
     * @param documentId The unique document ID.
     */
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    /**
     * Gets the timestamp of when this topic was created/last updated.
     * This will be a java.util.Date object when read from Firestore.
     * @return The Date object representing the timestamp.
     */
    public Date getTimestamp() { return timestamp; }
    /**
     * Sets the timestamp. This is primarily for Firestore's deserialization process.
     * When creating new Topic instances in app code, the timestamp is handled by @ServerTimestamp.
     * @param timestamp The Date object for the timestamp.
     */
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    /**
     * Gets the denormalized count of flashcards associated with this topic.
     * @return The number of flashcards.
     */
    public int getFlashcardCount() { return flashcardCount; }
    /**
     * Sets the denormalized count of flashcards. My application logic calls this
     * to update the count when flashcards are added or removed.
     * @param flashcardCount The new flashcard count.
     */
    public void setFlashcardCount(int flashcardCount) { this.flashcardCount = flashcardCount; }
}