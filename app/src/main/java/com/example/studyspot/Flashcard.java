package com.example.studyspot;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp; // Annotation for automatic timestamping by Firestore.
import java.util.Date; // Using java.util.Date for timestamp field.

/**
 * Represents a single flashcard. This class is a POJO (Plain Old Java Object)
 * designed to model the data structure for flashcards stored in Firebase Firestore.
 * It includes fields for the flashcard's content (question and answer),
 * its association with a topic and subject, and a timestamp.
 * My design choice to include subjectId here allows for potential broader queries on flashcards
 * directly by subject, although primarily they are accessed via topics.
 */
public class Flashcard {

    // The documentId is the unique identifier for this flashcard in Firestore.
    // I use @Exclude to prevent Firestore from trying to serialize/deserialize this field,
    // as I manage it manually after fetching the document.
    @Exclude
    private String documentId;

    // Foreign key linking this flashcard to a specific Topic document in Firestore.
    private String topicId;

    // Foreign key linking this flashcard to a specific Subject document.
    // This provides an additional layer of data organization and allows for
    // queries that might span across all flashcards of a subject, irrespective of topic.
    private String subjectId;

    // The question side of the flashcard.
    private String question;

    // The answer side of the flashcard.
    private String answer;

    // Timestamp indicating when the flashcard was created or last modified.
    // The @ServerTimestamp annotation tells Firestore to automatically populate this field
    // with the server's timestamp when the data is written, if it's null.
    // I've chosen java.util.Date as the type, which Firestore handles well.
    @ServerTimestamp
    private Date timestamp;

    /**
     * Default no-argument constructor.
     * This is REQUIRED by Firebase Firestore for automatic data mapping
     * when converting Firestore documents back into Flashcard objects.
     */
    public Flashcard() {}

    /**
     * Constructor used for creating new Flashcard instances within the application code
     * before they are saved to Firestore.
     * @param topicId The ID of the parent topic.
     * @param subjectId The ID of the parent subject.
     * @param question The question text for the flashcard.
     * @param answer The answer text for the flashcard.
     */
    public Flashcard(String topicId, String subjectId, String question, String answer) {
        this.topicId = topicId;
        this.subjectId = subjectId;
        this.question = question;
        this.answer = answer;
        // The 'timestamp' field is intentionally not set here;
        // I rely on the @ServerTimestamp annotation for Firestore to set it upon saving.
        // This ensures timestamps are consistent and server-generated.
    }

    // --- Getters and Setters ---
    // These are standard accessor and mutator methods for the private fields,
    // allowing controlled access to the Flashcard object's properties.
    // Firestore also uses these (specifically getters during serialization and setters during deserialization).

    /**
     * Gets the Firestore document ID of this flashcard.
     * @return The unique document ID.
     */
    @Exclude // Also excluded from Firestore writes as it's a read-only identifier from Firestore's perspective for the POJO.
    public String getDocumentId() { return documentId; }
    /**
     * Sets the Firestore document ID. This is typically called after fetching
     * the flashcard from Firestore, to store its ID within the object.
     * @param documentId The unique document ID.
     */
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    /**
     * Gets the timestamp of when this flashcard was created/last updated.
     * @return The Date object representing the timestamp.
     */
    public Date getTimestamp() { return timestamp; }
    /**
     * Sets the timestamp. While @ServerTimestamp handles automatic setting on write,
     * this setter is present for completeness and Firestore's deserialization.
     * @param timestamp The Date object for the timestamp.
     */
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}