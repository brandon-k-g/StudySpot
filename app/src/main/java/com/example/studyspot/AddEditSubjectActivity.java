package com.example.studyspot;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.studyspot.databinding.ActivityAddEditSubjectBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

/**
 * Activity responsible for creating a new subject or editing an existing one.
 * This activity handles user input for the subject title and interacts with
 * Firebase Firestore for data persistence. It also manages the deletion
 * of a subject and its associated topics and flashcards (cascading delete logic implemented here).
 */
public class AddEditSubjectActivity extends AppCompatActivity {

    // Log tag for identifying messages from this specific activity.
    private static final String TAG = "AddEditSubjectActivity";

    // Intent extras keys: These are constants I've defined for robust data passing between activities.
    // Key for passing the ID of the subject to be edited.
    public static final String EXTRA_EDIT_SUBJECT_ID = "com.example.studyspot.EXTRA_EDIT_SUBJECT_ID";
    // Key for passing the current title of the subject being edited.
    public static final String EXTRA_CURRENT_SUBJECT_TITLE = "com.example.studyspot.EXTRA_CURRENT_SUBJECT_TITLE";
    // Key for returning the updated title of a subject after an edit.
    public static final String EXTRA_UPDATED_SUBJECT_TITLE = "com.example.studyspot.EXTRA_UPDATED_SUBJECT_TITLE";
    // Key for passing the subject ID to other activities, like when creating a new topic for this subject.
    public static final String EXTRA_SUBJECT_ID_FOR_TOPIC = "com.example.studyspot.EXTRA_SUBJECT_ID_FOR_TOPIC";


    // Result code specifically for when a subject is deleted from this edit activity.
    // This allows the calling activity (SubjectListActivity) to recognize this specific outcome.
    public static final int RESULT_OK_SUBJECT_DELETED_FROM_EDIT_ACTIVITY = 301;

    // View binding instance for accessing UI elements in a null-safe and type-safe manner.
    private ActivityAddEditSubjectBinding binding;
    // Firebase Firestore instance, my chosen database for data persistence.
    private FirebaseFirestore mDb;
    // Firebase Authentication instance to manage user identity and secure data access.
    private FirebaseAuth mAuth;

    // Stores the ID of the subject currently being edited, if in edit mode.
    private String currentEditingSubjectId;
    // Stores the original title of the subject before editing, useful for context or comparison.
    private String originalSubjectTitle;
    // Flag to determine if the activity is in "add new" or "edit existing" mode.
    // This design allows me to reuse this activity for both creating and modifying subjects.
    private boolean isEditMode = false;

