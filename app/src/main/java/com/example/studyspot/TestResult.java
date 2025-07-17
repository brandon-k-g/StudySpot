package com.example.studyspot;

import com.google.firebase.firestore.ServerTimestamp; // Annotation for automatic server-side timestamping by Firestore.
import java.util.Date;                              // Using java.util.Date for the timestamp field.

/**
 * Represents the result of a single test session completed by a user.
 * This class is a POJO (Plain Old Java Object) designed as the data model for
 * storing test outcomes in Firebase Firestore.
 * My design for a TestResult includes identifiers for the user, subject, and topic (if applicable),
 * the score achieved (correct vs. attempted), the calculated percentage, the mode of the test,
 * and a server-generated timestamp indicating when the test was completed.
 */
public class TestResult {
    // User and content identifiers to provide context for the test result.
    private String userId;       // UID of the user who took the test.
    private String subjectId;    // ID of the subject tested.
    private String subjectTitle; // Title of the subject, stored for easy display in score lists.
    private String topicId;      // ID of the specific topic tested (can be null if test spans a whole subject, e.g., random all).
    private String topicTitle;   // Title of the topic, stored for display.

    // Core score data.
    private int scoreCorrect;     // Number of flashcards answered correctly.
    private int cardsAttempted;   // Total number of flashcards attempted in the test.
    private double percentage;    // Calculated percentage score (correct / attempted * 100).

    // Timestamp for when the test result was recorded.
    // The @ServerTimestamp annotation instructs Firestore to automatically populate this field
    // with the server's current time when this TestResult object is written to the database
    // (if the field is null when being written). On read, Firestore converts this to a java.util.Date.
    @ServerTimestamp
    private Date timestamp;

    // The mode of the test taken (e.g., "SPECIFIC_TOPIC", "RANDOM_ALL_SUBJECT").
    // This helps in categorizing and potentially filtering test results later.
    private String testMode;

    /**
     * Default no-argument constructor.
     * This is REQUIRED by Firebase Firestore for its automatic data mapping
     * when converting Firestore documents back into TestResult objects.
     */
    public TestResult() {}

    /**
     * Constructor used by my application logic (specifically in TestActivity)
     * to create a new TestResult instance when a test session is completed.
     * It calculates the percentage score based on correct and attempted cards.
     * The timestamp is deliberately not set here, as I rely on @ServerTimestamp.
     *
     * @param userId         UID of the user.
     * @param subjectId      ID of the subject.
     * @param subjectTitle   Title of the subject.
     * @param topicId        ID of the topic (can be null).
     * @param topicTitle     Title of the topic (can be null or a descriptive placeholder like "All Random").
     * @param scoreCorrect   Number of correctly answered flashcards.
     * @param cardsAttempted Total number of flashcards attempted.
     * @param testMode       The mode of the test (e.g., from TestActivity.MODE_SPECIFIC_TOPIC).
     */
    public TestResult(String userId, String subjectId, String subjectTitle, String topicId, String topicTitle,
                      int scoreCorrect, int cardsAttempted, String testMode) {
        this.userId = userId;
        this.subjectId = subjectId;
        this.subjectTitle = subjectTitle;
        this.topicId = topicId;
        this.topicTitle = topicTitle;
        this.scoreCorrect = scoreCorrect;
        this.cardsAttempted = cardsAttempted;
        this.testMode = testMode;

        // Calculate and store the percentage. My design includes storing this
        // to avoid recalculation every time the result is displayed.
        if (cardsAttempted > 0) {
            this.percentage = ((double) scoreCorrect / cardsAttempted) * 100.0;
        } else {
            this.percentage = 0.0; // Avoid division by zero if no cards were attempted.
        }
        // The 'timestamp' field is intentionally left null here.
        // The @ServerTimestamp annotation on the field will ensure Firestore populates it
        // with the server's time when this object is saved. This guarantees accurate and consistent timestamps.
    }

    // --- GETTERS ---
    // Standard getter methods for accessing the properties of the TestResult.
    // Firestore uses these (or public fields) during serialization.

    public String getUserId() { return userId; }
    public String getSubjectId() { return subjectId; }
    public String getSubjectTitle() { return subjectTitle; }
    public String getTopicId() { return topicId; }
    public String getTopicTitle() { return topicTitle; }
    public int getScoreCorrect() { return scoreCorrect; }
    public int getCardsAttempted() { return cardsAttempted; }
    public double getPercentage() { return percentage; }

    /**
     * Gets the timestamp of when this test result was recorded.
     * This will be a java.util.Date object when read from Firestore.
     * @return The Date object representing the timestamp.
     */
    public Date getTimestamp() { return timestamp; }
    public String getTestMode() { return testMode; }

    // --- SETTERS ---
    // Standard setter methods. Firestore uses these (or public fields)
    // when deserializing data from a document into a TestResult object.

    public void setUserId(String userId) { this.userId = userId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public void setSubjectTitle(String subjectTitle) { this.subjectTitle = subjectTitle; }
    public void setTopicId(String topicId) { this.topicId = topicId; }
    public void setTopicTitle(String topicTitle) { this.topicTitle = topicTitle; }
    public void setScoreCorrect(int scoreCorrect) { this.scoreCorrect = scoreCorrect; }
    public void setCardsAttempted(int cardsAttempted) { this.cardsAttempted = cardsAttempted; }
    public void setPercentage(double percentage) { this.percentage = percentage; }

    /**
     * Sets the timestamp. This is primarily for Firestore's deserialization process.
     * When creating new TestResult instances in app code, the timestamp is handled by @ServerTimestamp.
     * @param timestamp The Date object for the timestamp.
     */
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void setTestMode(String testMode) { this.testMode = testMode; }
}