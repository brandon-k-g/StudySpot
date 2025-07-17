package com.example.studyspot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Retained, useful for callbacks if ever needed.
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat; // For handling swipe gestures.

import com.example.studyspot.databinding.ActivityTestBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections; // For shuffling flashcards in random mode.
import java.util.HashMap;   // For topicIdToTitleMap.
import java.util.List;
import java.util.Locale;     // For formatting score strings.
import java.util.Map;      // For topicIdToTitleMap.

/**
 * TestActivity provides an interactive testing session for users with their flashcards.
 * My design supports multiple test modes: testing a specific topic, testing all flashcards
 * from a subject randomly, and testing flashcards sequentially topic-by-topic within a subject.
 * It features card flip animations, swipe gestures for marking cards correct/wrong,
 * progress tracking, and saves test results to Firebase Firestore.
 * The activity's architecture is built to handle these different modes by dynamically loading
 * and preparing flashcards accordingly.
 */
public class TestActivity extends AppCompatActivity {

    // Log tag for identifying messages from this TestActivity.
    private static final String TAG = "TestActivity";

    // Constants for Intent Extras, used to pass context (like subject/topic IDs and test mode) to this activity.
    // These are my defined keys for robust inter-activity communication.
    public static final String EXTRA_SUBJECT_ID = "SUBJECT_ID";
    public static final String EXTRA_TOPIC_ID = "TOPIC_ID"; // Used when testing a specific topic.
    public static final String EXTRA_TEST_MODE = "TEST_MODE"; // Defines how flashcards are selected and presented.
    public static final String EXTRA_SUBJECT_TITLE = "SUBJECT_TITLE"; // For display in toolbar or results.
    public static final String EXTRA_TOPIC_TITLE = "TOPIC_TITLE";   // For display in toolbar or results.

    // Defines the different test modes supported by this activity.
    // My design uses these constants to control flashcard loading and test flow logic.
    public static final String MODE_SPECIFIC_TOPIC = "SPECIFIC_TOPIC"; // Test flashcards from only one chosen topic.
    public static final String MODE_RANDOM_ALL_SUBJECT = "RANDOM_ALL_SUBJECT_FLASHCARDS"; // Test all flashcards from a subject in random order.
    public static final String MODE_TOPIC_BY_TOPIC_SEQUENTIAL = "TOPIC_BY_TOPIC_SEQUENTIAL"; // Test flashcards grouped by topic, one topic after another.

    // Firestore collection name for storing test results.
    // Using a constant ensures consistency when interacting with Firestore.
    public static final String TEST_RESULTS_COLLECTION = "testResults";


    // ViewBinding instance for type-safe access to UI elements in activity_test.xml.
    private ActivityTestBinding binding;
    // Firebase services instances.
    private FirebaseFirestore mDb;   // Firestore for loading flashcards and saving test results.
    private FirebaseAuth mAuth;      // For user authentication.
    private FirebaseUser currentUser;// The currently authenticated Firebase user.

    // Lists for managing flashcards during the test session.
    private List<Flashcard> allFlashcardsForTestSession; // Holds all flashcards initially loaded for the chosen mode.
    private List<Flashcard> currentTestQueue;          // The actual queue of cards being tested (e.g., after shuffling).
    private int currentCardIndex = -1;                 // Index of the currently displayed card in currentTestQueue.

    // State variables for card animation and interaction.
    private boolean isFrontVisible = true; // Tracks whether the question (front) or answer (back) of the card is visible.
    private float scale;                   // Used for camera distance settings in card flip animations.

    // Context variables received from the intent, defining the scope of the test.
    private String subjectId;        // ID of the parent subject for the test.
    private String topicId;          // ID of the specific topic (if in MODE_SPECIFIC_TOPIC).
    private String testMode;         // The current test mode (e.g., SPECIFIC_TOPIC).
    private String subjectTitleForToolbar; // Title of the subject, for display.
    private String topicTitleForToolbar;   // Title of the topic, for display (if applicable).

    // Variables for tracking overall test session scores.
    private int overallSessionScore = 0;        // Total correct answers in the current session.
    private int overallSessionCardsAttempted = 0;// Total cards attempted (marked correct or wrong).
    private boolean testFinished = false;       // Flag to indicate if the test session has concluded.

    // Gesture detector for handling swipe interactions on the flashcard.
    private GestureDetectorCompat gestureDetector;

    // Variables specifically for MODE_TOPIC_BY_TOPIC_SEQUENTIAL.
    // My design for this mode requires tracking topics and segment scores.
    private List<Topic> orderedTopicsForCurrentTest; // List of topics for the subject, in order they will be tested.
    private Map<String, String> topicIdToTitleMap;   // Maps topic IDs to their titles, crucial for displaying context.
    private String activeTopicIdInSequentialTest = null; // ID of the topic currently being tested in sequential mode.
    private String activeTopicTitleInSequentialTest = null;// Title of the active topic in sequential mode.
    private int scoreForActiveTopicSegment = 0;      // Correct answers for the current topic segment.
    private int attemptedForActiveTopicSegment = 0;  // Attempted cards for the current topic segment.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using ViewBinding.
        binding = ActivityTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase services.
        mDb = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // User authentication is required to start a test, as results are tied to users.
        if (currentUser == null) {
            Log.e(TAG, "onCreate: User is not authenticated. TestActivity cannot proceed. Finishing.");
            Toast.makeText(this, "Authentication error. Please log in to start a test session.", Toast.LENGTH_LONG).show();
            finish(); // Close the activity.
            return;   // Stop further execution.
        }
        Log.i(TAG, "onCreate: TestActivity started for user: " + currentUser.getUid());

        // Initialize lists and maps used for managing flashcards and topics.
        allFlashcardsForTestSession = new ArrayList<>();
        currentTestQueue = new ArrayList<>();
        orderedTopicsForCurrentTest = new ArrayList<>();
        topicIdToTitleMap = new HashMap<>();

        // Retrieve test parameters (mode, IDs, titles) passed via Intent.
        // These parameters define how the test session will be conducted.
        subjectId = getIntent().getStringExtra(EXTRA_SUBJECT_ID);
        topicId = getIntent().getStringExtra(EXTRA_TOPIC_ID);
        testMode = getIntent().getStringExtra(EXTRA_TEST_MODE);
        subjectTitleForToolbar = getIntent().getStringExtra(EXTRA_SUBJECT_TITLE);
        topicTitleForToolbar = getIntent().getStringExtra(EXTRA_TOPIC_TITLE);

        Log.i(TAG, "onCreate: Test parameters received - Mode: '" + testMode + "', SubjectId: '" + subjectId + "', TopicId: '" + topicId +
                "', SubjectTitle: '" + subjectTitleForToolbar + "', TopicTitle: '" + topicTitleForToolbar + "'");

