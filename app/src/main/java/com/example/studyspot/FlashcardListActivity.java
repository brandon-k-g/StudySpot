package com.example.studyspot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.studyspot.databinding.ActivityFlashcardListBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue; // Used for atomic increments/decrements in Firestore.
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
// import java.util.Locale; // This import was commented out, retaining that state as it's not used directly.

/**
 * Activity responsible for displaying a list of flashcards for a specific topic.
 * It allows users to view, add, edit, and delete flashcards.
 * This activity interacts heavily with Firebase Firestore for data persistence and
 * uses a RecyclerView to efficiently display the flashcard list.
 * It implements {@link FlashcardsAdapter.OnFlashcardClickListener} to handle
 * actions performed on individual flashcard items.
 * My design ensures that changes made here (like adding or deleting flashcards)
 * are communicated back to the calling activity (TopicListActivity) so it can refresh counts.
 */
public class FlashcardListActivity extends AppCompatActivity implements FlashcardsAdapter.OnFlashcardClickListener {

    // Log tag for identifying messages from this activity.
    private static final String TAG = "FlashcardListActivity";

    // Intent extras keys: Constants I've defined for robust data passing from TopicListActivity.
    // Key for the ID of the current topic whose flashcards are to be displayed.
    public static final String EXTRA_TOPIC_ID = "com.example.studyspot.EXTRA_TOPIC_ID";
    // Key for the title of the current topic, used for display purposes (e.g., toolbar).
    public static final String EXTRA_TOPIC_TITLE = "com.example.studyspot.EXTRA_TOPIC_TITLE";
    // Key for the ID of the parent subject, needed when creating/editing flashcards for context.
    public static final String EXTRA_SUBJECT_ID_FOR_FLASHCARD = "com.example.studyspot.EXTRA_SUBJECT_ID_FOR_FLASHCARD";
    // Key for the title of the parent subject, mainly for context in other activities (e.g. TestActivity).
    public static final String EXTRA_PARENT_SUBJECT_TITLE = "com.example.studyspot.EXTRA_PARENT_SUBJECT_TITLE";


    // Defines the name of the Firestore collection where flashcard data is stored.
    // Using a constant for this is good practice for consistency and maintainability.
    public static final String FLASHCARDS_COLLECTION = "flashcards";

    // View binding instance for type-safe access to UI elements.
    private ActivityFlashcardListBinding binding;
    // Firebase Firestore instance for database operations (my chosen data persistence solution).
    private FirebaseFirestore mDb;
    // Firebase Authentication instance for user management.
    private FirebaseAuth mAuth;
    // Represents the currently authenticated Firebase user.
    private FirebaseUser currentUser;

    // Adapter for the RecyclerView that displays flashcards.
    private FlashcardsAdapter flashcardsAdapter;
    // List to hold the Flashcard objects fetched from Firestore.
    private List<Flashcard> flashcardList;

    // Stores the ID of the current topic being displayed.
    private String currentTopicId;
    // Stores the title of the current topic.
    private String currentTopicTitle;
    // Stores the ID of the parent subject for the current topic.
    private String currentSubjectId;
    // Stores the title of the parent subject.
    private String parentSubjectTitle;


    // ActivityResultLauncher for handling results from AddEditFlashcardActivity.
    // This is my chosen modern approach for getting results back from activities.
    private ActivityResultLauncher<Intent> addOrEditFlashcardLauncher;
    // Flag to track if data (flashcards) has been changed (added/edited/deleted).
    // This is used to signal the calling activity (TopicListActivity) to refresh if necessary.
    private boolean mDataWasChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using view binding.
        binding = ActivityFlashcardListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.d(TAG, "onCreate: Activity starting up.");

        // Initialize Firebase services.
        mDb = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Critical authentication check. This activity requires a logged-in user.
        if (currentUser == null) {
            Log.e(TAG, "onCreate: User is not authenticated. Finishing FlashcardListActivity.");
            Toast.makeText(this, "Authentication error. Please log in again to view flashcards.", Toast.LENGTH_LONG).show();
            finish(); // Close the activity.
            return;   // Stop further execution.
        }
        Log.i(TAG, "onCreate: Authenticated user: " + currentUser.getUid());

        // Initialize the list that will hold flashcard data.
        flashcardList = new ArrayList<>();

