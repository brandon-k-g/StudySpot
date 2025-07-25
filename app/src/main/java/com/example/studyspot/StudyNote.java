package com.example.studyspot; // Ensure this matches your package name

import com.google.firebase.firestore.FieldValue;    // Used for server-side timestamp generation.
import com.google.firebase.firestore.ServerTimestamp; // Annotation for an alternative way to handle timestamps (see commented code).
import java.util.Date;                              // Used in the alternative timestamp approach.

/**
 * Represents a single study note created by a user.
 * This class is a POJO (Plain Old Java Object) designed as the data model for
 * storing notes in Firebase Firestore. Each note is associated with a user
 * and includes the note content and a timestamp.
 * My design uses {@link FieldValue#serverTimestamp()} for setting the creation time on the server.
 */
public class StudyNote {
    // The noteId will be the document ID in Firestore.
    // I plan for this to be auto-generated by Firestore when a new note is added,
    // or it can be set if I'm reading an existing note.
    private String noteId;

    // The UID of the user who created this note.
    // This is crucial for associating notes with specific users and for Firestore security rules.
    private String userId;

    // The actual text content of the study note.
    private String text;

    // Timestamp for when the note was created.
    // I've chosen to use an 'Object' type here specifically to work with
    // FieldValue.serverTimestamp(). This tells Firestore to populate this field
    // with the server's current time when the document is written.
    // This ensures consistent timestamps regardless of the client's clock.
    private Object timestamp;

    /**
     * Default no-argument constructor.
     * This is REQUIRED by Firebase Firestore for its automatic data mapping
     * when converting Firestore documents back into StudyNote objects.
     */
    public StudyNote() {}

    /**
     * Constructor for creating new StudyNote instances within the application code
     * before they are saved to Firestore.
     * @param userId The ID of the user creating the note.
     * @param text The content of the note.
     */
    public StudyNote(String userId, String text) {
        this.userId = userId;
        this.text = text;
        // Set the timestamp field to FieldValue.serverTimestamp().
        // When this object is saved to Firestore, Firestore will replace this placeholder
        // with the actual server-generated timestamp.
        this.timestamp = FieldValue.serverTimestamp();
    }

    // --- Getters ---
    // Standard getter methods for accessing the note's properties.
    // Firestore uses these (or public fields) when serializing data.

    public String getNoteId() { return noteId; }
    public String getUserId() { return userId; }
    public String getText() { return text; }

    /**
     * Gets the timestamp. When read from Firestore after being set by
     * FieldValue.serverTimestamp(), this will typically be a com.google.firebase.Timestamp object
     * (which can be converted to java.util.Date if needed using .toDate()).
     * Before saving, it holds the FieldValue.serverTimestamp() placeholder.
     * @return The timestamp object (either FieldValue.serverTimestamp() or a Firestore Timestamp).
     */
    public Object getTimestamp() { return timestamp; }

    // --- Setters ---
    // Standard setter methods. Firestore uses these (or public fields)
    // when deserializing data into StudyNote objects.

    /**
     * Sets the note's document ID. This is typically called by my application code
     * after a note is fetched from Firestore, to store its ID within the POJO.
     * @param noteId The Firestore document ID.
     */
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public void setText(String text) { this.text = text; }
    public void setUserId(String userId) { this.userId = userId; }

    /**
     * Sets the timestamp. This is primarily for Firestore's deserialization.
     * When creating new notes, the timestamp is set using FieldValue.serverTimestamp() in the constructor.
     * @param timestamp The timestamp object (typically a Firestore Timestamp when read).
     */
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    /*
    // Alternative Timestamp Implementation:
    // I considered using the @ServerTimestamp annotation with a java.util.Date field
    // as an alternative way to handle server-side timestamps in Firestore.
    // This approach involves annotating a Date field like so:
    //
    // @ServerTimestamp
    // private Date timestampDate;
    //
    // public Date getTimestampDate() { return timestampDate; }
    // public void setTimestampDate(Date timestampDate) { this.timestampDate = timestampDate; }
    //
    // However, for this StudyNote POJO, I've opted for the FieldValue.serverTimestamp()
    // approach with an Object type field, as it's also a common and effective pattern.
    // Both achieve server-side timestamping.
    */
}