        // Validate critical intent extras based on the test mode.
        // My design requires specific IDs for certain modes to function correctly.
        if (testMode == null) {
            Log.e(TAG, "onCreate: Test Mode (EXTRA_TEST_MODE) not provided in Intent. Finishing activity.");
            Toast.makeText(this, "Error starting test: Test mode is missing.", Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        // Subject ID is needed for modes that span an entire subject.
        if (subjectId == null && (MODE_RANDOM_ALL_SUBJECT.equals(testMode) || MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode))) {
            Log.e(TAG, "onCreate: Subject ID (EXTRA_SUBJECT_ID) is required for test mode '" + testMode + "' but was not provided. Finishing activity.");
            Toast.makeText(this, "Error: Subject ID is missing for the selected test mode.", Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        // Topic ID is needed specifically for testing a single topic.
        if (topicId == null && MODE_SPECIFIC_TOPIC.equals(testMode)) {
            Log.e(TAG, "onCreate: Topic ID (EXTRA_TOPIC_ID) is required for MODE_SPECIFIC_TOPIC but was not provided. Finishing activity.");
            Toast.makeText(this, "Error: Topic ID is missing for this specific topic test mode.", Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        // Setup UI components and load initial data.
        setupToolbar();
        loadAnimations(); // Prepare card flip animations.
        setupGestureDetectionAndArrowClickListeners(); // Enable swipe and click interactions.
        loadFlashcardsForTest(); // Start loading flashcards based on the chosen mode.
    }

    /**
     * Configures the toolbar for the TestActivity.
     * The title is dynamically set based on the current test mode and available subject/topic titles
     * to provide context to the user.
     */
    private void setupToolbar() {
        setSupportActionBar(binding.toolbarTest);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Show back arrow.
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Determine toolbar title based on test context.
            String title = getString(R.string.title_activity_test_mode); // Default title.
            if (MODE_SPECIFIC_TOPIC.equals(testMode) && topicTitleForToolbar != null && !topicTitleForToolbar.isEmpty()) {
                title = topicTitleForToolbar + " Test"; // e.g., "Chapter 1 Test"
            } else if (subjectTitleForToolbar != null && !subjectTitleForToolbar.isEmpty()) {
                // For subject-wide tests (random or sequential), use the subject title.
                title = subjectTitleForToolbar + " Test"; // e.g., "Calculus Test"
            }
            getSupportActionBar().setTitle(title);
            Log.d(TAG, "Toolbar setup. Title: '" + title + "'");
        }
    }

    /**
     * Loads and configures animations for the flashcard flip effect.
     * Setting camera distance is important for achieving a realistic 3D flip perspective.
     */
    private void loadAnimations() {
        // Scale factor based on screen density, used for camera distance calculation.
        scale = getApplicationContext().getResources().getDisplayMetrics().density;
        // Set camera distance for the card views to enhance the 3D flip animation.
        // Higher values prevent the card from looking too distorted during rotation.
        binding.cardQuestion.setCameraDistance(8000 * scale);
        binding.cardAnswer.setCameraDistance(8000 * scale);
        Log.d(TAG, "Card flip animations configured with camera distance.");
    }

    /**
     * Sets up gesture detection for swipe interactions on the flashcard container
     * and click listeners for the left/right arrow ImageViews.
     * This allows users to flip cards (tap/swipe up) and mark them correct/wrong (swipe left/right or tap arrows).
     */
    private void setupGestureDetectionAndArrowClickListeners() {
        // Initialize GestureDetector with my custom SwipeGestureListener.
        gestureDetector = new GestureDetectorCompat(this, new SwipeGestureListener());
        // Attach touch listener to the flashcard container to intercept touch events for gestures.
        binding.flashcardContainer.setOnTouchListener((v, event) -> {
            if (testFinished) return true; // Ignore gestures if test is finished.
            return gestureDetector.onTouchEvent(event); // Let gesture detector handle the event.
        });

        // Left arrow (Mark Wrong) click listener.
        binding.imageViewSwipeLeftIndicator.setOnClickListener(v -> {
            Log.d(TAG, "Left arrow (Mark Wrong) clicked by user.");
            // Action only allowed if answer is visible and test is not finished.
            if (!isFrontVisible && !testFinished) {
                markWrong();
            } else {
                Log.d(TAG, "Left arrow click ignored. Conditions not met - isFrontVisible: " + isFrontVisible + ", testFinished: " + testFinished);
            }
        });

        // Right arrow (Mark Correct) click listener.
        binding.imageViewSwipeRightIndicator.setOnClickListener(v -> {
            Log.d(TAG, "Right arrow (Mark Correct) clicked by user.");
            // Action only allowed if answer is visible and test is not finished.
            if (!isFrontVisible && !testFinished) {
                markCorrect();
            } else {
                Log.d(TAG, "Right arrow click ignored. Conditions not met - isFrontVisible: " + isFrontVisible + ", testFinished: " + testFinished);
            }
        });
        Log.d(TAG, "Gesture detector and arrow click listeners have been set up.");
    }


    /**
     * Resets the score and attempted count for the currently active topic segment
     * in {@link #MODE_TOPIC_BY_TOPIC_SEQUENTIAL}.
     * This is called when a new topic segment begins.
     */
    private void resetActiveTopicSegmentScores() {
        scoreForActiveTopicSegment = 0;
        attemptedForActiveTopicSegment = 0;
        Log.d(TAG, "Scores for the active topic segment have been reset.");
    }

    /**
     * Resets all scores and state variables for the entire test session.
     * This is called before starting a new test to ensure a clean slate.
     */
    private void resetSessionScoresAndState() {
        overallSessionScore = 0;
        overallSessionCardsAttempted = 0;
        currentCardIndex = -1;
        testFinished = false;
        isFrontVisible = true; // Start with the question side visible.
        // Reset sequential mode specific trackers.
        activeTopicIdInSequentialTest = null;
        activeTopicTitleInSequentialTest = null;
        resetActiveTopicSegmentScores(); // Also reset segment scores.
        // Reset UI display for scores.
        binding.textViewCorrectCount.setText("0");
        binding.textViewIncorrectCount.setText("0");
        Log.d(TAG, "All session scores and internal test states have been reset.");
    }

    /**
     * Orchestrates the loading of flashcards based on the selected {@link #testMode}.
     * It clears previous session data, shows a loading indicator, and then calls the
     * appropriate helper method to fetch flashcards from Firestore.
     * This is a critical part of my design to support different testing scenarios.
     */
    private void loadFlashcardsForTest() {
        if (currentUser == null) {
            Log.e(TAG, "loadFlashcardsForTest: currentUser is null. Cannot proceed. Finishing activity.");
            finish(); // Should have been caught in onCreate, but as a safeguard.
            return;
        }
        binding.progressBarTest.setVisibility(View.VISIBLE); // Show loading indicator.
        binding.textViewCardProgress.setText("Loading flashcards..."); // Initial progress message.
        resetSessionScoresAndState(); // Ensure a clean state for the new test.
        allFlashcardsForTestSession.clear(); // Clear any flashcards from a previous session.
        topicIdToTitleMap.clear(); // Clear topic titles map for the new session.

        Log.i(TAG, "loadFlashcardsForTest: Starting flashcard load for mode: " + testMode);

        // Branch logic based on the test mode.
        if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode)) {
            Log.i(TAG, "Loading flashcards for MODE_TOPIC_BY_TOPIC_SEQUENTIAL. Subject ID: " + subjectId);
            loadTopicsAndThenFlashcardsSequentially();
        } else if (MODE_RANDOM_ALL_SUBJECT.equals(testMode)) {
            Log.i(TAG, "Loading flashcards for MODE_RANDOM_ALL_SUBJECT. Subject ID: " + subjectId);
            // For this mode, I first load all topic titles (for display on cards) and then fetch all flashcards for the subject.
            loadTopicTitlesAndThenRandomFlashcardsForSubject();
        } else if (MODE_SPECIFIC_TOPIC.equals(testMode)) {
            Log.i(TAG, "Loading flashcards for MODE_SPECIFIC_TOPIC. Topic ID: " + topicId);
            // Populate topicIdToTitleMap for the single topic being tested.
            if (topicTitleForToolbar != null && topicId != null) {
                topicIdToTitleMap.put(topicId, topicTitleForToolbar);
            }
            // Construct Firestore query for flashcards of the specific topic.
            Query flashcardQuery = mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                    .whereEqualTo("topicId", topicId) // Filter by the specific topic ID.
                    .orderBy("timestamp", Query.Direction.ASCENDING); // Default order by creation.

            flashcardQuery.get().addOnCompleteListener(task -> {
                binding.progressBarTest.setVisibility(View.GONE); // Hide loading indicator.
                if (task.isSuccessful() && task.getResult() != null) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        try {
                            Flashcard flashcard = document.toObject(Flashcard.class);
                            flashcard.setDocumentId(document.getId());
                            allFlashcardsForTestSession.add(flashcard);
                        } catch (Exception e) {
                            Log.e(TAG, "Error converting Firestore document to Flashcard object: " + document.getId(), e);
                        }
                    }
                    Log.i(TAG, "Successfully fetched " + allFlashcardsForTestSession.size() + " flashcards for mode: " + testMode);
                } else {
                    Log.e(TAG, "Error loading flashcards for " + testMode + " (Topic ID: " + topicId + "): ", task.getException());
                    binding.textViewCardQuestion.setText(getString(R.string.error_loading_flashcard));
                    Toast.makeText(TestActivity.this, getString(R.string.error_loading_flashcard), Toast.LENGTH_SHORT).show();
                }
                prepareTestQueue(); // Prepare the loaded flashcards for the test.
            });
        } else {
            // Fallback for unhandled test modes.
            Log.e(TAG, "loadFlashcardsForTest: Unhandled or invalid test mode received: " + testMode);
            binding.progressBarTest.setVisibility(View.GONE);
            binding.textViewCardQuestion.setText("Error: Invalid test mode selected.");
            Toast.makeText(this, "Invalid test mode. Cannot start test.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Loads topic titles first, then fetches all flashcards for the given subject.
     * Used in {@link #MODE_RANDOM_ALL_SUBJECT} to ensure topic titles are available
     * for display on each flashcard, even though the cards themselves are shuffled.
     * This is part of my design to provide context even in random mode.
     */
    private void loadTopicTitlesAndThenRandomFlashcardsForSubject() {
        Log.i(TAG, "loadTopicTitlesAndThenRandomFlashcardsForSubject: Fetching topic titles for Subject ID: " + subjectId);
        // Step 1: Fetch all topic documents for the subject to populate topicIdToTitleMap.
        // This map allows me to display the topic title on each flashcard later.
        mDb.collection(TopicListActivity.TOPICS_COLLECTION)
                .whereEqualTo("subjectId", subjectId)
                .get()
                .addOnCompleteListener(topicsTask -> {
                    if (topicsTask.isSuccessful() && topicsTask.getResult() != null) {
                        for (QueryDocumentSnapshot doc : topicsTask.getResult()) {
                            try {
                                Topic topic = doc.toObject(Topic.class);
                                topic.setDocumentId(doc.getId());
                                topicIdToTitleMap.put(topic.getDocumentId(), topic.getTitle());
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting Firestore document to Topic object: " + doc.getId(), e);
                            }
                        }
                        Log.i(TAG, "Successfully fetched " + topicIdToTitleMap.size() + " topic titles for Subject ID: " + subjectId);

                        // Step 2: After fetching topic titles, fetch all flashcards for the subject.
                        fetchAllFlashcardsForSubjectAndShuffle();
                    } else {
                        Log.e(TAG, "Error fetching topics for Subject ID " + subjectId + ": ", topicsTask.getException());
                        binding.progressBarTest.setVisibility(View.GONE);
                        binding.textViewCardQuestion.setText("Error loading topic information for the test.");
                        Toast.makeText(this, "Error loading topic data. Test cannot start.", Toast.LENGTH_LONG).show();
                        prepareTestQueue(); // Prepare with an empty list if topic loading fails.
                    }
                });
    }

    /**
     * Fetches all flashcards belonging to the current {@link #subjectId}, then shuffles them.
     * This method is called after topic titles have been loaded, specifically for
     * {@link #MODE_RANDOM_ALL_SUBJECT}.
     * The shuffling ensures a random presentation order.
     */
    private void fetchAllFlashcardsForSubjectAndShuffle() {
        Log.i(TAG, "fetchAllFlashcardsForSubjectAndShuffle: Fetching all flashcards for Subject ID: " + subjectId + " to prepare for shuffling.");
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                .whereEqualTo("subjectId", subjectId) // Get all flashcards for the entire subject.
                .get()
                .addOnCompleteListener(flashcardTask -> {
                    binding.progressBarTest.setVisibility(View.GONE); // Hide loading indicator.
                    if (flashcardTask.isSuccessful() && flashcardTask.getResult() != null) {
                        for (QueryDocumentSnapshot doc : flashcardTask.getResult()) {
                            try {
                                Flashcard flashcard = doc.toObject(Flashcard.class);
                                flashcard.setDocumentId(doc.getId());
                                allFlashcardsForTestSession.add(flashcard);
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting Firestore document to Flashcard object: " + doc.getId(), e);
                            }
                        }
                        Log.i(TAG, "Successfully fetched " + allFlashcardsForTestSession.size() + " total flashcards for Subject ID: " + subjectId);
                        // Shuffle the collected flashcards for random presentation order.
                        Collections.shuffle(allFlashcardsForTestSession);
                        Log.d(TAG, "Flashcards have been shuffled for MODE_RANDOM_ALL_SUBJECT.");
                    } else {
                        Log.e(TAG, "Error fetching all flashcards for Subject ID " + subjectId + ": ", flashcardTask.getException());
                        binding.textViewCardQuestion.setText(getString(R.string.error_loading_flashcard));
                        Toast.makeText(TestActivity.this, getString(R.string.error_loading_flashcard), Toast.LENGTH_SHORT).show();
                    }
                    prepareTestQueue(); // Prepare the (potentially shuffled) flashcards for the test.
                });
    }


    /**
     * Loads topics for the current subject in a defined order, then recursively fetches
     * flashcards for each topic. Used in {@link #MODE_TOPIC_BY_TOPIC_SEQUENTIAL}.
     * This ensures flashcards are presented grouped by topic, and topics appear in sequence.
     * This is a more complex loading strategy in my design.
     */
    private void loadTopicsAndThenFlashcardsSequentially() {
        Log.i(TAG, "loadTopicsAndThenFlashcardsSequentially: Fetching topics in order for Subject ID: " + subjectId);
        // Step 1: Fetch all Topic documents for the subject, ordered by their timestamp (creation order).
        mDb.collection(TopicListActivity.TOPICS_COLLECTION)
                .whereEqualTo("subjectId", subjectId)
                .orderBy("timestamp", Query.Direction.ASCENDING) // Ensure topics are processed in a consistent order.
                .get()
                .addOnCompleteListener(topicsTask -> {
                    if (topicsTask.isSuccessful() && topicsTask.getResult() != null) {
                        orderedTopicsForCurrentTest.clear(); // Clear previous list.
                        topicIdToTitleMap.clear();           // Clear previous map.
                        for (QueryDocumentSnapshot doc : topicsTask.getResult()) {
                            try {
                                Topic topic = doc.toObject(Topic.class);
                                topic.setDocumentId(doc.getId());
                                orderedTopicsForCurrentTest.add(topic); // Store topics in order.
                                topicIdToTitleMap.put(topic.getDocumentId(), topic.getTitle()); // Populate map for display.
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting Firestore document to Topic object: " + doc.getId(), e);
                            }
                        }
                        Log.i(TAG, "Successfully fetched " + orderedTopicsForCurrentTest.size() + " topics for sequential test.");
                        if (orderedTopicsForCurrentTest.isEmpty()) {
                            // If no topics, then no flashcards can be loaded for this mode.
                            binding.progressBarTest.setVisibility(View.GONE);
                            prepareTestQueue(); // Proceed with an empty flashcard list.
                        } else {
                            // Step 2: Start recursively fetching flashcards for each topic in the ordered list.
                            fetchFlashcardsRecursivelyForTopics(0);
                        }
                    } else {
                        Log.e(TAG, "Error fetching topics for sequential test (Subject ID: " + subjectId + "): ", topicsTask.getException());
                        binding.progressBarTest.setVisibility(View.GONE);
                        binding.textViewCardQuestion.setText("Error loading topic sequence for test.");
                        Toast.makeText(this, "Error loading topics for the test.", Toast.LENGTH_LONG).show();
                        prepareTestQueue(); // Proceed with an empty list if topic fetching fails.
                    }
                });
    }

    /**
     * Recursively fetches flashcards for each topic in the {@link #orderedTopicsForCurrentTest} list.
     * This is a helper for {@link #MODE_TOPIC_BY_TOPIC_SEQUENTIAL}. After fetching flashcards
     * for one topic, it calls itself for the next topic in the sequence.
     * @param topicIndex The index of the current topic in {@link #orderedTopicsForCurrentTest} to fetch flashcards for.
     */
    private void fetchFlashcardsRecursivelyForTopics(int topicIndex) {
        // Base case: If all topics have been processed, finalize the flashcard queue.
        if (topicIndex >= orderedTopicsForCurrentTest.size()) {
            Log.i(TAG, "fetchFlashcardsRecursivelyForTopics: All flashcards fetched for sequential mode. Total count: " + allFlashcardsForTestSession.size());
            binding.progressBarTest.setVisibility(View.GONE); // Hide loading indicator.
            prepareTestQueue(); // All flashcards are loaded, prepare the test.
            return;
        }

        Topic currentTopic = orderedTopicsForCurrentTest.get(topicIndex);
        Log.i(TAG, "fetchFlashcardsRecursivelyForTopics: Fetching flashcards for sequential topic " +
                "(" + (topicIndex + 1) + "/" + orderedTopicsForCurrentTest.size() + "): " + currentTopic.getTitle());

        // Fetch flashcards for the current topic, ordered by their timestamp.
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION)
                .whereEqualTo("topicId", currentTopic.getDocumentId())
                .orderBy("timestamp", Query.Direction.ASCENDING) // Maintain order within the topic.
                .get()
                .addOnCompleteListener(flashcardTask -> {
                    if (flashcardTask.isSuccessful() && flashcardTask.getResult() != null) {
                        int countForThisTopic = 0;
                        for (QueryDocumentSnapshot doc : flashcardTask.getResult()) {
                            try {
                                Flashcard flashcard = doc.toObject(Flashcard.class);
                                flashcard.setDocumentId(doc.getId());
                                allFlashcardsForTestSession.add(flashcard); // Add to the global session list.
                                countForThisTopic++;
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting Firestore document to Flashcard object: " + doc.getId(), e);
                            }
                        }
                        Log.d(TAG, "Successfully fetched " + countForThisTopic + " flashcards for topic: " + currentTopic.getTitle());
                    } else {
                        Log.e(TAG, "Error fetching flashcards for topic: " + currentTopic.getTitle(), flashcardTask.getException());
                        // My design choice: Continue to next topic even if one fails, to make test as complete as possible.
                    }
                    // Recursive call to fetch flashcards for the next topic in the sequence.
                    fetchFlashcardsRecursivelyForTopics(topicIndex + 1);
                });
    }

    /**
     * Prepares the test queue from the loaded {@link #allFlashcardsForTestSession}.
     * Handles the case of no flashcards being available and initializes the UI for the first card.
     * Shuffling (if applicable for the mode) should have been done before this method is called.
     */
    private void prepareTestQueue() {
        Log.d(TAG, "prepareTestQueue: Preparing test with " + allFlashcardsForTestSession.size() + " total flashcards available.");
        currentTestQueue.clear(); // Clear any previous queue.
        testFinished = false;     // Reset finished flag.

        if (allFlashcardsForTestSession.isEmpty()) {
            // Handle case where no flashcards were loaded for the test.
            Log.w(TAG, "prepareTestQueue: No flashcards available for this test session.");
            binding.textViewCardQuestion.setText("No flashcards found for this test!");
            binding.textViewCardAnswer.setText("");
            binding.textViewCardProgress.setText("0/0");
            binding.textViewTapToFlip.setVisibility(View.GONE);
            binding.flashcardContainer.setClickable(false); // Disable interaction.
            testFinished = true; // Mark test as finished.
            showTestSummaryDialog(); // Show summary (which will indicate 0/0).
            return;
        }

        // Populate the actual test queue with all loaded flashcards.
        currentTestQueue.addAll(allFlashcardsForTestSession);
        // Note: Shuffling for MODE_RANDOM_ALL_SUBJECT is handled in 'fetchAllFlashcardsForSubjectAndShuffle'.
        // For MODE_TOPIC_BY_TOPIC_SEQUENTIAL, 'allFlashcardsForTestSession' is already ordered.

        currentCardIndex = -1; // Reset card index to start before the first card.
        binding.progressBarTest.setMax(currentTestQueue.size()); // Set progress bar maximum.
        binding.flashcardContainer.setClickable(true); // Enable interaction.
        binding.textViewTapToFlip.setVisibility(View.VISIBLE); // Show interaction hint.
        nextCard(); // Load and display the first card.
        Log.d(TAG, "prepareTestQueue: Test queue ready. Size: " + currentTestQueue.size());
    }

    /**
     * Handles topic transitions specifically for {@link #MODE_TOPIC_BY_TOPIC_SEQUENTIAL}.
     * When a new flashcard belongs to a different topic than the previous one,
     * this method saves the result for the just-completed topic segment and updates
     * the active topic trackers. This is a key part of my sequential testing design.
     * @param currentFlashcard The flashcard that is about to be displayed.
     */
    private void handleTopicTransitionIfNeeded(Flashcard currentFlashcard) {
        // This logic is only relevant for the sequential topic-by-topic test mode.
        if (!MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode)) return;

        String newCardTopicId = currentFlashcard.getTopicId();
        // Retrieve the title for the new card's topic from the pre-populated map.
        String newCardTopicTitle = topicIdToTitleMap.get(newCardTopicId);
        if (newCardTopicTitle == null) newCardTopicTitle = "Unknown Topic"; // Fallback.

        if (activeTopicIdInSequentialTest == null) {
            // This is the first card of the first topic segment.
            activeTopicIdInSequentialTest = newCardTopicId;
            activeTopicTitleInSequentialTest = newCardTopicTitle;
            resetActiveTopicSegmentScores(); // Initialize scores for this new segment.
            Log.i(TAG, "handleTopicTransitionIfNeeded: Starting first topic segment - Topic: '" + activeTopicTitleInSequentialTest + "'");
        } else if (!activeTopicIdInSequentialTest.equals(newCardTopicId)) {
            // The new card belongs to a different topic than the previous one: a transition has occurred.
            Log.i(TAG, "handleTopicTransitionIfNeeded: Transitioning from topic '" + activeTopicTitleInSequentialTest + "' to '" + newCardTopicTitle + "'.");
            // Save the result for the topic segment that just finished, if any cards were attempted.
            if (attemptedForActiveTopicSegment > 0) {
                saveIndividualTopicResult(activeTopicIdInSequentialTest, activeTopicTitleInSequentialTest,
                        scoreForActiveTopicSegment, attemptedForActiveTopicSegment);
            }
            // Update active topic trackers to the new topic.
            activeTopicIdInSequentialTest = newCardTopicId;
            activeTopicTitleInSequentialTest = newCardTopicTitle;
            resetActiveTopicSegmentScores(); // Reset scores for the new segment.
            Log.i(TAG, "handleTopicTransitionIfNeeded: Switched to new topic segment - Topic: '" + activeTopicTitleInSequentialTest + "'");
        }
        // If the new card's topic is the same as the active one, no transition handling is needed.
    }

    /**
     * Displays the current flashcard's content (question or answer) on the screen.
     * It updates the text views, manages card visibility for the flip animation,
     * and updates the toolbar subtitle if in sequential or specific topic mode.
     * If the test is finished, it calls {@link #showTestSummaryDialog()}.
     */
    private void displayCard() {
        Log.d(TAG, "displayCard: Attempting to display card. TestFinished: " + testFinished +
                ", CurrentCardIndex: " + currentCardIndex + ", QueueSize: " + currentTestQueue.size());

        // If test is marked as finished, show the summary.
        if (testFinished) {
            Log.d(TAG, "displayCard: Test is already finished. Showing summary dialog.");
            showTestSummaryDialog();
            return;
        }

        // Check if the current card index is valid.
        if (currentCardIndex >= 0 && currentCardIndex < currentTestQueue.size()) {
            Flashcard currentFlashcard = currentTestQueue.get(currentCardIndex);

            // For sequential mode, check if we've transitioned to a new topic.
            if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode)) {
                handleTopicTransitionIfNeeded(currentFlashcard);
            }

            // Set question and answer text on the respective card faces.
            binding.textViewCardQuestion.setText(currentFlashcard.getQuestion());
            binding.textViewCardAnswer.setText(currentFlashcard.getAnswer());

            // --- Display Topic Title on the Card ---
            // My design includes showing the topic of the current flashcard directly on the card.
            String displayTopicTitleOnCard = topicIdToTitleMap.get(currentFlashcard.getTopicId());
            if (displayTopicTitleOnCard == null || displayTopicTitleOnCard.isEmpty()) {
                // Fallback logic if topic title isn't found in the map.
                if (MODE_SPECIFIC_TOPIC.equals(testMode) && topicTitleForToolbar != null && !topicTitleForToolbar.isEmpty()) {
                    // For specific topic mode, use the known topic title.
                    displayTopicTitleOnCard = topicTitleForToolbar;
                } else {
                    displayTopicTitleOnCard = "Unknown Topic"; // General fallback.
                    Log.w(TAG, "displayCard: Topic title not found in map for TopicID: " + currentFlashcard.getTopicId() + ". Displaying 'Unknown Topic'.");
                }
            }
            binding.textViewCardTopic.setText(displayTopicTitleOnCard); // Topic on question card.
            binding.textViewCardAnswerTopic.setText(displayTopicTitleOnCard); // Topic on answer card.
            // --- End Topic Display Logic ---


            // Update toolbar subtitle based on the test mode and current topic (if applicable).
            if (getSupportActionBar() != null) {
                if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode) && activeTopicTitleInSequentialTest != null) {
                    getSupportActionBar().setSubtitle("Current Topic: " + activeTopicTitleInSequentialTest);
                } else if (MODE_SPECIFIC_TOPIC.equals(testMode) && topicTitleForToolbar != null) {
                    getSupportActionBar().setSubtitle("Testing Topic: " + topicTitleForToolbar);
                } else {
                    // For random all subject, no single topic subtitle for the entire test,
                    // as each card displays its own topic. So, clear subtitle.
                    getSupportActionBar().setSubtitle(null);
                }
            }

            // Reset card views for display (question front, answer back and hidden).
            // This prepares the card for the initial view or after a flip back to question.
            binding.cardQuestion.setAlpha(1f); binding.cardQuestion.setRotationY(0f); binding.cardQuestion.setVisibility(View.VISIBLE);
            binding.cardAnswer.setAlpha(0f); binding.cardAnswer.setRotationY(isFrontVisible ? 90f : -90f); // Prepare for flip-in animation.
            binding.cardAnswer.setVisibility(View.GONE);
            isFrontVisible = true; // Ensure front (question) is marked as visible.

            // Update UI hints.
            binding.textViewTapToFlip.setText(getString(R.string.swipe_up_to_reveal));
            binding.textViewTapToFlip.setVisibility(View.VISIBLE);
            // Hide marking arrows initially (they appear when answer is shown).
            binding.imageViewSwipeLeftIndicator.setVisibility(View.GONE);
            binding.imageViewSwipeRightIndicator.setVisibility(View.GONE);
        } else {
            // Index is out of bounds or queue is empty, meaning the test should end.
            Log.d(TAG, "displayCard: currentCardIndex (" + currentCardIndex + ") is out of bounds for queue size (" +
                    currentTestQueue.size() + ") or queue is empty. Marking test as finished.");
            testFinished = true;
            showTestSummaryDialog(); // Show summary.
        }
        updateProgressUI(); // Update progress bar and text.
    }

    /**
     * Animates the flipping of the flashcard between its question and answer sides.
     * My implementation uses AnimatorSet loaded from XML animator resources.
     * It toggles visibility of swipe indicators for marking correct/wrong.
     */
    private void flipCard() {
        if (testFinished) return; // Don't allow flipping if test is over.
        Log.d(TAG, "flipCard: Initiating card flip. isFrontVisible (before flip): " + isFrontVisible);

        // Load XML animators for the flip effect.
        AnimatorSet outAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.card_flip_out_to_middle);
        AnimatorSet inAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.card_flip_in_from_middle);

        View cardToShow, cardToHide;
        // Determine which card face to show and which to hide based on current state.
        if (isFrontVisible) { // Currently showing question, flip to answer.
            cardToHide = binding.cardQuestion;
            cardToShow = binding.cardAnswer;
            // Show marking indicators (arrows) when answer is visible.
            binding.imageViewSwipeLeftIndicator.setVisibility(View.VISIBLE);
            binding.imageViewSwipeRightIndicator.setVisibility(View.VISIBLE);
            binding.textViewTapToFlip.setVisibility(View.GONE); // Hide "tap to flip" hint.
        } else { // Currently showing answer, flip back to question.
            cardToHide = binding.cardAnswer;
            cardToShow = binding.cardQuestion;
            // Hide marking indicators when question is visible.
            binding.imageViewSwipeLeftIndicator.setVisibility(View.GONE);
            binding.imageViewSwipeRightIndicator.setVisibility(View.GONE);
            binding.textViewTapToFlip.setText(getString(R.string.swipe_up_to_reveal)); // Reset hint text.
            binding.textViewTapToFlip.setVisibility(View.VISIBLE);
        }

        // Set targets for animations.
        outAnim.setTarget(cardToHide);
        inAnim.setTarget(cardToShow);

        // Chain animations: when out-animation ends, start in-animation.
        // This creates a smooth two-part flip.
        outAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // Finalize state of hidden card and prepare visible card for in-animation.
                cardToHide.setVisibility(View.GONE); cardToHide.setAlpha(1f); cardToHide.setRotationY(0f);
                cardToShow.setAlpha(0f); cardToShow.setRotationY(isFrontVisible ? 90f : -90f); // Set initial rotation for flip-in.
                cardToShow.setVisibility(View.VISIBLE);
                inAnim.start(); // Start the second half of the flip.
            }
        });
        outAnim.start(); // Start the first half of the flip.
        isFrontVisible = !isFrontVisible; // Toggle the state.
        Log.d(TAG, "flipCard: Card flip animation complete. isFrontVisible (after flip): " + isFrontVisible);
    }

    /**
     * Advances to the next flashcard in the queue.
     * If all cards have been attempted, it marks the test as finished.
     * It also handles saving the final topic segment result in sequential mode.
     */
    private void nextCard() {
        Log.d(TAG, "nextCard: Advancing card. OverallAttempted: " + overallSessionCardsAttempted +
                ", QueueSize: " + currentTestQueue.size() + ", TestFinished: " + testFinished);

        if (testFinished) {
            // If test is already marked finished (e.g., by index out of bounds), ensure displayCard shows summary.
            Log.d(TAG, "nextCard: Test is already marked finished. Calling displayCard to ensure summary is shown.");
            displayCard(); // This will likely call showTestSummaryDialog().
            return;
        }

        // Check if all cards in the current session queue have been attempted.
        if (overallSessionCardsAttempted >= currentTestQueue.size()) {
            Log.i(TAG, "nextCard: All cards in the current session queue have been attempted. Marking test as finished.");
            testFinished = true;
            // If in sequential mode and the last segment had attempts, save its result.
            if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode) && activeTopicIdInSequentialTest != null && attemptedForActiveTopicSegment > 0) {
                Log.d(TAG, "nextCard: End of queue reached. Saving final topic segment result for: " + activeTopicTitleInSequentialTest);
                saveIndividualTopicResult(activeTopicIdInSequentialTest, activeTopicTitleInSequentialTest, scoreForActiveTopicSegment, attemptedForActiveTopicSegment);
                activeTopicIdInSequentialTest = null; // Clear active topic as session is ending.
            }
            displayCard(); // Call displayCard, which will see testFinished=true and show summary.
            return;
        }

        // Increment index to move to the next card.
        currentCardIndex++;
        Log.d(TAG, "nextCard: Incremented currentCardIndex to: " + currentCardIndex);

        // Display the card at the new index if it's within bounds.
        if (currentCardIndex < currentTestQueue.size()) {
            displayCard();
        } else {
            // Index is now out of bounds, meaning all cards have been cycled through.
            Log.i(TAG, "nextCard: currentCardIndex (" + currentCardIndex + ") is now at or beyond queue size (" +
                    currentTestQueue.size() + "). Marking test as finished.");
            testFinished = true;
            // Similar to above, save final segment if in sequential mode.
            if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode) && activeTopicIdInSequentialTest != null && attemptedForActiveTopicSegment > 0) {
                Log.d(TAG, "nextCard: Index out of bounds. Saving final topic segment result for: " + activeTopicTitleInSequentialTest);
                saveIndividualTopicResult(activeTopicIdInSequentialTest, activeTopicTitleInSequentialTest, scoreForActiveTopicSegment, attemptedForActiveTopicSegment);
            }
            displayCard(); // Call displayCard to show summary.
        }
    }

    /**
     * Marks the current flashcard as correct.
     * This action is only allowed if the answer side of the card is visible and the test is not finished.
     * It updates scores and proceeds to the next card.
     */
    private void markCorrect() {
        // Validate state: answer must be visible, test not finished, and current card index must be valid.
        if (isFrontVisible || testFinished || currentCardIndex < 0 || currentCardIndex >= currentTestQueue.size()) {
            Log.w(TAG, "markCorrect: Action ignored. Conditions not met - isFrontVisible: " + isFrontVisible +
                    ", testFinished: " + testFinished + ", currentCardIndex: " + currentCardIndex);
            return;
        }
        overallSessionScore++; // Increment overall correct score.
        overallSessionCardsAttempted++; // Increment overall attempted cards.

        // If in sequential mode, also update the current topic segment's score.
        if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode)) {
            scoreForActiveTopicSegment++;
            attemptedForActiveTopicSegment++;
        }
        Log.d(TAG, "Card marked CORRECT. Overall Score: " + overallSessionScore + "/" + overallSessionCardsAttempted +
                (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode) ? ", Segment Score: " + scoreForActiveTopicSegment + "/" + attemptedForActiveTopicSegment : ""));
        nextCard(); // Proceed to the next card.
    }

    /**
     * Marks the current flashcard as incorrect.
     * This action is only allowed if the answer side of the card is visible and the test is not finished.
     * It updates the attempted count and proceeds to the next card.
     */
    private void markWrong() {
        // Validate state, similar to markCorrect.
        if (isFrontVisible || testFinished || currentCardIndex < 0 || currentCardIndex >= currentTestQueue.size()) {
            Log.w(TAG, "markWrong: Action ignored. Conditions not met - isFrontVisible: " + isFrontVisible +
                    ", testFinished: " + testFinished + ", currentCardIndex: " + currentCardIndex);
            return;
        }
        overallSessionCardsAttempted++; // Increment overall attempted cards (score doesn't change for wrong).

        // If in sequential mode, update the current topic segment's attempted count.
        if (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode)) {
            attemptedForActiveTopicSegment++;
        }
        Log.d(TAG, "Card marked WRONG. Overall Score: " + overallSessionScore + "/" + overallSessionCardsAttempted +
                (MODE_TOPIC_BY_TOPIC_SEQUENTIAL.equals(testMode) ? ", Segment Score: " + scoreForActiveTopicSegment + "/" + attemptedForActiveTopicSegment : ""));
        nextCard(); // Proceed to the next card.
    }

    /**
     * Updates the UI elements that display test progress, such as the progress bar,
     * current card number, total cards, and counts of correct/incorrect answers.
     */
    private void updateProgressUI() {
        int totalCards = currentTestQueue.size();
        // Determine the card number to display (1-based index).
        // If test is finished, show total attempted. Otherwise, show current card number or 0 if before start.
        int currentCardDisplayNumber = testFinished ? overallSessionCardsAttempted : (currentCardIndex >= 0 ? currentCardIndex + 1 : 0);

        binding.progressBarTest.setMax(totalCards);
        // Progress bar reflects cards attempted out of total.
        binding.progressBarTest.setProgress(Math.min(overallSessionCardsAttempted, totalCards));

        // Update text views for card progress and scores.
        if (totalCards > 0) {
            if (!testFinished) {
                binding.textViewCardProgress.setText(String.format(Locale.getDefault(), "Card %d of %d", currentCardDisplayNumber, totalCards));
            } else {
                // When test is finished, show how many were completed.
                binding.textViewCardProgress.setText(String.format(Locale.getDefault(), "Completed: %d / %d", overallSessionCardsAttempted, totalCards));
            }
        } else {
            binding.textViewCardProgress.setText("0/0"); // For empty test case.
        }
        binding.textViewCorrectCount.setText(String.valueOf(overallSessionScore));
        binding.textViewIncorrectCount.setText(String.valueOf(overallSessionCardsAttempted - overallSessionScore));
        Log.d(TAG, "updateProgressUI: Displaying Progress: " + overallSessionCardsAttempted + "/" + totalCards + ", Current Score: " + overallSessionScore);
    }

    /**
     * Saves the test result for an individual topic segment to Firestore.
     * This method is specifically used in {@link #MODE_TOPIC_BY_TOPIC_SEQUENTIAL} when the test
     * transitions from one topic to the next, or when the entire sequential test finishes.
     * My design choice here is to save each topic's performance as a separate `TestResult`
     * marked as if it were a {@link #MODE_SPECIFIC_TOPIC} test for that topic.
     * @param topicIdToSave The ID of the topic whose result is being saved.
     * @param topicTitleToSave The title of the topic.
     * @param scoreForSeg The number of correct answers for this topic segment.
     * @param attemptedInSeg The number of cards attempted in this topic segment.
     */
    private void saveIndividualTopicResult(String topicIdToSave, String topicTitleToSave, int scoreForSeg, int attemptedInSeg) {
        // Validate essential data before attempting to save.
        if (currentUser == null || subjectId == null || topicIdToSave == null || topicTitleToSave == null) {
            Log.w(TAG, "saveIndividualTopicResult: Critical information missing for saving topic segment result. Topic: " + topicTitleToSave);
            return;
        }
        if (attemptedInSeg == 0) {
            // Don't save a result if no cards were actually attempted for this topic segment.
            Log.d(TAG, "saveIndividualTopicResult: No cards were attempted for topic segment '" + topicTitleToSave + "'. Result not saved.");
            return;
        }
        Log.i(TAG, "Saving INDIVIDUAL topic segment result to Firestore. User: " + currentUser.getUid() +
                ", Topic: '" + topicTitleToSave + "', Score: " + scoreForSeg + "/" + attemptedInSeg);

        // Create a TestResult object. Note: testMode is set to MODE_SPECIFIC_TOPIC here,
        // as each segment result is effectively a test of that specific topic.
        TestResult result = new TestResult(
                currentUser.getUid(),
                subjectId,
                subjectTitleForToolbar != null ? subjectTitleForToolbar : "Subject", // Parent subject title.
                topicIdToSave,
                topicTitleToSave,
                scoreForSeg,
                attemptedInSeg,
                TestActivity.MODE_SPECIFIC_TOPIC // Treat segment result as a specific topic test.
        );
        // Add the result to the 'testResults' collection in Firestore.
        mDb.collection(TEST_RESULTS_COLLECTION).add(result)
                .addOnSuccessListener(docRef -> Log.i(TAG, "saveIndividualTopicResult: SUCCESS! Individual topic TestResult for '" + topicTitleToSave + "' saved. Document ID: " + docRef.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "saveIndividualTopicResult: FAILURE! Error saving individual topic TestResult for '" + topicTitleToSave + "': ", e));
    }

    /**
     * Saves the main test result to Firestore upon completion of the entire test session.
     * This is used for {@link #MODE_SPECIFIC_TOPIC} and {@link #MODE_RANDOM_ALL_SUBJECT}.
     * For {@link #MODE_TOPIC_BY_TOPIC_SEQUENTIAL}, individual topic results are saved by
     * {@link #saveIndividualTopicResult(String, String, int, int)}, and this method
     * would not typically save an additional overall summary for that mode from here.
     */
    private void saveMainTestResult() {
        if (currentUser == null) {
            Log.w(TAG, "saveMainTestResult: User not logged in. Cannot save test result.");
            return;
        }

        // Logic for MODE_SPECIFIC_TOPIC.
        if (MODE_SPECIFIC_TOPIC.equals(testMode) && topicId != null && subjectId != null) {
            if (overallSessionCardsAttempted > 0) {
                Log.i(TAG, "Saving MAIN test result for MODE_SPECIFIC_TOPIC. User: " + currentUser.getUid() +
                        ", Score: " + overallSessionScore + "/" + overallSessionCardsAttempted);
                String sTitle = (subjectTitleForToolbar != null && !subjectTitleForToolbar.isEmpty()) ? subjectTitleForToolbar : getString(R.string.unknown_subject_name);
                String tTitle = (topicTitleForToolbar != null && !topicTitleForToolbar.isEmpty()) ? topicTitleForToolbar : "Unknown Topic";
                TestResult result = new TestResult(currentUser.getUid(), subjectId, sTitle, topicId, tTitle,
                        overallSessionScore, overallSessionCardsAttempted, testMode);
                mDb.collection(TEST_RESULTS_COLLECTION).add(result)
                        .addOnSuccessListener(docRef -> Log.i(TAG, "saveMainTestResult: SUCCESS! TestResult saved for specific topic '" + tTitle + "'. ID: " + docRef.getId()))
                        .addOnFailureListener(e -> Log.e(TAG, "saveMainTestResult: FAILURE! Error saving TestResult for specific topic '" + tTitle + "': ", e));
            } else {
                Log.d(TAG, "saveMainTestResult (Specific Topic): No cards attempted in this session. Result not saved.");
            }
        } else if (MODE_RANDOM_ALL_SUBJECT.equals(testMode) && subjectId != null) {
            // Logic for MODE_RANDOM_ALL_SUBJECT.
            // Here, I save a single result representing the entire random session for the subject.
            if (overallSessionCardsAttempted > 0) {
                Log.i(TAG, "Saving MAIN test result for MODE_RANDOM_ALL_SUBJECT. User: " + currentUser.getUid() +
                        ", Score: " + overallSessionScore + "/" + overallSessionCardsAttempted);
                String sTitle = (subjectTitleForToolbar != null && !subjectTitleForToolbar.isEmpty()) ? subjectTitleForToolbar : getString(R.string.unknown_subject_name);
                // For a "random all subject" test, a specific topicId isn't applicable for the overall result.
                // I use a placeholder or descriptive title for the topic field.
                TestResult result = new TestResult(currentUser.getUid(), subjectId, sTitle,
                        null, // No specific topic ID for the overall "all random" result.
                        "All Random Flashcards", // A descriptive name for this type of test.
                        overallSessionScore, overallSessionCardsAttempted, testMode);
                mDb.collection(TEST_RESULTS_COLLECTION).add(result)
                        .addOnSuccessListener(docRef -> Log.i(TAG, "saveMainTestResult: SUCCESS! TestResult saved for random all subject '" + sTitle + "'. ID: " + docRef.getId()))
                        .addOnFailureListener(e -> Log.e(TAG, "saveMainTestResult: FAILURE! Error saving TestResult for random all subject '" + sTitle + "': ", e));
            } else {
                Log.d(TAG, "saveMainTestResult (Random All Subject): No cards attempted. Result not saved.");
            }
        } else {
            // This method is not intended to save a main result for MODE_TOPIC_BY_TOPIC_SEQUENTIAL here,
            // as those are saved segment by segment.
            Log.d(TAG, "saveMainTestResult: Test mode is not SPECIFIC_TOPIC or RANDOM_ALL_SUBJECT, or required IDs are missing. No main result saved by this method call.");
        }
    }

    /**
     * Displays a summary dialog when the test session is completed.
     * It shows the final score and percentage. It also triggers saving the main test result
     * for relevant modes before showing the dialog.
     * My design uses an AlertDialog for this summary.
     */
    private void showTestSummaryDialog() {
        Log.i(TAG, "showTestSummaryDialog: Preparing to display test summary. Overall Score: " +
                overallSessionScore + "/" + overallSessionCardsAttempted);

        // Update UI to reflect "test complete" state.
        binding.cardQuestion.setVisibility(View.VISIBLE); // Show question card area for message.
        binding.textViewCardQuestion.setText(getString(R.string.test_complete));
        binding.cardAnswer.setVisibility(View.GONE); // Hide answer card.
        binding.textViewCardAnswer.setText("");
        binding.flashcardContainer.setClickable(false); // Disable further interaction.
        binding.textViewTapToFlip.setVisibility(View.GONE);
        binding.imageViewSwipeLeftIndicator.setVisibility(View.GONE);
        binding.imageViewSwipeRightIndicator.setVisibility(View.GONE);
        if (getSupportActionBar()!=null) getSupportActionBar().setSubtitle(null); // Clear subtitle.
        updateProgressUI(); // Final update to progress display.

        // Save the main test result if the mode is SPECIFIC_TOPIC or RANDOM_ALL_SUBJECT.
        // For MODE_TOPIC_BY_TOPIC_SEQUENTIAL, individual topic results are saved as each segment finishes.
        if (MODE_SPECIFIC_TOPIC.equals(testMode) || MODE_RANDOM_ALL_SUBJECT.equals(testMode)) {
            saveMainTestResult();
        }

        // Calculate percentage score.
        double percentage = 0.0;
        if (overallSessionCardsAttempted > 0) {
            percentage = ((double) overallSessionScore / overallSessionCardsAttempted) * 100.0;
        }

        // Format the score and final message for the dialog.
        String scorePart = String.format(Locale.getDefault(), getString(R.string.score_label),
                overallSessionScore,
                overallSessionCardsAttempted);
        String finalMessage = String.format(Locale.getDefault(), "%s (%.1f%%)",
                scorePart,
                percentage);

        // Build and show the AlertDialog.
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.test_complete))
                .setMessage(finalMessage)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Set result to OK to indicate to the calling activity (e.g., MainActivity via TopicListActivity)
                    // that a test was completed, which might trigger a refresh of recent scores.
                    setResult(Activity.RESULT_OK);
                    finish(); // Close the TestActivity.
                })
                .setCancelable(false) // User must acknowledge the summary.
                .show();
        Log.d(TAG, "showTestSummaryDialog: Test summary dialog displayed.");
    }

    /**
     * Handles selection of options from the toolbar menu, primarily the "home" (up/back) button.
     * If the test is not finished, it prompts the user to confirm ending the test early,
     * as progress will not be saved in that case.
     * @param item The menu item that was selected.
     * @return True if the item was handled, false otherwise.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { // Back/Up button pressed.
            if (!testFinished) {
                // If test is ongoing, confirm if user wants to exit without saving progress.
                // This is an important UX consideration in my design.
                new AlertDialog.Builder(this)
                        .setTitle("End Test Session?")
                        .setMessage("Are you sure you want to end this test session? Your current progress for this session will not be saved.")
                        .setPositiveButton("End Test", (dialog, which) -> {
                            Log.d(TAG, "User confirmed ending test early via toolbar action.");
                            setResult(Activity.RESULT_CANCELED); // Indicate test was not completed normally.
                            finish(); // Close activity.
                        })
                        .setNegativeButton("Cancel", null) // User chose to continue test.
                        .show();
                return true; // Event handled.
            }
            // If test was already finished, standard back navigation.
            Log.d(TAG, "Home/Up button pressed. Test finished status: " + testFinished + ". Defaulting to RESULT_CANCELED if not already set by summary.");
            // If user presses back after summary dialog (which sets RESULT_OK) but before pressing "OK" on dialog,
            // this path is taken. The result should ideally remain RESULT_OK if already set by the summary.
            // If finish() is called without an explicit setResult before this, it defaults to RESULT_CANCELED.
            if (!isFinishing()) { // Avoid setting result if activity is already in the process of finishing.
                // This ensures that if RESULT_OK was set by showTestSummaryDialog and finish was called,
                // we don't override it here. If the user presses back *after* summary but *before* OK,
                // it's reasonable to consider it a cancellation of acknowledging the summary.
                setResult(Activity.RESULT_CANCELED);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Custom GestureListener to handle swipe gestures on the flashcard container.
     * My design uses horizontal swipes to mark cards correct/wrong (when answer is visible)
     * and vertical swipes (up) to flip the card. A single tap also flips the card.
     */
    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        // Thresholds to distinguish a swipe from a scroll or accidental touch.
        private static final int SWIPE_THRESHOLD = 100; // Minimum swipe distance in pixels.
        private static final int SWIPE_VELOCITY_THRESHOLD = 100; // Minimum swipe speed in pixels per second.

        // onDown is important to return true if we want our GestureDetector to intercept touch events.
        // I only allow gestures if the test is not finished.
        @Override public boolean onDown(MotionEvent e) { return !testFinished; }

        @Override public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            // Ignore fling gestures if test is finished or no card is currently displayed/valid.
            if (testFinished || currentCardIndex < 0 || currentCardIndex >= currentTestQueue.size()) return false;

            float diffX = e2.getX() - e1.getX(); // Horizontal distance of the swipe.
            float diffY = e2.getY() - e1.getY(); // Vertical distance of the swipe.

            // Determine if it's primarily a horizontal or vertical swipe.
            if (Math.abs(diffX) > Math.abs(diffY)) { // Horizontal swipe.
                // Check if the swipe meets the distance and velocity thresholds.
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    // Marking correct/wrong is only allowed if the answer (back) of the card is visible.
                    if (isFrontVisible) {
                        Log.d(TAG, "SwipeGestureListener: Horizontal swipe detected on front of card. Action ignored.");
                        return false; // Don't mark if the question side is showing.
                    }
                    if (diffX > 0) { // Swipe Right (from Left to Right).
                        markCorrect();
                        Log.d(TAG, "SwipeGestureListener: Swipe Right detected, card marked CORRECT.");
                    } else { // Swipe Left (from Right to Left).
                        markWrong();
                        Log.d(TAG, "SwipeGestureListener: Swipe Left detected, card marked WRONG.");
                    }
                    return true; // Gesture handled.
                }
            } else { // Vertical swipe.
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY < 0) { // Swipe Up (from Bottom to Top).
                        flipCard();
                        Log.d(TAG, "SwipeGestureListener: Swipe Up detected, card flipped.");
                    }
                    // Swipe Down is not currently assigned an action in my design.
                    return true; // Gesture handled.
                }
            }
            return false; // Gesture was not significant enough or not handled by this logic.
        }

        // Handle a single tap on the card as an action to flip it.
        @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
            if (testFinished || currentCardIndex < 0 || currentCardIndex >= currentTestQueue.size()) return false;
            Log.d(TAG, "SwipeGestureListener: Single tap up detected, calling flipCard().");
            flipCard();
            return true; // Gesture handled.
        }
    }
}