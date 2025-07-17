package com.example.studyspot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler; // For posting UI updates from background threads.
import android.os.Looper;  // For getting the main looper for the Handler.
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Useful for parameters that can be null.
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.studyspot.databinding.ActivityTopicListBinding;
// Removed GMS Task imports as direct Task handling isn't prominent here after refactoring.
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// AggregateQuery related imports were removed as the counting strategy changed to denormalization.
import com.google.firebase.firestore.FieldValue; // Kept, useful if direct field updates were planned (though not heavily used in current read-focused logic).
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot; // Explicitly used in callbacks.
import com.google.firebase.firestore.WriteBatch; // For atomic deletion of topics and their flashcards.

import java.util.ArrayList;
import java.util.List;
// import java.util.Locale; // Retained as commented if future string formatting needs it.

import java.util.concurrent.ExecutorService; // For background Firebase operations.
import java.util.concurrent.Executors;   // For creating ExecutorService.

/**
 * TopicListActivity displays a list of topics for a selected subject.
 * My design allows users to switch between their subjects using a dropdown menu,
 * add new topics to the current subject, and perform actions on existing topics
 * such as viewing flashcards, editing the topic, or deleting it (which also deletes associated flashcards).
 * It also provides entry points to start test sessions for the current subject.
 * This activity uses Firebase Firestore for data persistence and communicates changes
 * back to MainActivity via ActivityResults.
 * It implements {@link TopicsAdapter.OnTopicActionClickListener} to handle actions from the topics list.
 */
public class TopicListActivity extends AppCompatActivity implements TopicsAdapter.OnTopicActionClickListener {

    // Log tag for identifying messages from this activity.
    private static final String TAG = "TopicListActivity";

    // Intent extras keys for receiving subject context from MainActivity.
    // These are my defined constants for robust data transfer.
    public static final String EXTRA_SUBJECT_ID = "com.example.studyspot.EXTRA_SUBJECT_ID";
    public static final String EXTRA_SUBJECT_TITLE = "com.example.studyspot.EXTRA_SUBJECT_TITLE";

    // Result codes to communicate specific outcomes back to the calling activity (MainActivity).
    // This allows MainActivity to refresh its data appropriately.
    public static final int RESULT_SUBJECT_DETAILS_EDITED = 101; // Indicates the current subject's details were changed.
    public static final int RESULT_SUBJECT_DELETED = 102;      // Indicates the current subject was deleted.

    // Firestore collection names, defined as constants for consistency.
    public static final String TOPICS_COLLECTION = "topics";
    public static final String SUBJECTS_COLLECTION = "subjects";

    // ViewBinding instance for type-safe UI element access.
    private ActivityTopicListBinding binding;
    // Firebase services instances.
    private FirebaseFirestore mDb;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Adapters and lists for RecyclerViews and Spinners.
    private TopicsAdapter topicsAdapter;              // Adapter for the topics RecyclerView.
    private List<Topic> topicsList;                   // Data source for topicsAdapter.
    private List<Subject> userSubjectsListForSpinner; // Holds Subject objects for the dropdown.
    private ArrayAdapter<String> subjectSpinnerAdapter; // Adapter for the subject selection dropdown.
    private List<String> subjectTitlesForSpinner;     // Titles displayed in the subject dropdown.

    // State variables to keep track of the currently selected subject.
    private String currentSubjectDocumentId;
    private String currentSubjectTitle;

    // ActivityResultLaunchers for handling results from child activities.
    // My design uses these for a modern approach to getting results and refreshing data.
    private ActivityResultLauncher<Intent> addOrEditTopicLauncher;     // For AddEditTopicActivity (topics).
    private ActivityResultLauncher<Intent> editSubjectDetailsLauncher; // For AddEditSubjectActivity (editing current subject's details).
    private ActivityResultLauncher<Intent> viewFlashcardsLauncher;     // For FlashcardListActivity.

    // Flags to manage the result set when this activity finishes, informing MainActivity of changes.
    private boolean mCurrentSubjectDetailsWereEdited = false; // True if current subject's title was changed.
    private boolean mCurrentSubjectWasDeleted = false;    // True if current subject was deleted.
    private boolean mExplicitResultIsSet = false;         // True if a specific result (deleted/edited) has been set.
    private boolean mDataWasChanged = false;              // General flag if any data (topics/flashcards) changed.

    // Threading utilities for background Firebase operations.
    // I use these to ensure Firestore queries don't block the main UI thread.
    private ExecutorService executorService;
    private Handler mainThreadHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate layout using ViewBinding.
        binding = ActivityTopicListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.d(TAG, "onCreate: TopicListActivity is starting.");

        // Initialize ExecutorService for background tasks and Handler for UI updates.
        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // Initialize Firebase services.
        mDb = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Critical: User must be authenticated to access this screen.
        if (currentUser == null) {
            Log.e(TAG, "onCreate: User not authenticated. Finishing TopicListActivity.");
            Toast.makeText(this, "Authentication error. Please log in to view topics.", Toast.LENGTH_LONG).show();
            finish(); // Close activity.
            return;   // Stop further execution.
        }
        Log.i(TAG, "onCreate: Current User Authenticated: " + currentUser.getUid());

