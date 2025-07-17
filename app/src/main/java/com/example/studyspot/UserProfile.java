package com.example.studyspot; // Ensure this matches your package name

/**
 * Represents a user's profile within the StudySpot application.
 * This class is a POJO (Plain Old Java Object) designed as the data model for
 * storing extended user information in Firebase Firestore, separate from the
 * core Firebase Authentication user details.
 * My design for UserProfile includes essential fields like display name and email,
 * a server-generated creation timestamp, and optional fields for a photo URL, bio, and role.
 * The 'role' field could be used in future enhancements to differentiate user capabilities.
 */
public class UserProfile {
    private String displayName; // The user's chosen display name.
    private String email;       // The user's email address, likely matching their Firebase Auth email.
    private Object createdAt;
    private String photoUrl;  // URL string pointing to the user's profile picture.
    private String bio;       // A short biography or description provided by the user.
    private String role;      // User role, e.g., "student". Could be expanded later (e.g., "tutor", "admin").

    /**
     * Default no-argument constructor.
     * This is REQUIRED by Firebase Firestore for its automatic data mapping
     * when converting Firestore documents back into UserProfile objects.
     */
    public UserProfile() {}

    /**
     * Constructor used by my application logic (e.g., in SignUpActivity)
     * when creating a new user profile to be saved to Firestore.
     * It initializes core fields and sets default values for optional ones.
     *
     * @param displayName The display name for the new user.
     * @param email       The email address for the new user.
     * @param createdAt   Expected to be FieldValue.serverTimestamp() passed from the calling code,
     * which Firestore will replace with the actual server timestamp upon saving.
     */
    public UserProfile(String displayName, String email, Object createdAt) {
        this.displayName = displayName;
        this.email = email;
        this.createdAt = createdAt; // This will hold the FieldValue.serverTimestamp() placeholder initially.
        this.role = "student";      // My design defaults new users to the "student" role.
        // Initialize other optional fields to sensible defaults.
        this.photoUrl = null;       // No photo URL by default.
        this.bio = "";              // Empty bio by default.
    }

    // --- Getters ---
    // Standard getter methods for accessing the profile's properties.
    // Firestore uses these (or public fields) when serializing data.

    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }

    /**
     * Gets the creation timestamp of the profile.
     * When read from Firestore after being set by FieldValue.serverTimestamp(),
     * this will typically be a com.google.firebase.Timestamp object.
     * Before saving, it holds the FieldValue.serverTimestamp() placeholder.
     * @return The timestamp object.
     */
    public Object getCreatedAt() { return createdAt; }
    public String getPhotoUrl() { return photoUrl; }
    public String getBio() { return bio; }
    public String getRole() { return role; }

    // --- Setters ---
    // Standard setter methods. These are useful if profile data needs to be modified
    // in the application code before saving, and Firestore uses them for deserialization.

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setEmail(String email) { this.email = email; }

    /**
     * Sets the creation timestamp. This is primarily for Firestore's deserialization process.
     * When creating new profiles in app code, 'createdAt' is set using FieldValue.serverTimestamp() in the constructor.
     * @param createdAt The timestamp object (typically a Firestore Timestamp when read).
     */
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setBio(String bio) { this.bio = bio; }
    public void setRole(String role) { this.role = role; }

    /*
    // Alternative Timestamp Design Consideration:
    // I also considered an alternative method for handling the creation timestamp,
    // which involves using the @ServerTimestamp annotation directly on a java.util.Date field:
    //
    // @ServerTimestamp // Firestore populates this with server time on write if it's null
    // private Date createdAtDate;
    //
    // public Date getCreatedAtDate() { return createdAtDate; }
    // public void setCreatedAtDate(Date createdAtDate) { this.createdAtDate = createdAtDate; }
    //
    // If I used this, the constructor might initialize 'createdAtDate' to null, and Firestore
    // would automatically set it. However, for this UserProfile POJO, I've implemented
    // the FieldValue.serverTimestamp() approach with an 'Object' type field. Both are valid
    // strategies for server-side timestamping provided by Firebase.
    */
}