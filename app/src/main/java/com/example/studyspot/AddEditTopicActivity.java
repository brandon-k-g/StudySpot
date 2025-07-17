package com.example.studyspot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Retained as it might be intended for future Firestore listener use.
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.studyspot.databinding.ActivityAddEditTopicBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot; // Retained for direct use in callbacks
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap; // Used for creating a map for Firestore updates.
import java.util.List;
import java.util.Map;   // Interface for the HashMap.
import java.util.Objects; // For Objects.requireNonNull, a utility I use for null checks.

/**
 * Activity for creating a new topic or editing an existing one.
 * This involves setting the topic title and associating it with a parent subject.
 * It interacts with Firebase Firestore for all data persistence operations,
 * including creating, reading, updating, and deleting topics and their associated flashcards.
 * My design includes features like confirmation dialogs for significant changes or deletions
 * and batch operations for data integrity when deleting.
 */
public class AddEditTopicActivity extends AppCompatActivity {

    // Log tag for filtering log messages specific to this activity.
    private static final String TAG = "AddEditTopicActivity";

    // Intent extras keys: These constants are crucial for passing data reliably between activities.
    // Key for the ID of the parent Subject when creating a new topic.
    public static final String EXTRA_SUBJECT_ID_FOR_TOPIC = "com.example.studyspot.EXTRA_SUBJECT_ID_FOR_TOPIC";
    // Key for the ID of the Topic being edited.
    public static final String EXTRA_EDIT_TOPIC_ID = "com.example.studyspot.EXTRA_EDIT_TOPIC_ID";
    // Key for the current title of the Topic being edited.
    public static final String EXTRA_CURRENT_TOPIC_TITLE = "com.example.studyspot.EXTRA_CURRENT_TOPIC_TITLE";
    // Key for the original parent Subject ID of the Topic being edited (used to detect if the subject changes).
    public static final String EXTRA_ORIGINAL_SUBJECT_ID = "com.example.studyspot.EXTRA_ORIGINAL_SUBJECT_ID";

    // Result code to indicate to the calling activity that a topic was deleted.
    public static final int RESULT_TOPIC_DELETED = 201;

    // View binding instance for type-safe access to UI elements.
    private ActivityAddEditTopicBinding binding;
    // Firebase Firestore instance, my chosen solution for database operations (data persistence).
    private FirebaseFirestore mDb;
    // Firebase Authentication instance for managing user sessions and securing data.
    private FirebaseAuth mAuth;
    // Represents the currently authenticated Firebase user.
    private FirebaseUser currentUser;

    // Stores the Document ID of the parent subject, passed via intent when creating a new topic.
    private String parentSubjectDocumentIdFromIntent;
    // Stores the Document ID of the subject currently selected in the spinner.
    private String currentSelectedSubjectIdInSpinner;
    // Stores the title/name of the subject currently selected in the spinner.
    private String currentSelectedSubjectNameInSpinner;
    // Stores the Document ID of the topic being edited (if in edit mode).
    private String editingTopicId;
    // Flag to differentiate between "add new topic" and "edit existing topic" modes.
    // This design choice allows reusing this single activity for both operations.
    private boolean isEditMode = false;

    // Variables to store original topic data when in edit mode.
    // These are used to detect if any actual changes were made by the user.
    private String originalLoadedTopicTitle;        // Original title of the topic being edited.
    private String originalLoadedParentSubjectId;   // Original parent subject's Document ID.
    private String originalLoadedParentSubjectName; // Original parent subject's title.

    // Data structures for managing the parent subject selection spinner.
    private List<Subject> subjectsAvailableForSpinner; // Holds full Subject objects, useful for ID and Title.
    private ArrayAdapter<String> subjectSpinnerAdapter;    // Adapter for the AutoCompleteTextView acting as a spinner.
    private List<String> subjectTitlesForSpinner;      // List of subject titles to display in the spinner.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using View Binding.
        binding = ActivityAddEditTopicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase services. These are essential for my application's backend functionality.
        mDb = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Critical check: User must be authenticated to use this activity.
        if (currentUser == null) {
            Log.e(TAG, "User not logged in. Aborting AddEditTopicActivity creation.");
            Toast.makeText(this, "User authentication error. Please log in.", Toast.LENGTH_LONG).show();
            finish(); // Close the activity if no user is logged in.
            return;   // Stop further execution of onCreate.
        }