    /**
     * Called when the activity is first created.
     * Initializes view binding, Firebase services, toolbar, and determines
     * if the activity is in "add" or "edit" mode based on intent extras.
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in {@link #onSaveInstanceState}. Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using view binding for easy access to UI components.
        binding = ActivityAddEditSubjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase services. These are fundamental for my app's backend.
        mDb = FirebaseFirestore.getInstance(); // Firestore for database operations.
        mAuth = FirebaseAuth.getInstance();     // Firebase Auth for user management.

        // Setup the toolbar with a title and back navigation.
        setSupportActionBar(binding.toolbarAddSubject);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Check if the intent contains an EXTRA_EDIT_SUBJECT_ID.
        // If it does, I set the activity to "Edit Mode".
        if (getIntent().hasExtra(EXTRA_EDIT_SUBJECT_ID)) {
            isEditMode = true;
            currentEditingSubjectId = getIntent().getStringExtra(EXTRA_EDIT_SUBJECT_ID);
            originalSubjectTitle = getIntent().getStringExtra(EXTRA_CURRENT_SUBJECT_TITLE);

            // Customize UI for "Edit Mode".
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.title_activity_edit_subject_details));
            }
            binding.buttonSaveSubject.setText(getString(R.string.update_subject)); // Change button text.
            if (originalSubjectTitle != null) {
                binding.editTextSubjectTitle.setText(originalSubjectTitle); // Pre-fill current title.
            }
            // The delete button is only visible and functional in edit mode.
            binding.buttonDeleteSubject.setVisibility(View.VISIBLE);
            binding.buttonDeleteSubject.setOnClickListener(v -> confirmDeleteThisSubject());

        } else {
            // If no EXTRA_EDIT_SUBJECT_ID, then it's "Add Mode".
            isEditMode = false;
            // Customize UI for "Add Mode".
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.title_activity_add_subject));
            }
            binding.buttonSaveSubject.setText(getString(R.string.save_subject));
            binding.buttonDeleteSubject.setVisibility(View.GONE); // Delete button is hidden when adding a new subject.
        }

        // Set the click listener for the save/update button.
        binding.buttonSaveSubject.setOnClickListener(v -> saveOrUpdateSubject());
    }

    /**
     * Handles action bar item clicks, specifically the "home" (back) button.
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to proceed,
     * true to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // If the home (back arrow) button is pressed, I finish the activity.
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED); // Indicate no explicit save/delete action was completed.
            finish(); // Close this activity and return to the previous one.
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Manages the visibility of the loading indicator (ProgressBar) and
     * enables/disables UI elements to prevent interaction during processing.
     * This is a common UI pattern I use to provide feedback during operations.
     * @param isLoading True if loading is in progress, false otherwise.
     */
    private void showLoading(boolean isLoading) {
        binding.progressBarAddSubject.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // Disable buttons and input fields during loading to prevent multiple submissions or edits.
        binding.buttonSaveSubject.setEnabled(!isLoading);
        binding.editTextSubjectTitle.setEnabled(!isLoading);
        if (isEditMode) { // Delete button is only active in edit mode.
            binding.buttonDeleteSubject.setEnabled(!isLoading);
        }
    }

    /**
     * Saves a new subject or updates an existing one in Firebase Firestore.
     * This method handles input validation, prepares the data, and executes
     * the appropriate Firestore write operation (add or update).
     * This is a core function for data persistence of subjects.
     */
    private void saveOrUpdateSubject() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // User must be logged in to save or update subjects. This is a basic security measure.
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to perform this action.", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = binding.editTextSubjectTitle.getText().toString().trim();

        // Validate that the subject title is not empty. This is a required field in my design.
        if (TextUtils.isEmpty(title)) {
            binding.textInputLayoutSubjectTitle.setError(getString(R.string.subject_title_required));
            binding.editTextSubjectTitle.requestFocus(); // Focus on the field for user convenience.
            return;
        } else {
            binding.textInputLayoutSubjectTitle.setError(null); // Clear error if valid.
        }

        showLoading(true); // Show progress indicator.
        String userId = currentUser.getUid(); // Get current user's ID for associating the subject.