        // Initialize lists and adapters.
        topicsList = new ArrayList<>();
        userSubjectsListForSpinner = new ArrayList<>();
        subjectTitlesForSpinner = new ArrayList<>();
        // ArrayAdapter for the subject selection dropdown.
        subjectSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, subjectTitlesForSpinner);

        // Setup UI components.
        setupToolbar();
        setupRecyclerView();
        setupSubjectDropdown(); // Configures the subject selection mechanism.
        setupClickListeners();  // For buttons like "Add New Topic" and test buttons.

        // Retrieve initial subject context (ID and Title) passed from MainActivity.
        Intent intent = getIntent();
        if (intent != null) {
            currentSubjectDocumentId = intent.getStringExtra(EXTRA_SUBJECT_ID);
            currentSubjectTitle = intent.getStringExtra(EXTRA_SUBJECT_TITLE);
            Log.i(TAG, "onCreate: Received initial Subject context - ID: '" + currentSubjectDocumentId + "', Title: '" + currentSubjectTitle + "'");
            // Set toolbar title with the initial subject's title.
            if (currentSubjectTitle != null && getSupportActionBar() != null) {
                getSupportActionBar().setTitle(currentSubjectTitle);
            }
        } else {
            Log.w(TAG, "onCreate: No initial subject data was passed in the intent. The subject dropdown will default to the first available subject or show 'No subjects'.");
        }

        // Load all subjects belonging to the current user for the dropdown.
        // This method will also trigger loading topics for the initially selected/determined subject.
        loadAllUserSubjectsForDropdown();

        // Register ActivityResultLauncher for AddEditTopicActivity.
        // This handles results when a topic is added, edited, or deleted.
        addOrEditTopicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "addOrEditTopicLauncher: Received result. Result code: " + result.getResultCode());
                    // If a topic was successfully created, edited (RESULT_OK), or deleted (custom result code).
                    if (result.getResultCode() == Activity.RESULT_OK || result.getResultCode() == AddEditTopicActivity.RESULT_TOPIC_DELETED) {
                        Log.i(TAG, "addOrEditTopicLauncher: AddEditTopicActivity indicated data change. Refreshing topics list.");
                        mDataWasChanged = true; // Signal that data affecting subject stats (like topic counts) might have changed.
                        loadTopicsForCurrentSubject(); // Reload topics for the current subject, which now include updated flashcard counts.
                    }
                });

        // Register ActivityResultLauncher for AddEditSubjectActivity (when editing current subject's details).
        editSubjectDetailsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "editSubjectDetailsLauncher: Received result. Result code: " + result.getResultCode());
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        // If subject details (e.g., title) were updated successfully.
                        String updatedTitle = result.getData().getStringExtra(AddEditSubjectActivity.EXTRA_UPDATED_SUBJECT_TITLE);
                        if (updatedTitle != null && !updatedTitle.equals(currentSubjectTitle)) {
                            Log.i(TAG, "editSubjectDetailsLauncher: Subject title updated from '" + currentSubjectTitle + "' to '" + updatedTitle + "'.");
                            currentSubjectTitle = updatedTitle; // Update local title.
                            if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentSubjectTitle); // Update toolbar.
                            mCurrentSubjectDetailsWereEdited = true; // Set flag for finish().
                            // Refresh the subject dropdown to reflect the new title. This also re-selects the current subject.
                            loadAllUserSubjectsForDropdown();
                        }
                    } else if (result.getResultCode() == AddEditSubjectActivity.RESULT_OK_SUBJECT_DELETED_FROM_EDIT_ACTIVITY ||
                            result.getResultCode() == TopicListActivity.RESULT_SUBJECT_DELETED) { // Handles if subject was deleted from edit screen.
                        Log.i(TAG, "editSubjectDetailsLauncher: Current subject was deleted. Finishing TopicListActivity.");
                        Toast.makeText(this, "The current subject was deleted.", Toast.LENGTH_LONG).show();
                        mCurrentSubjectWasDeleted = true; // Set flag for finish().
                        finish(); // Close this activity as its context (subject) is gone.
                    }
                });

        // Register ActivityResultLauncher for FlashcardListActivity.
        // This handles results if flashcards were added/deleted, which might affect topic flashcard counts.
        viewFlashcardsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "viewFlashcardsLauncher: Received result from FlashcardListActivity. Result code: " + result.getResultCode());
                    // RESULT_OK from FlashcardListActivity implies a change in flashcards (add/edit/delete).
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.i(TAG, "viewFlashcardsLauncher: FlashcardListActivity indicated data change. Refreshing topics to update flashcard counts.");
                        mDataWasChanged = true; // Signal to MainActivity that underlying data changed.
                        loadTopicsForCurrentSubject(); // Reload topics to get fresh denormalized flashcard counts.
                    }
                });
        Log.d(TAG, "onCreate: ActivityResultLaunchers registered successfully.");
    }

    /**
     * Configures the toolbar for this activity.
     * It enables the "up" navigation and sets the title dynamically based on the current subject.
     */
    private void setupToolbar() {
        setSupportActionBar(binding.toolbarTopics);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Show back arrow.
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Set initial toolbar title. It will be updated if the subject changes via dropdown.
            getSupportActionBar().setTitle(currentSubjectTitle != null ? currentSubjectTitle : getString(R.string.title_activity_edit_subject)); // Default title.
            Log.d(TAG, "Toolbar setup. Initial title: '" + getSupportActionBar().getTitle() + "'");
        }
    }

    /**
     * Initializes the RecyclerView for displaying topics, including setting its LayoutManager and Adapter.
     */
    private void setupRecyclerView() {
        binding.recyclerViewTopics.setLayoutManager(new LinearLayoutManager(this));
        // Initialize adapter with an empty list and set this activity as the action click listener.
        topicsAdapter = new TopicsAdapter(this, topicsList, this);
        binding.recyclerViewTopics.setAdapter(topicsAdapter);
        Log.d(TAG, "Topics RecyclerView and Adapter initialized.");
    }

    /**
     * Sets up the AutoCompleteTextView used as a dropdown/spinner for subject selection.
     * It populates the dropdown with the user's subjects and handles item selection
     * to load topics for the newly selected subject. This is a key part of my navigation design.
     */
    private void setupSubjectDropdown() {
        binding.autoCompleteTextViewSubject.setAdapter(subjectSpinnerAdapter); // Set adapter for the dropdown.
        // Listener for when a subject is selected from the dropdown.
        binding.autoCompleteTextViewSubject.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < userSubjectsListForSpinner.size()) {
                Subject selectedSubject = userSubjectsListForSpinner.get(position);
                Log.d(TAG, "Subject selected from dropdown: '" + selectedSubject.getTitle() + "' (ID: " + selectedSubject.getDocumentId() + ")");
                // Check if the selected subject is different from the currently displayed one.
                if (selectedSubject.getDocumentId() != null && !selectedSubject.getDocumentId().equals(currentSubjectDocumentId)) {
                    // Update current subject context.
                    currentSubjectDocumentId = selectedSubject.getDocumentId();
                    currentSubjectTitle = selectedSubject.getTitle();
                    // Update toolbar title to reflect the new subject.
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentSubjectTitle);
                    Log.i(TAG, "Switched to new Subject via dropdown: '" + currentSubjectTitle + "' (ID: " + currentSubjectDocumentId + "). Reloading topics.");
                    loadTopicsForCurrentSubject(); // Load topics for this newly selected subject.
                    // Reset flags as we are now in a new subject context.
                    mCurrentSubjectDetailsWereEdited = false;
                    mCurrentSubjectWasDeleted = false;
                    mDataWasChanged = false; // Reset general data change flag for the new context
                }
            }
        });
        Log.d(TAG, "Subject selection dropdown configured.");
    }

    /**
     * Sets up click listeners for main action buttons on this screen,
     * such as "Add New Topic" and buttons for starting different test modes.
     */
    private void setupClickListeners() {
        // "Add New Topic" button listener.
        binding.buttonAddNewTopic.setOnClickListener(v -> {
            Log.d(TAG, "\"Add New Topic\" button clicked. Current Subject ID for new topic: '" + currentSubjectDocumentId + "'");
            // Ensure a subject is actually selected before allowing topic creation.
            if (currentSubjectDocumentId == null || currentSubjectDocumentId.isEmpty()) {
                Log.w(TAG, "Cannot add new topic: currentSubjectDocumentId is null or empty. User needs to select a subject.");
                Toast.makeText(this, "Please select or create a subject first using the dropdown or main screen.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.i(TAG, "Proceeding to launch AddEditTopicActivity for new topic under Subject ID: " + currentSubjectDocumentId);
            Intent intent = new Intent(TopicListActivity.this, AddEditTopicActivity.class);
            // Pass the current subject's ID and title so the new topic can be associated correctly.
            intent.putExtra(AddEditTopicActivity.EXTRA_SUBJECT_ID_FOR_TOPIC, currentSubjectDocumentId);
            intent.putExtra(EXTRA_SUBJECT_TITLE, currentSubjectTitle); // Pass current subject title for context
            addOrEditTopicLauncher.launch(intent); // Launch for result.
        });

        // Listener for "Test Topic by Topic" button.
        binding.buttonTestTopicByTopic.setOnClickListener(v -> startTest(TestActivity.MODE_TOPIC_BY_TOPIC_SEQUENTIAL));
        // Listener for "Test Random Flashcards" (for the whole subject) button.
        binding.buttonTestRandomFlashcards.setOnClickListener(v -> startTest(TestActivity.MODE_RANDOM_ALL_SUBJECT));
        Log.d(TAG, "Click listeners for 'Add Topic' and test buttons set up.");
    }

    /**
     * Initiates a test session by first checking if the current subject has any flashcards.
     * This pre-flight check in my design prevents starting a test on an empty subject.
     * @param testMode The mode for the test (e.g., TestActivity.MODE_TOPIC_BY_TOPIC_SEQUENTIAL).
     */
    private void startTest(String testMode) {
        Log.d(TAG, "startTest: Initiating test with Mode: '" + testMode + "', for Subject ID: '" + currentSubjectDocumentId + "'");
        // Validate that a subject is selected and user is logged in.
        if (currentSubjectDocumentId == null || currentSubjectDocumentId.isEmpty() || currentUser == null) {
            Toast.makeText(this, "Please select a subject and ensure you are logged in to start a test.", Toast.LENGTH_LONG).show();
            return;
        }
        // Inform user while checking for flashcards. This involves a quick Firestore query.
        Toast.makeText(TopicListActivity.this, "Checking for available flashcards...", Toast.LENGTH_SHORT).show();
        // Query Firestore to see if there's at least one flashcard for the current subject.
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                .whereEqualTo("subjectId", currentSubjectDocumentId)
                .limit(1).get() // Only need to know if at least one exists.
                .addOnCompleteListener(task -> handleFlashcardCheckCompletion(
                        task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty(),
                        task.getException(),
                        testMode
                ));
    }

    /**
     * Handles the completion of the flashcard check before starting a test.
     * If flashcards exist (and topics for sequential mode), it launches TestActivity.
     * Otherwise, it informs the user that the test cannot start.
     * @param hasFlashcards True if at least one flashcard exists for the subject.
     * @param e Exception if the check failed, null otherwise.
     * @param testMode The test mode to start if conditions are met.
     */
    private void handleFlashcardCheckCompletion(boolean hasFlashcards, @Nullable Exception e, String testMode) {
        if (e != null) {
            // Error during the pre-flight check.
            Log.e(TAG, "handleFlashcardCheckCompletion: Error occurred while checking for flashcards for Subject ID: " + currentSubjectDocumentId, e);
            Toast.makeText(TopicListActivity.this, "Could not verify flashcards for test. Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (!hasFlashcards) {
            // No flashcards found for the current subject.
            Log.w(TAG, "handleFlashcardCheckCompletion: No flashcards found in subject '" + currentSubjectTitle + "'. Test cannot be started.");
            Toast.makeText(TopicListActivity.this, "This subject currently has no flashcards to test. Please add some first.", Toast.LENGTH_LONG).show();
            return;
        }
        // Specific check for sequential mode: requires topics to exist.
        if (topicsList.isEmpty() && TestActivity.MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode)) {
            Log.w(TAG, "handleFlashcardCheckCompletion: No topics found in subject '" + currentSubjectTitle + "'. Cannot start MODE_TOPIC_BY_TOPIC_SEQUENTIAL test.");
            Toast.makeText(this, "This subject has no topics. 'Topic by Topic' test cannot be started.", Toast.LENGTH_LONG).show();
            return;
        }

        // Conditions met, proceed to launch TestActivity.
        Log.d(TAG, "handleFlashcardCheckCompletion: Pre-flight checks passed. Launching TestActivity with Mode: '" + testMode + "' for Subject: '" + currentSubjectTitle + "'");
        Intent testIntent = new Intent(TopicListActivity.this, TestActivity.class);
        testIntent.putExtra(TestActivity.EXTRA_SUBJECT_ID, currentSubjectDocumentId);
        testIntent.putExtra(TestActivity.EXTRA_SUBJECT_TITLE, currentSubjectTitle);
        testIntent.putExtra(TestActivity.EXTRA_TEST_MODE, testMode);
        // For MODE_SPECIFIC_TOPIC, EXTRA_TOPIC_ID and EXTRA_TOPIC_TITLE would be added here if launching from a topic click.
        // This startTest method is for subject-level tests.
        startActivity(testIntent);
    }

    /**
     * Loads all subjects for the current user from Firestore to populate the subject selection dropdown.
     * This method performs the Firestore query and then calls {@link #processSubjectsForDropdown(List)}
     * to update the UI. My design fetches subjects on the main thread here, assuming the number of subjects
     * per user is relatively small. For larger datasets, this would also use the ExecutorService.
     */
    private void loadAllUserSubjectsForDropdown() {
        Log.i(TAG, "loadAllUserSubjectsForDropdown: Fetching all subjects for current user to populate dropdown.");
        if (currentUser == null) {
            Log.e(TAG, "loadAllUserSubjectsForDropdown: currentUser is null. Cannot load subjects.");
            processSubjectsForDropdown(new ArrayList<>()); // Process with empty list to clear UI.
            return;
        }
        String userIdToQuery = currentUser.getUid();
        Log.d(TAG, "loadAllUserSubjectsForDropdown: Querying Firestore for subjects linked to User ID: " + userIdToQuery);

        // Query Firestore for subjects belonging to the current user, ordered by title.
        mDb.collection(SUBJECTS_COLLECTION)
                .whereEqualTo("userId", userIdToQuery)
                .orderBy("title", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> { // This listener runs on the main thread by default for get().
                    List<Subject> subjectsFromFirestore = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            try {
                                Subject subject = doc.toObject(Subject.class);
                                subject.setDocumentId(doc.getId());
                                subjectsFromFirestore.add(subject);
                            } catch (Exception ex) {
                                Log.e(TAG, "Error converting Firestore document to Subject object during dropdown load: " + doc.getId(), ex);
                            }
                        }
                        Log.d(TAG, "loadAllUserSubjectsForDropdown: Firestore query successful. Found " + subjectsFromFirestore.size() + " subjects.");
                    } else {
                        Log.e(TAG, "Error loading subjects from Firestore for dropdown: ", task.getException());
                        Toast.makeText(this, "Failed to load subject list.", Toast.LENGTH_SHORT).show();
                    }
                    // Process the fetched subjects to update the dropdown and trigger topic loading for the selected subject.
                    processSubjectsForDropdown(subjectsFromFirestore);
                });
    }

    /**
     * Processes the list of subjects fetched for the dropdown. It updates the adapter,
     * determines which subject should be pre-selected (based on intent or first in list),
     * and then triggers loading of topics for that selected subject.
     * @param subjects The list of {@link Subject} objects fetched from Firestore.
     */
    private void processSubjectsForDropdown(List<Subject> subjects) {
        Log.d(TAG, "processSubjectsForDropdown: Processing " + (subjects != null ? subjects.size() : "null (no)") + " subjects for the dropdown.");
        // Clear previous dropdown data.
        userSubjectsListForSpinner.clear();
        subjectTitlesForSpinner.clear();

        if (subjects != null && !subjects.isEmpty()) {
            userSubjectsListForSpinner.addAll(subjects);
            for (Subject subject : subjects) {
                subjectTitlesForSpinner.add(subject.getTitle() != null ? subject.getTitle() : "Unnamed Subject"); // Add titles for display.
            }
        }
        Log.i(TAG, "processSubjectsForDropdown: Subject titles for spinner populated. Count: " + subjectTitlesForSpinner.size());

        if (subjectSpinnerAdapter != null) {
            subjectSpinnerAdapter.notifyDataSetChanged(); // Refresh dropdown display.
        } else {
            Log.e(TAG, "processSubjectsForDropdown: subjectSpinnerAdapter is unexpectedly NULL!");
        }

        int preSelectionIndex = -1; // Index of the subject to pre-select.
        boolean needsTopicLoadForSelectedSubject = false; // Flag to trigger topic loading.

        // Attempt to find the initially passed subject (currentSubjectDocumentId) in the loaded list.
        if (currentSubjectDocumentId != null && !userSubjectsListForSpinner.isEmpty()) {
            for (int i = 0; i < userSubjectsListForSpinner.size(); i++) {
                if (userSubjectsListForSpinner.get(i).getDocumentId().equals(currentSubjectDocumentId)) {
                    preSelectionIndex = i;
                    break;
                }
            }
        }

        if (preSelectionIndex != -1) {
            // If the initial/current subject is found, pre-select it.
            Subject selected = userSubjectsListForSpinner.get(preSelectionIndex);
            binding.autoCompleteTextViewSubject.setText(selected.getTitle(), false); // Set text without filtering.
            currentSubjectTitle = selected.getTitle(); // Ensure local title matches.
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentSubjectTitle); // Update toolbar.
            Log.i(TAG, "processSubjectsForDropdown: Pre-selected subject in dropdown: '" + currentSubjectTitle + "' (ID: " + currentSubjectDocumentId + ")");
            needsTopicLoadForSelectedSubject = true;
        } else if (!userSubjectsListForSpinner.isEmpty()) {
            // If no specific subject was pre-selected (or initial one not found), default to the first subject in the list.
            Subject firstSubject = userSubjectsListForSpinner.get(0);
            currentSubjectDocumentId = firstSubject.getDocumentId();
            currentSubjectTitle = firstSubject.getTitle();
            binding.autoCompleteTextViewSubject.setText(currentSubjectTitle, false);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentSubjectTitle);
            Log.i(TAG, "processSubjectsForDropdown: Defaulted to first subject in spinner: '" + currentSubjectTitle + "' (ID: " + currentSubjectDocumentId + ")");
            needsTopicLoadForSelectedSubject = true;
        } else {
            // No subjects available for the user.
            Log.w(TAG, "processSubjectsForDropdown: No subjects loaded into spinner. Clearing topics list and updating UI.");
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Topics"); // Reset title.
            binding.autoCompleteTextViewSubject.setText("", false); // Clear dropdown text.
            currentSubjectDocumentId = null; currentSubjectTitle = null; // Clear current subject context.
            updateAdapterWithTopics(new ArrayList<>()); // Clear the topics RecyclerView.
            Toast.makeText(this, "No subjects found. You can add subjects from the main screen.", Toast.LENGTH_LONG).show();
        }

        // If a subject is selected (either pre-selected or defaulted), load its topics.
        if (needsTopicLoadForSelectedSubject && currentSubjectDocumentId != null) {
            loadTopicsForCurrentSubject();
        }
    }

    /**
     * Loads topics for the {@link #currentSubjectDocumentId} from Firestore.
     * This operation is performed on a background thread using {@link #executorService},
     * and UI updates are posted to the main thread. My design now relies on the denormalized
     * `flashcardCount` field within each Topic object from Firestore.
     */
    private void loadTopicsForCurrentSubject() {
        Log.i(TAG, "loadTopicsForCurrentSubject: Attempting to load topics for Subject ID: '" + currentSubjectDocumentId + "'.");
        // Validate that a subject is selected and user is authenticated.
        if (currentSubjectDocumentId == null || currentSubjectDocumentId.isEmpty() || currentUser == null) {
            Log.w(TAG, "loadTopicsForCurrentSubject: Aborted. currentSubjectId ('" + currentSubjectDocumentId + "') is null/empty or currentUser is null.");
            // Post UI update to clear topics list and show appropriate message.
            mainThreadHandler.post(() -> {
                updateAdapterWithTopics(new ArrayList<>());
                if (binding.textViewNoTopics != null) {
                    binding.textViewNoTopics.setVisibility(View.VISIBLE);
                    binding.textViewNoTopics.setText(currentSubjectDocumentId == null ?
                            getString(R.string.no_topics_select_subject) : // If no subject is selected at all.
                            getString(R.string.no_topics_for_this_subject)); // If subject selected but has no topics.
                }
            });
            return;
        }

        // Show loading state (e.g., hide "no topics" message, could show a ProgressBar).
        mainThreadHandler.post(() -> {
            if (binding.textViewNoTopics != null) binding.textViewNoTopics.setVisibility(View.GONE);
            // If I had a dedicated progress bar for topics:
            // if (binding.progressBarTopics != null) binding.progressBarTopics.setVisibility(View.VISIBLE);
        });

        // Query Firestore for topics associated with the current subject, ordered by timestamp.
        mDb.collection(TOPICS_COLLECTION)
                .whereEqualTo("subjectId", currentSubjectDocumentId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(executorService, topicTask -> { // Execute completion listener on background thread.
                    final List<Topic> fetchedTopics = new ArrayList<>();
                    String GmsTaskErrorMessage = null; // For storing potential error messages.

                    if (topicTask.isSuccessful() && topicTask.getResult() != null) {
                        for (QueryDocumentSnapshot doc : topicTask.getResult()) {
                            try {
                                Topic topic = doc.toObject(Topic.class);
                                topic.setDocumentId(doc.getId());
                                // The 'flashcardCount' is now directly part of the Topic object,
                                // assuming it's correctly denormalized and stored in Firestore.
                                fetchedTopics.add(topic);
                            } catch (Exception e) {
                                Log.e(TAG, "loadTopics (Background Thread): Error converting Firestore document to Topic object: " + doc.getId(), e);
                            }
                        }
                        Log.i(TAG, "loadTopics (Background Thread): Firestore query successful. Found " + fetchedTopics.size() + " topics for Subject ID: " + currentSubjectDocumentId);
                    } else {
                        Log.e(TAG, "loadTopics (Background Thread): Error loading topics from Firestore for Subject ID " + currentSubjectDocumentId + ": ", topicTask.getException());
                        GmsTaskErrorMessage = "Error loading topics. Please try again.";
                    }

                    final String finalGmsTaskErrorMessage = GmsTaskErrorMessage; // Final for lambda.
                    // Post UI update back to the Main Thread.
                    mainThreadHandler.post(() -> {
                        // Hide progress bar if shown:
                        // if (binding.progressBarTopics != null) binding.progressBarTopics.setVisibility(View.GONE);
                        if (finalGmsTaskErrorMessage != null) {
                            Toast.makeText(TopicListActivity.this, finalGmsTaskErrorMessage, Toast.LENGTH_SHORT).show();
                        }
                        // Update the RecyclerView with the fetched topics.
                        updateAdapterWithTopics(fetchedTopics);
                    });
                });
    }

    /**
     * Updates the topics RecyclerView adapter with a new list of topics.
     * It also manages the visibility of a "no topics" message based on whether the list is empty.
     * @param finalTopics The new list of {@link Topic} objects to display.
     */
    private void updateAdapterWithTopics(List<Topic> finalTopics) {
        topicsList.clear(); // Clear existing topics.
        if (finalTopics != null) {
            topicsList.addAll(finalTopics); // Add new topics.
        }
        if (topicsAdapter != null) {
            topicsAdapter.setTopics(topicsList); // Update adapter's data and notify.
        } else {
            Log.e(TAG, "updateAdapterWithTopics: topicsAdapter is null! Cannot update RecyclerView.");
        }
        Log.i(TAG, "Topics adapter updated. Displaying " + topicsList.size() + " topics.");

        // Handle "no topics" message visibility and text.
        if (binding.textViewNoTopics != null) {
            binding.textViewNoTopics.setVisibility(topicsList.isEmpty() ? View.VISIBLE : View.GONE);
            if (topicsList.isEmpty() && currentSubjectDocumentId != null) {
                // If a subject is selected but has no topics.
                binding.textViewNoTopics.setText(getString(R.string.no_topics_in_subject_add_new, currentSubjectTitle != null ? currentSubjectTitle : "this subject"));
            } else if (currentSubjectDocumentId == null) {
                // If no subject is selected at all (e.g., user has no subjects).
                binding.textViewNoTopics.setText(getString(R.string.no_topics_select_subject));
            }
        } else {
            Log.e(TAG, "updateAdapterWithTopics: binding.textViewNoTopics is null! Check layout activity_topic_list.xml.");
        }
    }


    /**
     * Inflates the options menu for the activity's toolbar.
     * My menu includes an option to "Edit Subject Details."
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_topic_list, menu); // My custom menu for this activity.
        return true;
    }

    /**
     * Handles action bar item clicks, specifically the "home" (back) button
     * and the "Edit Subject Details" menu item.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // Home/Up button pressed. The finish() method handles setting the correct result.
            finish();
            return true;
        } else if (itemId == R.id.action_edit_subject_details) {
            // "Edit Subject Details" menu item selected.
            if (currentSubjectDocumentId == null || currentSubjectDocumentId.isEmpty()) {
                // Prevent editing if no subject is currently selected.
                Toast.makeText(this, "No subject selected to edit. Please select a subject from the dropdown.", Toast.LENGTH_LONG).show();
                return true;
            }
            Log.d(TAG, "Edit Subject Details option selected for Subject: '" + currentSubjectTitle + "' (ID: " + currentSubjectDocumentId + ")");
            Intent intent = new Intent(this, AddEditSubjectActivity.class);
            // Pass current subject's ID and title to pre-fill the edit screen.
            intent.putExtra(AddEditSubjectActivity.EXTRA_EDIT_SUBJECT_ID, currentSubjectDocumentId);
            intent.putExtra(AddEditSubjectActivity.EXTRA_CURRENT_SUBJECT_TITLE, currentSubjectTitle);
            editSubjectDetailsLauncher.launch(intent); // Launch for result.
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Implementation of TopicsAdapter.OnTopicActionClickListener ---
    // These methods are callbacks from the TopicsAdapter when user interacts with a topic item.

    /**
     * Called when a topic item is clicked.
     * Navigates to {@link FlashcardListActivity} to display flashcards for the selected topic.
     * @param topic The {@link Topic} object that was clicked.
     */
    @Override
    public void onTopicClick(Topic topic) {
        Log.d(TAG, "onTopicClick: User clicked on Topic '" + topic.getTitle() + "' (ID: " + topic.getDocumentId() + "). Launching FlashcardListActivity.");
        Intent intent = new Intent(this, FlashcardListActivity.class);
        // Pass necessary context: topic ID, topic title, parent subject ID, and parent subject title.
        intent.putExtra(FlashcardListActivity.EXTRA_TOPIC_ID, topic.getDocumentId());
        intent.putExtra(FlashcardListActivity.EXTRA_TOPIC_TITLE, topic.getTitle());
        intent.putExtra(FlashcardListActivity.EXTRA_SUBJECT_ID_FOR_FLASHCARD, topic.getSubjectId()); // Subject ID of the topic.
        intent.putExtra(FlashcardListActivity.EXTRA_PARENT_SUBJECT_TITLE, currentSubjectTitle); // Title of the currently selected subject.
        if (viewFlashcardsLauncher != null) {
            viewFlashcardsLauncher.launch(intent); // Launch for result.
        } else {
            // Fallback if launcher is somehow null (should not happen in normal flow).
            Log.e(TAG, "onTopicClick: viewFlashcardsLauncher is null! Using startActivity as a fallback.");
            startActivity(intent);
        }
    }

    /**
     * Called when the "Edit" action is selected for a topic.
     * Navigates to {@link AddEditTopicActivity} to edit the selected topic.
     * @param topic The {@link Topic} object to be edited.
     */
    @Override
    public void onEditTopic(Topic topic) {
        Log.d(TAG, "onEditTopic: User selected 'Edit' for Topic: '" + topic.getTitle() + "' (ID: " + topic.getDocumentId() + ")");
        Intent intent = new Intent(this, AddEditTopicActivity.class);
        // Pass topic's ID, current title, and original subject ID for editing context.
        intent.putExtra(AddEditTopicActivity.EXTRA_EDIT_TOPIC_ID, topic.getDocumentId());
        intent.putExtra(AddEditTopicActivity.EXTRA_CURRENT_TOPIC_TITLE, topic.getTitle());
        intent.putExtra(AddEditTopicActivity.EXTRA_ORIGINAL_SUBJECT_ID, topic.getSubjectId());
        // EXTRA_SUBJECT_ID_FOR_TOPIC is also passed in case AddEditTopicActivity allows changing parent subject.
        intent.putExtra(AddEditTopicActivity.EXTRA_SUBJECT_ID_FOR_TOPIC, topic.getSubjectId());
        addOrEditTopicLauncher.launch(intent); // Launch for result.
    }

    /**
     * Called when the "Delete" action is selected for a topic.
     * Initiates the confirmation process before actual deletion.
     * @param topic The {@link Topic} object to be deleted.
     */
    @Override
    public void onDeleteTopic(Topic topic) {
        Log.d(TAG, "onDeleteTopic: User selected 'Delete' for Topic: '" + topic.getTitle() + "' (ID: " + topic.getDocumentId() + ")");
        confirmDeleteTopicFromList(topic); // Start confirmation flow.
    }

    /**
     * Starts the confirmation process for deleting a topic.
     * It uses the denormalized `flashcardCount` from the Topic object for the confirmation message.
     * @param topic The topic to be confirmed for deletion.
     */
    private void confirmDeleteTopicFromList(Topic topic) {
        Log.d(TAG, "confirmDeleteTopicFromList: Preparing confirmation dialog for deleting topic: '" + topic.getTitle() + "'");
        if (currentUser == null) { // Should not happen if activity guards are correct.
            Toast.makeText(this, "Authentication error. Cannot delete topic.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Using the flashcardCount directly from the Topic object.
        // This count is denormalized and stored with the Topic in Firestore,
        // so no extra query is needed here to get the count for the dialog message.
        int flashcardCount = topic.getFlashcardCount();
        showDeleteDialogFromList(topic, flashcardCount);
    }

    /**
     * Displays an AlertDialog to confirm topic deletion, showing the topic title
     * and the number of associated flashcards that will also be deleted.
     * @param topic The topic to be deleted.
     * @param flashcardCount The number of flashcards associated with this topic.
     */
    private void showDeleteDialogFromList(Topic topic, int flashcardCount) {
        String message = getString(R.string.confirm_delete_topic_message, topic.getTitle(), flashcardCount);
        Log.d(TAG, "showDeleteDialogFromList: Displaying confirmation for topic '" + topic.getTitle() + "' with " + flashcardCount + " flashcards.");

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_topic_title))
                .setMessage(message) // Informative message about flashcard deletion.
                .setPositiveButton(getString(R.string.delete), (dialog, which) ->
                        // User confirmed, proceed to delete the topic and its flashcards.
                        deleteTopicAndFlashcardsFromList(topic.getDocumentId()))
                .setNegativeButton(getString(R.string.cancel), null) // User cancelled.
                .setIcon(android.R.drawable.ic_dialog_alert) // Standard alert icon.
                .show();
    }

    /**
     * Deletes a topic and all its associated flashcards from Firebase Firestore.
     * My design uses a {@link WriteBatch} to perform these deletions atomically, ensuring
     * data integrity (either both topic and its flashcards are deleted, or neither is).
     * This operation is performed with background processing for fetching flashcard IDs.
     * @param topicIdToDelete The ID of the topic to delete.
     */
    private void deleteTopicAndFlashcardsFromList(String topicIdToDelete) {
        Log.i(TAG, "deleteTopicAndFlashcardsFromList: Starting deletion process for Topic ID: '" + topicIdToDelete + "' and its associated flashcards.");
        if (currentUser == null) { // Should be caught earlier, but as a safeguard.
            Toast.makeText(this, "Authentication error. Deletion failed.", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoadingState(true, "Deleting topic and its flashcards..."); // Indicate processing.

        WriteBatch batch = mDb.batch(); // Initialize a Firestore WriteBatch for atomic operations.

        // Step 1: Query for all flashcards associated with the topicIdToDelete.
        // This part (fetching flashcard IDs) runs on a background thread via executorService.
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                .whereEqualTo("topicId", topicIdToDelete)
                .get()
                .addOnCompleteListener(executorService, flashcardTask -> { // Listener on background thread.
                    if (flashcardTask.isSuccessful() && flashcardTask.getResult() != null) {
                        for (QueryDocumentSnapshot fcDoc : flashcardTask.getResult()) {
                            // Add delete operation for each found flashcard to the batch.
                            batch.delete(fcDoc.getReference());
                        }
                        Log.d(TAG, "deleteTopicAndFlashcardsFromList (Background Thread): Staged deletion for " +
                                flashcardTask.getResult().size() + " flashcards associated with Topic ID: " + topicIdToDelete);
                    } else {
                        // Log error if fetching flashcards failed. My design choice is to still attempt
                        // to delete the topic itself, though this could leave orphaned flashcards.
                        // A more robust solution might halt if this critical step fails.
                        Log.e(TAG, "deleteTopicAndFlashcardsFromList (Background Thread): Error fetching flashcards for batch deletion. Topic ID: " +
                                topicIdToDelete, flashcardTask.getException());
                    }

                    // Step 2: Add delete operation for the topic document itself to the batch.
                    batch.delete(mDb.collection(TOPICS_COLLECTION).document(topicIdToDelete));
                    Log.d(TAG, "deleteTopicAndFlashcardsFromList (Background Thread): Staged deletion for Topic ID: " + topicIdToDelete);

                    // Step 3: Commit the batch operation. The commit listener runs on the main thread by default.
                    batch.commit().addOnCompleteListener(commitTask -> {
                        // Ensure UI updates related to commit result are on the main thread.
                        mainThreadHandler.post(() -> {
                            showLoadingState(false, null); // Hide loading indicator.
                            if (commitTask.isSuccessful()) {
                                Log.i(TAG, "Successfully deleted topic and associated flashcards (batch commit). Topic ID: " + topicIdToDelete);
                                Toast.makeText(this, getString(R.string.topic_deleted_successfully), Toast.LENGTH_SHORT).show();
                                mDataWasChanged = true; // Signal data change.
                                // I also need to decrement the topicCount on the parent subject.
                                if (currentSubjectDocumentId != null && !currentSubjectDocumentId.isEmpty()) {
                                    mDb.collection(SUBJECTS_COLLECTION).document(currentSubjectDocumentId)
                                            .update("topicCount", FieldValue.increment(-1))
                                            .addOnSuccessListener(aVoid -> Log.i(TAG, "Successfully decremented topic count for subject: " + currentSubjectDocumentId))
                                            .addOnFailureListener(e -> Log.e(TAG, "Failed to decrement topic count for subject: " + currentSubjectDocumentId, e));
                                }
                                loadTopicsForCurrentSubject(); // Refresh the topics list in the UI.
                            } else {
                                Log.e(TAG, "Error committing batch delete for topic and flashcards. Topic ID: " + topicIdToDelete, commitTask.getException());
                                Toast.makeText(this, getString(R.string.error_deleting_topic) + ". Please try again.", Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                });
    }

    /**
     * Helper method to show a simple loading state.
     * In my current design, this is a placeholder and uses a Toast. A more robust
     * implementation would use a ProgressBar or a more persistent loading dialog.
     * @param show True to show loading, false to hide.
     * @param message Optional message to display during loading.
     */
    private void showLoadingState(boolean show, @Nullable String message) {
        // This is a basic implementation. For a production app, I'd use a ProgressBar.
        // Example: if (binding.progressBarTopicsList != null) binding.progressBarTopicsList.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && message != null && !message.isEmpty()) {
            // A Toast is not ideal for a persistent loading state but serves as a placeholder here.
            // Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "showLoadingState: " + message); // Logging the loading message.
        }
    }

    /**
     * Overridden finish() method to set the correct ActivityResult for MainActivity.
     * My design uses flags (mCurrentSubjectWasDeleted, mCurrentSubjectDetailsWereEdited, mDataWasChanged)
     * to determine the specific result code, allowing MainActivity to refresh its data accurately.
     * This is crucial for maintaining data consistency across activities.
     */
    @Override
    public void finish() {
        Log.d(TAG, "finish() called. Flags - SubjectDeleted: " + mCurrentSubjectWasDeleted +
                ", SubjectEdited: " + mCurrentSubjectDetailsWereEdited + ", DataChanged: " + mDataWasChanged);
        Intent resultIntent = new Intent(); // Create a new intent for the result.

        if (mCurrentSubjectWasDeleted) {
            // If the subject this activity was focused on was deleted.
            setResult(RESULT_SUBJECT_DELETED, resultIntent);
            mExplicitResultIsSet = true;
            Log.d(TAG, "finish: Setting result to RESULT_SUBJECT_DELETED.");
        } else if (mCurrentSubjectDetailsWereEdited) {
            // If the subject's details (e.g., title) were edited.
            setResult(RESULT_SUBJECT_DETAILS_EDITED, resultIntent);
            mExplicitResultIsSet = true;
            Log.d(TAG, "finish: Setting result to RESULT_SUBJECT_DETAILS_EDITED.");
        } else if (mDataWasChanged) {
            // If general data changed (e.g., topics added/deleted, flashcards changed impacting topic stats).
            setResult(Activity.RESULT_OK, resultIntent);
            mExplicitResultIsSet = true;
            Log.d(TAG, "finish: Setting result to Activity.RESULT_OK due to mDataWasChanged.");
        }

        // If no explicit result was set by the flags above (meaning no significant changes relevant to MainActivity occurred),
        // and there is a calling activity, the default result (usually RESULT_CANCELED) will be sent.
        if (!mExplicitResultIsSet && getCallingActivity() != null) {
            Log.d(TAG, "finish: No explicit result was set by flags. Default ActivityResult will apply (likely CANCELED).");
        }
        super.finish(); // Call super.finish() to actually close the activity.
    }

    /**
     * Called when the activity is no longer visible to the user.
     * My implementation includes shutting down the {@link ExecutorService} to free up resources
     * and prevent potential leaks from background tasks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Gracefully shut down the executor service.
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "TopicListActivity onDestroy: ExecutorService has been shut down.");
        }
    }
}