        // Initialize lists and adapter for the subject selection spinner.
        subjectsAvailableForSpinner = new ArrayList<>();
        subjectTitlesForSpinner = new ArrayList<>();
        subjectSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, subjectTitlesForSpinner);

        // Perform initial UI setup.
        setupToolbar();
        setupSubjectSpinner(); // Configures the adapter and item click listener for subject selection.

        // Process incoming intent data to determine mode (add/edit) and pre-fill data.
        Intent intent = getIntent();
        // This is the parent subject ID if we are coming from "Add Topic" under a specific subject.
        parentSubjectDocumentIdFromIntent = intent.getStringExtra(EXTRA_SUBJECT_ID_FOR_TOPIC);

        if (intent.hasExtra(EXTRA_EDIT_TOPIC_ID)) {
            // ----- EDIT MODE -----
            // If EXTRA_EDIT_TOPIC_ID is present, we are editing an existing topic.
            isEditMode = true;
            editingTopicId = intent.getStringExtra(EXTRA_EDIT_TOPIC_ID);
            originalLoadedTopicTitle = intent.getStringExtra(EXTRA_CURRENT_TOPIC_TITLE);
            originalLoadedParentSubjectId = intent.getStringExtra(EXTRA_ORIGINAL_SUBJECT_ID);
            currentSelectedSubjectIdInSpinner = originalLoadedParentSubjectId; // Initialize spinner selection.

            // Update UI elements for edit mode.
            binding.toolbarAddTopic.setTitle(getString(R.string.title_activity_edit_topic_details));
            binding.editTextTopicTitle.setText(originalLoadedTopicTitle); // Pre-fill current title.
            binding.buttonSaveTopic.setText(getString(R.string.button_update_topic));
            binding.buttonDeleteTopic.setVisibility(View.VISIBLE); // Show delete button.
            binding.buttonDeleteTopic.setOnClickListener(v -> confirmDeleteCurrentTopic());

            // Load subjects for the spinner and attempt to pre-select the topic's original parent subject.
            loadSubjectsForSpinner(originalLoadedParentSubjectId);

        } else if (parentSubjectDocumentIdFromIntent != null) {
            // ----- ADD NEW MODE (with a pre-selected parent subject) -----
            // This case handles creating a new topic when a parent subject is already known.
            isEditMode = false;
            currentSelectedSubjectIdInSpinner = parentSubjectDocumentIdFromIntent; // This subject will be pre-selected.
            originalLoadedParentSubjectId = parentSubjectDocumentIdFromIntent; // Set for consistency, though not strictly "original" here.

            // Update UI elements for add mode.
            binding.toolbarAddTopic.setTitle(getString(R.string.title_activity_add_topic));
            binding.buttonSaveTopic.setText(getString(R.string.save_topic));
            binding.buttonDeleteTopic.setVisibility(View.GONE); // Hide delete button.

            // Load subjects and pre-select the passed parent subject.
            loadSubjectsForSpinner(parentSubjectDocumentIdFromIntent);
        } else {
            // ----- CRITICAL ERROR -----
            // If neither editing nor adding with a parent subject ID, the activity cannot function.
            // This indicates a problem with how this activity was launched.
            Log.e(TAG, "Critical error: Intent is missing required data for either Add or Edit mode. Finishing activity.");
            Toast.makeText(this, "Error: Parent subject context or topic details missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Set the listener for the main save/update button.
        // I use a confirmation step before actual saving if in edit mode and changes are detected.
        binding.buttonSaveTopic.setOnClickListener(v -> saveOrUpdateTopicWithConfirmation());
    }

    /**
     * Configures the toolbar for this activity, including setting the title (dynamically)
     * and enabling the "up" navigation (back arrow).
     */
    private void setupToolbar() {
        setSupportActionBar(binding.toolbarAddTopic);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // The title itself is set within onCreate based on whether it's add or edit mode.
        }
    }

    /**
     * Initializes the AutoCompleteTextView used as a spinner for selecting the parent subject.
     * It sets the adapter and an item click listener to capture user selection.
     * This is a key part of my design for associating topics with subjects.
     */
    private void setupSubjectSpinner() {
        binding.autoCompleteTextViewSubjectForTopic.setAdapter(subjectSpinnerAdapter);
        // Listener to update currentSelectedSubjectIdInSpinner when a subject is chosen.
        binding.autoCompleteTextViewSubjectForTopic.setOnItemClickListener((parent, view, position, id) -> {
            // Validate the selected position.
            if (position >= 0 && position < subjectsAvailableForSpinner.size()) {
                Subject selectedSubject = subjectsAvailableForSpinner.get(position);
                currentSelectedSubjectIdInSpinner = selectedSubject.getDocumentId(); // Store the ID for saving.
                currentSelectedSubjectNameInSpinner = selectedSubject.getTitle(); // Store the name for display/confirmation.
                Log.d(TAG, "User selected Subject in spinner: " + currentSelectedSubjectNameInSpinner + " (ID: " + currentSelectedSubjectIdInSpinner + ")");
            }
        });
    }

    /**
     * Shows or hides a loading indicator (ProgressBar) and enables/disables UI elements
     * to prevent user interaction during processing.
     * This is my standard approach for providing visual feedback during long operations.
     * @param isLoading True to show loading and disable UI, false to hide loading and enable UI.
     */
    private void showLoading(boolean isLoading) {
        binding.progressBarAddTopic.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // Disable interactive elements during loading to prevent conflicts.
        binding.buttonSaveTopic.setEnabled(!isLoading);
        binding.editTextTopicTitle.setEnabled(!isLoading);
        binding.autoCompleteTextViewSubjectForTopic.setEnabled(!isLoading); // Subject spinner.
        if (isEditMode) { // Delete button is only relevant in edit mode.
            binding.buttonDeleteTopic.setEnabled(!isLoading);
        }
    }

    /**
     * Helper method to retrieve a subject's name (title) using its ID from the list
     * of subjects already loaded into the spinner.
     * This is useful for display purposes, like in confirmation messages.
     * @param subjectId The Document ID of the subject.
     * @return The title of the subject, or a default "unknown" string if not found.
     */
    private String getSubjectNameByIdFromSpinnerList(String subjectId) {
        if (subjectId == null || subjectsAvailableForSpinner == null) {
            return getString(R.string.unknown_subject_name); // Default string resource.
        }
        // Iterate through the loaded subjects to find a match.
        for (Subject subject : subjectsAvailableForSpinner) {
            if (subject.getDocumentId() != null && subject.getDocumentId().equals(subjectId)) {
                return subject.getTitle();
            }
        }
        Log.w(TAG, "Subject name not found in spinner list for ID: " + subjectId);
        return getString(R.string.unknown_subject_name); // Fallback.
    }

    /**
     * Loads subjects from Firebase Firestore that belong to the current user.
     * These subjects populate the spinner for selecting a topic's parent subject.
     * This method handles the asynchronous data retrieval and updates the UI.
     * @param subjectIdToPreselect The Document ID of the subject to be pre-selected in the spinner (if any).
     */
    private void loadSubjectsForSpinner(String subjectIdToPreselect) {
        if (currentUser == null) {
            Log.e(TAG, "Cannot load subjects for spinner: Current user is null.");
            Toast.makeText(this, "Authentication error. Cannot load subjects.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Loading subjects for spinner. User: " + currentUser.getUid() + ". Attempting to preselect Subject ID: " + subjectIdToPreselect);
        showLoading(true); // Indicate loading.

        // Query Firestore for subjects belonging to the current user, ordered by title.
        // This is a key data retrieval operation for this activity's functionality.
        mDb.collection(TopicListActivity.SUBJECTS_COLLECTION)
                .whereEqualTo("userId", currentUser.getUid()) // Filter by current user.
                .orderBy("title", Query.Direction.ASCENDING)   // Order alphabetically for user convenience.
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false); // Hide loading indicator.
                    // Clear previous spinner data.
                    subjectsAvailableForSpinner.clear();
                    subjectTitlesForSpinner.clear();

                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Subject subject = document.toObject(Subject.class); // Convert Firestore doc to Subject POJO.
                            subject.setDocumentId(document.getId());          // Manually set the document ID.
                            subjectsAvailableForSpinner.add(subject);
                            subjectTitlesForSpinner.add(subject.getTitle());
                        }
                        subjectSpinnerAdapter.notifyDataSetChanged(); // Refresh spinner display.
                        Log.d(TAG, "Successfully loaded " + subjectsAvailableForSpinner.size() + " subjects into the spinner.");

                        // Store the original parent subject's name if we are in edit mode (for confirmation messages).
                        if (isEditMode && originalLoadedParentSubjectId != null) {
                            originalLoadedParentSubjectName = getSubjectNameByIdFromSpinnerList(originalLoadedParentSubjectId);
                        }

                        // Attempt to pre-select the specified subject in the spinner.
                        int preSelectionIndex = -1;
                        for (int i = 0; i < subjectsAvailableForSpinner.size(); i++) {
                            if (subjectsAvailableForSpinner.get(i).getDocumentId().equals(subjectIdToPreselect)) {
                                preSelectionIndex = i;
                                break;
                            }
                        }

                        if (preSelectionIndex != -1) {
                            // If found, set the text and update current selection variables.
                            binding.autoCompleteTextViewSubjectForTopic.setText(subjectTitlesForSpinner.get(preSelectionIndex), false); // 'false' to prevent filtering.
                            currentSelectedSubjectIdInSpinner = subjectsAvailableForSpinner.get(preSelectionIndex).getDocumentId();
                            currentSelectedSubjectNameInSpinner = subjectTitlesForSpinner.get(preSelectionIndex);
                            Log.d(TAG, "Pre-selected subject in spinner: " + currentSelectedSubjectNameInSpinner);
                        } else if (!subjectsAvailableForSpinner.isEmpty()) {
                            // If specified pre-selection not found, but list is not empty, default to the first subject.
                            // This is a design choice to ensure something is selected if possible.
                            binding.autoCompleteTextViewSubjectForTopic.setText(subjectTitlesForSpinner.get(0), false);
                            currentSelectedSubjectIdInSpinner = subjectsAvailableForSpinner.get(0).getDocumentId();
                            currentSelectedSubjectNameInSpinner = subjectTitlesForSpinner.get(0);
                            Log.w(TAG, "Specified pre-selection ID ("+ subjectIdToPreselect +") not found. Defaulted to first subject: " + currentSelectedSubjectNameInSpinner);
                        } else {
                            // If no subjects are available at all.
                            Toast.makeText(this, "No subjects available. Please create a subject first.", Toast.LENGTH_LONG).show();
                            binding.autoCompleteTextViewSubjectForTopic.setText("", false); // Clear spinner text.
                            currentSelectedSubjectIdInSpinner = null;
                            currentSelectedSubjectNameInSpinner = null;
                            Log.w(TAG, "No subjects available to populate the spinner.");
                        }
                    } else {
                        Log.e(TAG, "Error loading subjects for spinner from Firestore: ", task.getException());
                        Toast.makeText(AddEditTopicActivity.this, getString(R.string.error_loading_subjects), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Initiates the save or update process.
     * It performs validation and, if in edit mode and changes are detected (title or parent subject),
     * it shows a confirmation dialog to the user before proceeding with the Firestore operation.
     * This confirmation step is a UX design choice to prevent accidental major changes.
     */
    private void saveOrUpdateTopicWithConfirmation() {
        String newTitle = Objects.requireNonNull(binding.editTextTopicTitle.getText()).toString().trim();
        // currentSelectedSubjectIdInSpinner and currentSelectedSubjectNameInSpinner are updated by the spinner's
        // OnItemClickListener or during the pre-selection in loadSubjectsForSpinner.

        // Validate topic title.
        if (TextUtils.isEmpty(newTitle)) {
            binding.textInputLayoutTopicTitle.setError(getString(R.string.topic_title_required));
            binding.editTextTopicTitle.requestFocus();
            return;
        } else {
            binding.textInputLayoutTopicTitle.setError(null); // Clear error if valid.
        }

        // Validate parent subject selection.
        if (currentSelectedSubjectIdInSpinner == null || currentSelectedSubjectIdInSpinner.isEmpty()) {
            binding.textInputLayoutSubjectSpinnerForTopic.setError(getString(R.string.topic_subject_required));
            Toast.makeText(this, getString(R.string.topic_subject_required), Toast.LENGTH_SHORT).show();
            return;
        } else {
            binding.textInputLayoutSubjectSpinnerForTopic.setError(null); // Clear error.
        }

        if (isEditMode) {
            // ----- EDIT MODE: Check for changes and confirm -----
            boolean titleChanged = !newTitle.equals(originalLoadedTopicTitle);
            boolean subjectChanged = !currentSelectedSubjectIdInSpinner.equals(originalLoadedParentSubjectId);

            if (titleChanged || subjectChanged) {
                // If changes are detected, build a confirmation message.
                StringBuilder confirmationMessageBuilder = new StringBuilder(getString(R.string.confirm_topic_update_message_intro));
                if (titleChanged) {
                    confirmationMessageBuilder.append(getString(R.string.change_name_detected));
                }
                if (subjectChanged) {
                    // Provide clear information about moving the topic between subjects.
                    String oldParentName = originalLoadedParentSubjectName != null ? originalLoadedParentSubjectName : getString(R.string.unknown_subject_name);
                    String newParentName = currentSelectedSubjectNameInSpinner != null ? currentSelectedSubjectNameInSpinner : getString(R.string.unknown_subject_name);
                    confirmationMessageBuilder.append(getString(R.string.confirm_topic_move_message, oldParentName, newParentName));
                }
                confirmationMessageBuilder.append(getString(R.string.confirm_topic_update_query));

                // Show AlertDialog for confirmation.
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.confirm_topic_update_title))
                        .setMessage(confirmationMessageBuilder.toString())
                        .setPositiveButton(getString(R.string.dialog_update_button), (dialog, which) ->
                                // User confirmed, proceed with actual Firestore update.
                                proceedWithFirestoreSave(newTitle, currentSelectedSubjectIdInSpinner, true))
                        .setNegativeButton(getString(R.string.cancel), null) // User cancelled.
                        .show();
            } else {
                // No changes detected by the user.
                Toast.makeText(this, getString(R.string.no_changes_to_update), Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_CANCELED); // Indicate no actual update occurred.
                finish();
            }
        } else {
            // ----- ADD NEW MODE: No confirmation needed, proceed directly to save -----
            proceedWithFirestoreSave(newTitle, currentSelectedSubjectIdInSpinner, false);
        }
    }

    /**
     * Performs the actual save (add or update) operation to Firebase Firestore.
     * This method is called after validation and any necessary user confirmations.
     * This is where the core data persistence logic for topics resides.
     * @param title The title of the topic.
     * @param subjectId The Document ID of the parent subject.
     * @param isUpdate True if updating an existing topic, false if adding a new one.
     */
    private void proceedWithFirestoreSave(String title, String subjectId, boolean isUpdate) {
        if (currentUser == null) {
            Toast.makeText(this, "Cannot save: User not authenticated. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true); // Show progress indicator.

        if (isUpdate) {
            // ----- UPDATE EXISTING TOPIC -----
            if (editingTopicId == null) { // Should not happen if logic is correct, but good to check.
                Log.e(TAG, "Critical error in proceedWithFirestoreSave (Update): editingTopicId is null.");
                handleSaveFailure(new IllegalStateException("Topic ID missing for update operation."), "Error preparing to update topic");
                return;
            }
            Log.d(TAG, "Attempting to update topic in Firestore. Topic ID: " + editingTopicId + ", New Title: " + title + ", New Subject ID: " + subjectId);
            // Prepare a Map of fields to update.
            Map<String, Object> topicUpdates = new HashMap<>();
            topicUpdates.put("title", title);
            topicUpdates.put("subjectId", subjectId);
            topicUpdates.put("timestamp", FieldValue.serverTimestamp()); // Update timestamp to reflect modification.

            mDb.collection(TopicListActivity.TOPICS_COLLECTION).document(editingTopicId)
                    .update(topicUpdates) // Perform the update.
                    .addOnSuccessListener(aVoid -> handleSaveSuccess(true)) // Handle success.
                    .addOnFailureListener(e -> handleSaveFailure(e, "Error updating topic in Firestore")); // Handle failure.
        } else {
            // ----- ADD NEW TOPIC -----
            Log.d(TAG, "Attempting to add new topic to Firestore. Subject ID: " + subjectId + ", Title: " + title);
            // Create a new Topic POJO. The constructor handles default values like flashcardCount and timestamp.
            Topic newTopic = new Topic(subjectId, title);

            mDb.collection(TopicListActivity.TOPICS_COLLECTION).add(newTopic) // Add to Firestore.
                    .addOnSuccessListener(documentReference -> {
                        // Success: new topic created.
                        Log.d(TAG, "Successfully added new topic to Firestore. New Topic ID: " + documentReference.getId());
                        handleSaveSuccess(false); // Process success (includes incrementing subject's topic count).
                    })
                    .addOnFailureListener(e -> handleSaveFailure(e, "Error saving new topic to Firestore")); // Handle failure.
        }
    }

    /**
     * Handles successful save or update operations.
     * It hides the loading indicator, shows a success message, sets the activity result,
     * and finishes the activity. For new topics, it also increments the topic count on the parent subject.
     * @param isUpdate True if the operation was an update, false if it was a new creation.
     */
    private void handleSaveSuccess(boolean isUpdate) {
        showLoading(false);
        Toast.makeText(AddEditTopicActivity.this,
                isUpdate ? getString(R.string.topic_updated_successfully) : getString(R.string.topic_saved_successfully),
                Toast.LENGTH_SHORT).show();

        // If a new topic was added, I need to increment the topicCount on the parent subject.
        // This is a denormalization strategy for quick display of counts.
        if (!isUpdate && currentSelectedSubjectIdInSpinner != null && !currentSelectedSubjectIdInSpinner.isEmpty()) {
            mDb.collection(TopicListActivity.SUBJECTS_COLLECTION).document(currentSelectedSubjectIdInSpinner)
                    .update("topicCount", FieldValue.increment(1))
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Successfully incremented topic count for subject: " + currentSelectedSubjectIdInSpinner))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to increment topic count for subject: " + currentSelectedSubjectIdInSpinner, e));
        }

        // If an existing topic was moved from an old subject to a new one.
        if (isUpdate && originalLoadedParentSubjectId != null &&
                currentSelectedSubjectIdInSpinner != null &&
                !originalLoadedParentSubjectId.equals(currentSelectedSubjectIdInSpinner)) {
            // Decrement count on the old parent subject.
            mDb.collection(TopicListActivity.SUBJECTS_COLLECTION).document(originalLoadedParentSubjectId)
                    .update("topicCount", FieldValue.increment(-1))
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Successfully decremented topic count for old subject: " + originalLoadedParentSubjectId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to decrement topic count for old subject: " + originalLoadedParentSubjectId, e));
            // Increment count on the new parent subject.
            mDb.collection(TopicListActivity.SUBJECTS_COLLECTION).document(currentSelectedSubjectIdInSpinner)
                    .update("topicCount", FieldValue.increment(1))
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Successfully incremented topic count for new subject: " + currentSelectedSubjectIdInSpinner))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to increment topic count for new subject: " + currentSelectedSubjectIdInSpinner, e));
        }


        setResult(Activity.RESULT_OK); // Indicate success to the calling activity.
        finish(); // Close this activity.
    }

    /**
     * Handles failures during save or update operations.
     * It hides the loading indicator, logs the error, and shows an error message to the user.
     * @param e The exception that occurred.
     * @param logMessage A descriptive message for logging purposes.
     */
    private void handleSaveFailure(Exception e, String logMessage) {
        showLoading(false);
        Log.e(TAG, logMessage + ": ", e); // Log the detailed error.
        Toast.makeText(AddEditTopicActivity.this, "Operation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    /**
     * Initiates the process of deleting the current topic.
     * It first queries Firestore to count associated flashcards to include in the confirmation message.
     * This is a UX design choice to inform the user about the extent of the deletion.
     */
    private void confirmDeleteCurrentTopic() {
        if (!isEditMode || editingTopicId == null) {
            Toast.makeText(this, "No topic currently selected for deletion.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Determine the topic title to display in the dialog.
        String topicTitleForDialog = binding.editTextTopicTitle.getText().toString().trim();
        if (TextUtils.isEmpty(topicTitleForDialog) && originalLoadedTopicTitle != null) {
            topicTitleForDialog = originalLoadedTopicTitle; // Use original if current input is empty.
        }
        if (TextUtils.isEmpty(topicTitleForDialog)) topicTitleForDialog = "this topic"; // Fallback.
        final String finalDialogTitle = topicTitleForDialog;

        showLoading(true); // Show loading while fetching flashcard count.
        // Query to count flashcards associated with this topic.
        // This information enhances the delete confirmation dialog.
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                .whereEqualTo("topicId", editingTopicId)
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false); // Hide loading.
                    int flashcardCount = -1; // Default if count fails.
                    if (task.isSuccessful() && task.getResult() != null) {
                        flashcardCount = task.getResult().size(); // Get the number of associated flashcards.
                        Log.d(TAG, "Found " + flashcardCount + " flashcards associated with topic: " + editingTopicId);
                    } else {
                        Log.e(TAG, "Error counting flashcards for delete confirmation dialog. Topic: " + editingTopicId, task.getException());
                    }
                    // Proceed to show the confirmation dialog with the (potentially updated) flashcard count.
                    showDeleteConfirmationDialog(finalDialogTitle, flashcardCount);
                });
    }

    /**
     * Displays the actual AlertDialog to confirm topic deletion.
     * The message includes the number of associated flashcards that will also be deleted.
     * @param topicTitle The title of the topic being considered for deletion.
     * @param flashcardCount The number of flashcards associated with this topic.
     */
    private void showDeleteConfirmationDialog(String topicTitle, int flashcardCount) {
        String message;
        if (flashcardCount >= 0) {
            // Formatted string to include topic title and flashcard count.
            message = getString(R.string.confirm_delete_topic_message, topicTitle, flashcardCount);
        } else {
            // Fallback message if flashcard count couldn't be determined.
            message = "Are you sure you want to delete the topic \"" + topicTitle + "\"? The number of associated flashcards could not be determined. This action cannot be undone.";
            Log.w(TAG, "Flashcard count was negative when showing delete confirmation for topic: " + topicTitle);
        }
        // Build and show the AlertDialog.
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_topic_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.delete), (dialog, which) ->
                        // User confirmed deletion, proceed with the batch delete operation.
                        deleteTopicAndAssociatedFlashcards())
                .setNegativeButton(getString(R.string.cancel), null) // User cancelled.
                .setIcon(android.R.drawable.ic_dialog_alert) // Standard alert icon.
                .show();
    }

    /**
     * Deletes the specified topic and all its associated flashcards from Firestore.
     * This operation is performed using a WriteBatch for atomicity, ensuring that
     * either all deletions succeed or none do, maintaining data integrity.
     * This is a critical data persistence operation for this feature.
     */
    private void deleteTopicAndAssociatedFlashcards() {
        // Ensure topic ID and user authentication are valid before proceeding.
        if (editingTopicId == null || editingTopicId.isEmpty() || currentUser == null) {
            Log.e(TAG, "Cannot delete topic: Topic ID is missing or user is not authenticated.");
            if(currentUser == null) Toast.makeText(this, "Authentication error. Cannot delete.", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true); // Show progress indicator.
        final String topicIdToDelete = editingTopicId; // Use a final variable for lambda.

        // Create a WriteBatch for atomic operations. This is my preferred way to handle multiple related deletions.
        WriteBatch batch = mDb.batch();

        // First, find all flashcards associated with the topic to be deleted.
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                .whereEqualTo("topicId", topicIdToDelete)
                .get()
                .addOnCompleteListener(flashcardTask -> {
                    if (flashcardTask.isSuccessful() && flashcardTask.getResult() != null) {
                        for (QueryDocumentSnapshot flashcardDoc : flashcardTask.getResult()) {
                            // Add each flashcard deletion to the batch.
                            batch.delete(flashcardDoc.getReference());
                            Log.d(TAG, "Added flashcard (ID: " + flashcardDoc.getId() + ") to batch delete for topic: " + topicIdToDelete);
                        }
                    } else {
                        // Log error if fetching flashcards fails.
                        // My current design decision is to proceed with attempting to delete the topic itself,
                        // which might leave orphaned flashcards if this query fails but the topic deletion succeeds.
                        // A more robust implementation might halt the process or retry.
                        Log.e(TAG, "Error fetching flashcards for batch deletion. Topic: " + topicIdToDelete, flashcardTask.getException());
                        Toast.makeText(this, "Warning: Could not list flashcards for deletion. Orphaned data may result.", Toast.LENGTH_LONG).show();
                    }
                    // Add the topic itself to the batch for deletion.
                    batch.delete(mDb.collection(TopicListActivity.TOPICS_COLLECTION).document(topicIdToDelete));
                    Log.d(TAG, "Added topic (ID: " + topicIdToDelete + ") to batch delete.");

                    // Commit the batch operation.
                    batch.commit()
                            .addOnSuccessListener(aVoid -> handleDeleteSuccess()) // Handle successful batch commit.
                            .addOnFailureListener(e -> handleDeleteFailure(e, "Error during batch deletion of topic and flashcards")); // Handle batch commit failure.
                });
    }

    /**
     * Handles successful deletion of a topic (and its flashcards).
     * It updates UI, shows a success message, and sets the activity result.
     * Also, it decrements the topicCount on the parent subject.
     */
    private void handleDeleteSuccess() {
        showLoading(false);
        Toast.makeText(AddEditTopicActivity.this, getString(R.string.topic_deleted_successfully), Toast.LENGTH_SHORT).show();

        // Decrement topicCount on the original parent subject.
        // This ensures data consistency for subject-level summaries.
        if (originalLoadedParentSubjectId != null && !originalLoadedParentSubjectId.isEmpty()) {
            mDb.collection(TopicListActivity.SUBJECTS_COLLECTION).document(originalLoadedParentSubjectId)
                    .update("topicCount", FieldValue.increment(-1))
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Successfully decremented topic count for subject: " + originalLoadedParentSubjectId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to decrement topic count for subject: " + originalLoadedParentSubjectId, e));
        } else {
            Log.w(TAG, "Could not decrement topic count as originalLoadedParentSubjectId was null/empty during topic delete.");
        }

        setResult(RESULT_TOPIC_DELETED); // Use the defined constant for clarity.
        finish(); // Close this activity.
    }

    /**
     * Handles failures during the deletion process.
     * @param e The exception that occurred.
     * @param logMessage A descriptive message for logging.
     */
    private void handleDeleteFailure(Exception e, String logMessage) {
        showLoading(false);
        Log.e(TAG, logMessage + ": ", e);
        Toast.makeText(AddEditTopicActivity.this, getString(R.string.error_deleting_topic) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    /**
     * Handles action bar item clicks, specifically the "home" (back) button.
     * Overridden to ensure RESULT_CANCELED is set if the user navigates back
     * without completing an action.
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to proceed,
     * true to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // User pressed the back arrow in the toolbar.
            setResult(Activity.RESULT_CANCELED); // Indicate no explicit save/delete action was completed.
            finish(); // Close this activity.
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}