        if (isEditMode && currentEditingSubjectId != null) {
            // ----- UPDATE EXISTING SUBJECT -----
            Log.d(TAG, "Attempting to update subject. ID: " + currentEditingSubjectId + ", New Title: " + title);
            DocumentReference subjectRef = mDb.collection(TopicListActivity.SUBJECTS_COLLECTION).document(currentEditingSubjectId);
            // I'm only updating the title here. Other fields might be updated elsewhere or not at all via this screen.
            subjectRef.update("title", title)
                    .addOnSuccessListener(aVoid -> {
                        showLoading(false);
                        Log.d(TAG, "Subject updated successfully in Firestore. ID: " + currentEditingSubjectId);
                        Toast.makeText(AddEditSubjectActivity.this, getString(R.string.subject_updated_successfully), Toast.LENGTH_SHORT).show();
                        // Prepare result intent to pass back the updated title.
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_UPDATED_SUBJECT_TITLE, title);
                        setResult(RESULT_OK, resultIntent); // Indicate success.
                        finish(); // Close activity.
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Log.e(TAG, "Error updating subject in Firestore. ID: " + currentEditingSubjectId, e);
                        Toast.makeText(AddEditSubjectActivity.this, getString(R.string.error_updating_subject) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

        } else {
            // ----- SAVE NEW SUBJECT -----
            Log.d(TAG, "Attempting to save new subject. UserID: " + userId + ", Title: " + title);
            // Create a new Subject object. I've defined default values for fields not set at creation time.
            // This matches the Subject POJO structure.
            Subject newSubject = new Subject(
                    title,
                    "N/A",   // previousResult - Default placeholder
                    "0 hrs", // studyTime - Default placeholder
                    0,       // revisionProgress - Initial value
                    0,       // flashcardsCompleted - Initial value
                    0,       // flashcardsTotal - Initial value
                    0,       // streakCount - Initial value
                    ""       // cardColorHex - Default, planning to implement a color picker later.
            );
            newSubject.setUserId(userId); // Associate with the current user.

            // Add the new subject to the "subjects" collection in Firestore.
            mDb.collection(TopicListActivity.SUBJECTS_COLLECTION)
                    .add(newSubject) // Firestore generates a unique ID.
                    .addOnSuccessListener(documentReference -> {
                        showLoading(false);
                        String newSubjectId = documentReference.getId(); // Get the ID of the newly created subject.
                        Log.d(TAG, "New subject saved successfully to Firestore. New ID: " + newSubjectId);
                        Toast.makeText(AddEditSubjectActivity.this, getString(R.string.subject_saved_successfully), Toast.LENGTH_SHORT).show();

                        // After saving, I've decided to navigate directly to the TopicListActivity for this new subject.
                        // This provides a smooth workflow for the user.
                        Intent intentToTopicList = new Intent(AddEditSubjectActivity.this, TopicListActivity.class);
                        intentToTopicList.putExtra(TopicListActivity.EXTRA_SUBJECT_ID, newSubjectId);
                        intentToTopicList.putExtra(TopicListActivity.EXTRA_SUBJECT_TITLE, title);
                        setResult(RESULT_OK); // Set result for SubjectListActivity if it needs to refresh.
                        startActivity(intentToTopicList); // Navigate.
                        finish(); // Close this activity.
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Log.e(TAG, "Error adding new subject to Firestore.", e);
                        Toast.makeText(AddEditSubjectActivity.this, getString(R.string.error_saving_subject) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    /**
     * Displays a confirmation dialog before deleting the current subject.
     * This is an important UX step to prevent accidental data loss.
     */
    private void confirmDeleteThisSubject() {
        // Use the original title if available, otherwise the current text in the input field.
        String titleForConfirmation = (originalSubjectTitle != null && !originalSubjectTitle.isEmpty())
                ? originalSubjectTitle
                : binding.editTextSubjectTitle.getText().toString().trim();
        if (TextUtils.isEmpty(titleForConfirmation)) {
            titleForConfirmation = "this subject"; // Fallback if title is somehow empty.
        }

        // Build and show the AlertDialog.
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_subject_title))
                // Using formatted string for a more user-friendly message.
                .setMessage(getString(R.string.confirm_delete_subject_message, titleForConfirmation))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    // User confirmed delete, proceed with deletion logic.
                    // My design requires deleting associated topics and flashcards as well (cascading delete).
                    // For now, topics are handled; flashcards deletion would be handled transitively if topics manage their flashcards.
                    deleteSubjectAndAssociatedTopicsFromEditScreen(currentEditingSubjectId);
                })
                .setNegativeButton(getString(R.string.cancel), null) // User cancelled, do nothing.
                .setIcon(android.R.drawable.ic_dialog_alert) // Standard alert icon.
                .show();
    }

    /**
     * Deletes all topics associated with a given subject ID from Firestore.
     * This is part of my cascading delete implementation to maintain data integrity.
     * After deleting topics, it proceeds to delete the subject document itself.
     * @param subjectIdToDelete The ID of the subject whose topics are to be deleted.
     */
    private void deleteSubjectAndAssociatedTopicsFromEditScreen(String subjectIdToDelete) {
        if (subjectIdToDelete == null || subjectIdToDelete.isEmpty()) {
            Log.e(TAG, "Subject ID to delete is null or empty. Cannot proceed with topic deletion.");
            Toast.makeText(this, "Error: Critical Subject ID missing for deletion process.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Starting deletion of associated topics for subject ID: " + subjectIdToDelete);
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // Authentication check is crucial before any destructive operation.
        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Cannot delete subject or topics.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true); // Indicate processing.
        CollectionReference topicsRef = mDb.collection(TopicListActivity.TOPICS_COLLECTION);
        // Query for all topics where 'subjectId' matches the ID of the subject being deleted.
        topicsRef.whereEqualTo("subjectId", subjectIdToDelete).get()
                .addOnCompleteListener(topicTask -> {
                    if (topicTask.isSuccessful() && topicTask.getResult() != null) {
                        // Using a WriteBatch for atomic deletion of multiple topic documents.
                        // This is more efficient and ensures all deletions succeed or fail together.
                        WriteBatch batch = mDb.batch();
                        int topicCount = topicTask.getResult().size();
                        for (QueryDocumentSnapshot document : topicTask.getResult()) {
                            batch.delete(document.getReference()); // Add delete operation to the batch.
                        }
                        Log.d(TAG, "Found " + topicCount + " topics to delete for subject ID: " + subjectIdToDelete);

                        // Commit the batch operation.
                        batch.commit().addOnCompleteListener(batchCommitTask -> {
                            if (batchCommitTask.isSuccessful()) {
                                Log.d(TAG, "Successfully deleted all associated topics (batch commit) for subject ID: " + subjectIdToDelete);
                                // After topics are deleted, proceed to delete the subject document itself.
                                deleteSubjectDocumentFromEditScreen(subjectIdToDelete);
                            } else {
                                showLoading(false);
                                Log.e(TAG, "Error committing batch delete for topics. Subject: " + subjectIdToDelete, batchCommitTask.getException());
                                Toast.makeText(this, "Error deleting associated topics. Subject deletion aborted.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        showLoading(false);
                        // If fetching topics fails, I've decided to log the error and still attempt to delete the subject document.
                        // This could leave orphaned topics if the query failed but subject deletion succeeds.
                        // A more robust solution might involve retries or specific error handling.
                        Log.e(TAG, "Error fetching topics for deletion. Subject: " + subjectIdToDelete, topicTask.getException());
                        Toast.makeText(this, "Could not fetch topics for deletion, but will attempt to delete subject.", Toast.LENGTH_LONG).show();
                        deleteSubjectDocumentFromEditScreen(subjectIdToDelete); // Attempt to delete subject anyway.
                    }
                });
    }

    /**
     * Deletes the subject document itself from Firestore.
     * This is typically called after its associated topics have been handled.
     * @param subjectIdToDelete The ID of the subject document to delete.
     */
    private void deleteSubjectDocumentFromEditScreen(String subjectIdToDelete) {
        Log.d(TAG, "Proceeding to delete subject document. ID: " + subjectIdToDelete);
        // Assuming showLoading(true) was called by the initiating delete method.
        mDb.collection(TopicListActivity.SUBJECTS_COLLECTION).document(subjectIdToDelete).delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Log.d(TAG, "Subject document successfully deleted from Firestore. ID: " + subjectIdToDelete);
                    Toast.makeText(this, getString(R.string.subject_deleted_successfully), Toast.LENGTH_SHORT).show();
                    // Set a specific result code so the calling activity (SubjectListActivity)
                    // knows a subject was deleted and can refresh its list appropriately.
                    setResult(RESULT_OK_SUBJECT_DELETED_FROM_EDIT_ACTIVITY);
                    finish(); // Close this activity.
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error deleting subject document from Firestore. ID: " + subjectIdToDelete, e);
                    Toast.makeText(this, getString(R.string.error_deleting_subject) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // If subject document deletion fails, the user is notified, but the activity remains open
                    // for them to retry or cancel. Associated topics might have been deleted already.
                });
    }
}