        // Retrieve and validate data passed from the calling activity (TopicListActivity).
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_TOPIC_ID) &&
                intent.hasExtra(EXTRA_TOPIC_TITLE) &&
                intent.hasExtra(EXTRA_SUBJECT_ID_FOR_FLASHCARD) &&
                intent.hasExtra(EXTRA_PARENT_SUBJECT_TITLE)) {
            // Assign intent data to local variables. These define the context for this screen.
            currentTopicId = intent.getStringExtra(EXTRA_TOPIC_ID);
            currentTopicTitle = intent.getStringExtra(EXTRA_TOPIC_TITLE);
            currentSubjectId = intent.getStringExtra(EXTRA_SUBJECT_ID_FOR_FLASHCARD);
            parentSubjectTitle = intent.getStringExtra(EXTRA_PARENT_SUBJECT_TITLE);

            Log.i(TAG, "onCreate: Successfully received context - Topic ID: " + currentTopicId +
                    ", Topic Title: " + currentTopicTitle +
                    ", Subject ID: " + currentSubjectId +
                    ", Parent Subject Title: " + parentSubjectTitle);
        } else {
            // If required data is missing, the activity cannot function correctly.
            Log.e(TAG, "onCreate: Critical information (Topic ID/Title, Subject ID, Parent Subject Title) was not passed via intent. Finishing activity.");
            Toast.makeText(this, "Error: Missing required details to display flashcards.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Setup UI components.
        setupToolbar();
        setupRecyclerView();
        setupActionButtons(); // FAB for adding new flashcards and "Test this topic" button.

        // Register the ActivityResultLauncher.
        // This handles the result when AddEditFlashcardActivity finishes.
        addOrEditFlashcardLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "addOrEditFlashcardLauncher received result. Result code: " + result.getResultCode());
                    // If a flashcard was successfully added, edited, or deleted.
                    if (result.getResultCode() == Activity.RESULT_OK ||
                            result.getResultCode() == AddEditFlashcardActivity.RESULT_FLASHCARD_DELETED) {
                        Log.i(TAG, "addOrEditFlashcardLauncher: AddEditFlashcardActivity indicated data changed. Refreshing flashcard list.");
                        mDataWasChanged = true; // Mark that data has changed.
                        loadFlashcards();       // Reload flashcards to reflect changes.
                    }
                }
        );
        Log.d(TAG, "onCreate: ActivityResultLauncher successfully registered.");

        // Initial load of flashcards for the current topic.
        loadFlashcards();
    }

    /**
     * Configures the toolbar for this activity, setting the title to the current topic's title
     * and enabling the "up" navigation button (back arrow).
     */
    private void setupToolbar() {
        setSupportActionBar(binding.toolbarFlashcards);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Show back arrow.
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Set toolbar title dynamically based on the current topic.
            String title = currentTopicTitle != null ? currentTopicTitle : getString(R.string.title_activity_flashcards);
            getSupportActionBar().setTitle(title);
            Log.d(TAG, "Toolbar setup successful. Title set to: " + title);
        } else {
            Log.e(TAG, "setupToolbar: Failed to getSupportActionBar. Toolbar might not be correctly configured in layout.");
        }
    }

    /**
     * Initializes the RecyclerView, including setting its LayoutManager and Adapter.
     * The adapter is responsible for binding flashcard data to individual list items.
     */
    private void setupRecyclerView() {
        // Using a LinearLayoutManager for a vertical list of flashcards.
        binding.recyclerViewFlashcards.setLayoutManager(new LinearLayoutManager(this));
        // Initialize the adapter with an empty list initially and set this activity as the click listener.
        flashcardsAdapter = new FlashcardsAdapter(this, flashcardList, this);
        binding.recyclerViewFlashcards.setAdapter(flashcardsAdapter);
        Log.d(TAG, "RecyclerView and FlashcardsAdapter setup complete.");
    }

    /**
     * Sets up click listeners for action buttons on this screen, such as the
     * FloatingActionButton (FAB) for adding new flashcards and the "Test this topic" button.
     */
    private void setupActionButtons() {
        // FAB click listener: Launches AddEditFlashcardActivity to create a new flashcard.
        binding.fabAddFlashcardToList.setOnClickListener(v -> {
            Log.d(TAG, "Add Flashcard FAB clicked. Launching AddEditFlashcardActivity for Topic ID: " + currentTopicId);
            Intent intentAdd = new Intent(this, AddEditFlashcardActivity.class);
            // Pass necessary context (topic ID, subject ID, topic title) to the add/edit activity.
            intentAdd.putExtra(AddEditFlashcardActivity.EXTRA_TOPIC_ID_FOR_FLASHCARD, currentTopicId);
            intentAdd.putExtra(AddEditFlashcardActivity.EXTRA_SUBJECT_ID_FOR_FLASHCARD, currentSubjectId);
            intentAdd.putExtra(AddEditFlashcardActivity.EXTRA_TOPIC_TITLE_FOR_FLASHCARD, currentTopicTitle); // For AI context
            // It's important to pass the parent subject title for AI context in AddEditFlashcardActivity too
            intentAdd.putExtra(AddEditFlashcardActivity.EXTRA_SUBJECT_TITLE_FOR_FLASHCARD_CONTEXT, parentSubjectTitle);
            addOrEditFlashcardLauncher.launch(intentAdd); // Launch using the registered launcher.
        });

        // "Test this topic" button listener: Launches TestActivity for the current topic.
        binding.buttonTestThisTopic.setOnClickListener(v -> {
            Log.d(TAG, "\"Test this topic\" button clicked for Topic ID: " + currentTopicId);
            if (flashcardList.isEmpty()) {
                // My design choice: prevent starting a test if there are no flashcards.
                Toast.makeText(this, "No flashcards available in this topic to start a test.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent testIntent = new Intent(this, TestActivity.class);
            // Pass all necessary IDs and titles for the TestActivity context.
            testIntent.putExtra(TestActivity.EXTRA_SUBJECT_ID, currentSubjectId);
            testIntent.putExtra(TestActivity.EXTRA_TOPIC_ID, currentTopicId);
            testIntent.putExtra(TestActivity.EXTRA_SUBJECT_TITLE, parentSubjectTitle);
            testIntent.putExtra(TestActivity.EXTRA_TOPIC_TITLE, currentTopicTitle);
            // Specify that the test mode is for a specific topic.
            testIntent.putExtra(TestActivity.EXTRA_TEST_MODE, TestActivity.MODE_SPECIFIC_TOPIC);
            startActivity(testIntent);
        });
        Log.d(TAG, "Action buttons (FAB for add, Button for test) setup complete.");
    }

    /**
     * Loads flashcards for the current topic from Firebase Firestore.
     * This is the primary data retrieval method for this activity.
     * It queries the 'flashcards' collection, filtering by the currentTopicId
     * and ordering by timestamp.
     */
    private void loadFlashcards() {
        Log.i(TAG, "loadFlashcards: Attempting to load flashcards for Topic ID: " + currentTopicId);
        // Ensure user is authenticated and topicId is valid before querying.
        if (currentUser == null) {
            Log.e(TAG, "loadFlashcards: Aborted. Current user is null.");
            updateFlashcardListUI(new ArrayList<>()); // Clear UI if user is somehow null.
            return;
        }
        if (currentTopicId == null || currentTopicId.isEmpty()) {
            Log.e(TAG, "loadFlashcards: Aborted. currentTopicId is null or empty.");
            updateFlashcardListUI(new ArrayList<>()); // Clear UI if topic ID is invalid.
            return;
        }
        binding.textViewFlashcardsHeader.setVisibility(View.GONE); // Hide "no flashcards" message initially.

        // Firestore query to fetch flashcards.
        // This is a key part of my data persistence strategy.
        mDb.collection(FLASHCARDS_COLLECTION)
                .whereEqualTo("topicId", currentTopicId) // Filter by the current topic.
                .orderBy("timestamp", Query.Direction.ASCENDING) // Order flashcards by creation time.
                .get()
                .addOnCompleteListener(task -> {
                    List<Flashcard> fetchedFlashcards = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                // Convert Firestore document to Flashcard POJO.
                                Flashcard flashcard = document.toObject(Flashcard.class);
                                flashcard.setDocumentId(document.getId()); // Manually set the document ID.
                                fetchedFlashcards.add(flashcard);
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting Firestore document to Flashcard object. Document ID: " + document.getId(), e);
                            }
                        }
                        Log.i(TAG, "loadFlashcards: Firestore query successful. Fetched " + fetchedFlashcards.size() + " flashcards for Topic ID: " + currentTopicId);
                    } else {
                        Log.e(TAG, "Error loading flashcards from Firestore for Topic ID " + currentTopicId + ": ", task.getException());
                        Toast.makeText(FlashcardListActivity.this, "Failed to load flashcards.", Toast.LENGTH_SHORT).show();
                    }
                    // Update the UI with the fetched (or empty) list of flashcards.
                    updateFlashcardListUI(fetchedFlashcards);
                });
    }

    /**
     * Updates the RecyclerView and a header message based on the loaded flashcards.
     * If the list is empty, a "no flashcards" message is shown.
     * @param newFlashcards The new list of Flashcard objects to display.
     */
    private void updateFlashcardListUI(List<Flashcard> newFlashcards) {
        flashcardList.clear(); // Clear the existing list.
        if (newFlashcards != null) {
            flashcardList.addAll(newFlashcards); // Add all newly fetched flashcards.
        }

        if (flashcardsAdapter != null) {
            // Notify the adapter that the data set has changed.
            // For better performance with large lists, I would consider using DiffUtil here.
            flashcardsAdapter.setFlashcards(flashcardList); // My adapter has a method to update its internal list and notify.
        } else {
            Log.e(TAG, "updateFlashcardListUI: flashcardsAdapter is null. Cannot update RecyclerView.");
        }

        // Show or hide the "no flashcards" message based on list content.
        if (flashcardList.isEmpty()) {
            binding.textViewFlashcardsHeader.setText(getString(R.string.no_flashcards_in_topic, currentTopicTitle));
            binding.textViewFlashcardsHeader.setVisibility(View.VISIBLE);
        } else {
            binding.textViewFlashcardsHeader.setVisibility(View.GONE);
        }
        Log.d(TAG, "Flashcard list UI updated. Displaying " + flashcardList.size() + " flashcards.");
    }

    // --- Implementation of FlashcardsAdapter.OnFlashcardClickListener ---
    // These methods are called by the FlashcardsAdapter when a user interacts with a flashcard item.

    /**
     * Handles a click on the main view of a flashcard item in the RecyclerView.
     * My design decision here is to treat a general click as an intention to edit.
     * @param flashcard The Flashcard object that was clicked.
     * @param position The position of the clicked item in the list.
     */
    @Override
    public void onFlashcardClick(Flashcard flashcard, int position) {
        Log.d(TAG, "onFlashcardClick (item view): Clicked on flashcard '" + flashcard.getQuestion() + "'. Launching edit screen.");
        launchEditFlashcardActivity(flashcard);
    }

    /**
     * Handles a click on the "Edit" option from a flashcard's context menu (if implemented in adapter).
     * @param flashcard The Flashcard object to be edited.
     */
    @Override
    public void onEditFlashcard(Flashcard flashcard) {
        Log.d(TAG, "onEditFlashcard (from adapter's options menu): Editing flashcard '" + flashcard.getQuestion() + "'.");
        launchEditFlashcardActivity(flashcard);
    }

    /**
     * Prepares and launches the {@link AddEditFlashcardActivity} for editing an existing flashcard.
     * It passes all necessary data of the flashcard to be edited.
     * @param flashcard The Flashcard object to be edited.
     */
    private void launchEditFlashcardActivity(Flashcard flashcard) {
        // Validate flashcard ID before attempting to launch edit activity.
        if (flashcard.getDocumentId() == null || flashcard.getDocumentId().isEmpty()) {
            Toast.makeText(this, "Error: Cannot edit flashcard. ID is missing.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "launchEditFlashcardActivity: Attempted to edit a flashcard with a null or empty document ID.");
            return;
        }
        Intent intentEdit = new Intent(this, AddEditFlashcardActivity.class);
        // Populate intent with flashcard data for editing.
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_EDIT_FLASHCARD_ID, flashcard.getDocumentId());
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_CURRENT_QUESTION, flashcard.getQuestion());
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_CURRENT_ANSWER, flashcard.getAnswer());
        // Pass topic and subject context as well.
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_TOPIC_ID_FOR_FLASHCARD, flashcard.getTopicId());
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_SUBJECT_ID_FOR_FLASHCARD, flashcard.getSubjectId());
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_TOPIC_TITLE_FOR_FLASHCARD, currentTopicTitle); // For AI context
        intentEdit.putExtra(AddEditFlashcardActivity.EXTRA_SUBJECT_TITLE_FOR_FLASHCARD_CONTEXT, parentSubjectTitle); // For AI context

        addOrEditFlashcardLauncher.launch(intentEdit); // Launch for result.
    }

    /**
     * Handles a click on the "Delete" option from a flashcard's context menu.
     * It shows a confirmation dialog before proceeding with the actual deletion.
     * @param flashcard The Flashcard object to be deleted.
     */
    @Override
    public void onDeleteFlashcard(Flashcard flashcard) {
        Log.d(TAG, "onDeleteFlashcard (from adapter's options menu): Request to delete flashcard '" + flashcard.getQuestion() + "'.");
        // My design includes a confirmation dialog to prevent accidental deletions.
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_flashcard_title))
                .setMessage(getString(R.string.confirm_delete_flashcard_message)) // Generic delete message.
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    // User confirmed deletion.
                    performDeleteFlashcard(flashcard);
                })
                .setNegativeButton(getString(R.string.cancel), null) // User cancelled.
                .setIcon(android.R.drawable.ic_dialog_alert) // Standard alert icon.
                .show();
    }

    /**
     * Performs the actual deletion of a flashcard from Firebase Firestore.
     * It also updates the `flashcardCount` on the parent Topic document.
     * This is a critical data persistence operation and involves updating denormalized data.
     * @param flashcard The Flashcard object to be deleted.
     */
    private void performDeleteFlashcard(Flashcard flashcard) {
        // Validate flashcard ID and user authentication.
        if (flashcard.getDocumentId() == null || flashcard.getDocumentId().isEmpty()) {
            Toast.makeText(this, "Error: Cannot delete flashcard. ID is missing.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "performDeleteFlashcard: Attempted to delete a flashcard with a null or empty document ID.");
            return;
        }
        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Cannot delete flashcard.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String flashcardIdToDelete = flashcard.getDocumentId();
        final String topicIdOfDeletedFlashcard = flashcard.getTopicId(); // Needed to update the parent topic's count.

        Log.i(TAG, "Attempting to delete flashcard from Firestore. Flashcard ID: " + flashcardIdToDelete + ", from Topic ID: " + topicIdOfDeletedFlashcard);
        // I would consider adding a showLoading(true) here if the operation is expected to take time.

        // Delete the flashcard document from Firestore.
        mDb.collection(FLASHCARDS_COLLECTION).document(flashcardIdToDelete)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "Flashcard successfully deleted from Firestore. ID: " + flashcardIdToDelete);

                    // --- DECREMENT FLASHCARD COUNT ON PARENT TOPIC ---
                    // This is an important part of my data integrity strategy.
                    // By denormalizing the flashcard count on the Topic object,
                    // I can display it efficiently without needing to query all flashcards.
                    if (topicIdOfDeletedFlashcard != null && !topicIdOfDeletedFlashcard.isEmpty()) {
                        mDb.collection(TopicListActivity.TOPICS_COLLECTION).document(topicIdOfDeletedFlashcard)
                                .update("flashcardCount", FieldValue.increment(-1)) // Atomic decrement.
                                .addOnSuccessListener(aVoid_ -> Log.i(TAG, "Successfully decremented flashcardCount for Topic ID: " + topicIdOfDeletedFlashcard))
                                .addOnFailureListener(e_ -> Log.e(TAG, "Failed to decrement flashcardCount for Topic ID: " + topicIdOfDeletedFlashcard + ". Count may be inconsistent.", e_));
                    } else {
                        Log.w(TAG, "Could not decrement flashcardCount as topicIdOfDeletedFlashcard was missing for deleted flashcard: " + flashcardIdToDelete);
                    }
                    // --- END OF COUNT DECREMENT ---

                    // Consider showLoading(false) here.
                    Toast.makeText(FlashcardListActivity.this, getString(R.string.flashcard_deleted_successfully), Toast.LENGTH_SHORT).show();
                    mDataWasChanged = true; // Signal that data changed.
                    loadFlashcards();       // Refresh the displayed list.
                })
                .addOnFailureListener(e -> {
                    // Consider showLoading(false) here.
                    Log.e(TAG, "Error deleting flashcard from Firestore. ID: " + flashcardIdToDelete, e);
                    Toast.makeText(FlashcardListActivity.this, getString(R.string.error_deleting_flashcard) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Handles selection of options from the toolbar menu, specifically the "home" (back) button.
     * It sets the activity result based on whether data was changed (mDataWasChanged flag)
     * to inform the calling activity (TopicListActivity).
     * @param item The menu item that was selected.
     * @return True if the item was handled, false otherwise.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // If data was changed (e.g., a flashcard added/deleted), set RESULT_OK.
            // Otherwise, set RESULT_CANCELED. This allows TopicListActivity to know if it needs to refresh.
            if (mDataWasChanged) {
                Log.d(TAG, "onOptionsItemSelected (Home): Data was changed during this activity. Setting result to RESULT_OK.");
                setResult(Activity.RESULT_OK);
            } else {
                Log.d(TAG, "onOptionsItemSelected (Home): No data changes detected. Setting result to RESULT_CANCELED.");
                setResult(Activity.RESULT_CANCELED);
            }
            finish(); // Close this activity.
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Overridden finish() method to ensure RESULT_OK is set if mDataWasChanged is true,
     * even if finish() is called by other means (e.g., system back press) before onOptionsItemSelected.
     * This is a safeguard for my result-passing mechanism.
     */
    @Override
    public void finish() {
        // This check ensures that if TopicListActivity is expecting a result,
        // and data did change, we explicitly set RESULT_OK.
        if (getCallingActivity() != null && mDataWasChanged && !isFinishing()) {
            Log.d(TAG, "finish(): Explicitly setting RESULT_OK because mDataWasChanged is true and there's a calling activity.");
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }
}