package com.example.studyspot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.studyspot.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity serves as the main screen or dashboard of the application.
 * It displays a list of subjects and recent test scores if the user is logged in.
 * My design now also supports a "guest mode" if the user skips login, in which case
 * placeholder content is shown and user-specific features are disabled.
 * It handles navigation to other features and uses Firebase Firestore for data.
 * It implements {@link SubjectsAdapter.OnSubjectClickListener} for subject list interactions.
 */
public class MainActivity extends AppCompatActivity implements SubjectsAdapter.OnSubjectClickListener {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    private FirebaseAuth mAuth;
    private UserProfileFetcher mUserProfileFetcher;
    private FirebaseUser currentUser;
    private FirebaseFirestore mDb;

    private SubjectsAdapter subjectsAdapter;
    private List<Subject> subjectList;

    private RecentScoresAdapter recentScoresAdapter;
    private List<TestResult> recentScoresList;

    private ActivityResultLauncher<Intent> addOrEditSubjectLauncher;
    private ActivityResultLauncher<Intent> topicListLauncher;

    private ExecutorService executorService;
    private Handler mainThreadHandler;

    // Flag to indicate if the activity is running in guest mode (login skipped).
    private boolean isGuestMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.d(TAG, "onCreate: Initializing MainActivity.");

        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        mAuth = FirebaseAuth.getInstance();
        mUserProfileFetcher = new UserProfileFetcher();
        mDb = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Check if MainActivity was launched in guest mode from LoginActivity.
        isGuestMode = getIntent().getBooleanExtra("IS_GUEST_MODE", false);

        // Authentication check:
        // If user is null AND not explicitly in guest mode, redirect to Login.
        // If in guest mode, proceed even if user is null.
        if (currentUser == null && !isGuestMode) {
            Log.w(TAG, "onCreate: User is null AND not in guest mode. Redirecting to LoginActivity.");
            goToLoginActivity();
            return; // Stop further execution if redirecting.
        }
        // At this point, either currentUser is not null, OR isGuestMode is true.

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        subjectList = new ArrayList<>();
        recentScoresList = new ArrayList<>();

        setupUserProfileAndToolbarActions();
        setupSubjectsRecyclerView();
        setupRecentScoresRecyclerView();

        // UI and data loading will now correctly handle the guest mode state.
        setupUIBasedOnLoginState();

        addOrEditSubjectLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "addOrEditSubjectLauncher: Result code: " + result.getResultCode());
                    if (currentUser != null && (result.getResultCode() == Activity.RESULT_OK ||
                            result.getResultCode() == TopicListActivity.RESULT_SUBJECT_DETAILS_EDITED ||
                            result.getResultCode() == AddEditSubjectActivity.RESULT_OK_SUBJECT_DELETED_FROM_EDIT_ACTIVITY ||
                            result.getResultCode() == TopicListActivity.RESULT_SUBJECT_DELETED)) {
                        Log.i(TAG, "addOrEditSubjectLauncher: Add/Edit/Delete Subject flow finished or TopicList reported change. Refreshing subjects for logged-in user.");
                        refreshCurrentUserSubjects();
                    } else if (currentUser == null && isGuestMode) {
                        Log.i(TAG, "addOrEditSubjectLauncher: Returned to guest mode. No subject refresh needed from Firebase.");
                    }
                });

        topicListLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "topicListLauncher: Result from TopicListActivity code: " + result.getResultCode());
                    if (currentUser != null && (result.getResultCode() == TopicListActivity.RESULT_SUBJECT_DELETED ||
                            result.getResultCode() == TopicListActivity.RESULT_SUBJECT_DETAILS_EDITED ||
                            result.getResultCode() == Activity.RESULT_OK)) {
                        Log.i(TAG, "topicListLauncher: Subject/Topic flow finished. Refreshing subjects and scores for logged-in user.");
                        refreshCurrentUserSubjects();
                        loadRecentScores();
                    } else if (currentUser == null && isGuestMode) {
                        Log.i(TAG, "topicListLauncher: Returned to guest mode. No data refresh from Firebase needed.");
                    }
                });
        Log.d(TAG, "onCreate: Setup complete. Guest mode: " + isGuestMode);
    }

    /**
     * Sets up click listeners for UI elements like the profile icon and "Add Subject" button.
     * The visibility of some elements will be controlled by {@link #setupUIBasedOnLoginState()}.
     */
    private void setupUserProfileAndToolbarActions() {
        binding.imageViewProfile.setOnClickListener(v -> {
            // ProfileActivity handles its own authentication check.
            // If in guest mode, ProfileActivity will likely redirect to LoginActivity.
            Log.d(TAG, "Profile icon in Toolbar clicked. Launching ProfileActivity. Guest mode: " + isGuestMode);
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        binding.buttonAddSubject.setOnClickListener(v -> {
            // This button's visibility is managed by setupUIBasedOnLoginState.
            // If it's visible, currentUser should not be null.
            if (currentUser != null) {
                Log.d(TAG, "Add Subject Button clicked. Launching AddEditSubjectActivity.");
                Intent intent = new Intent(MainActivity.this, AddEditSubjectActivity.class);
                addOrEditSubjectLauncher.launch(intent);
            } else {
                // This case should ideally not be reached if button visibility is correct.
                Log.w(TAG, "Add Subject button clicked, but no logged-in user (unexpected in this state).");
                Toast.makeText(this, "Please log in to add subjects.", Toast.LENGTH_SHORT).show();
            }
        });
        Log.d(TAG, "setupUserProfileAndToolbarActions: Click listeners set.");
    }


    private void setupSubjectsRecyclerView() {
        binding.recyclerViewSubjects.setLayoutManager(new LinearLayoutManager(this));
        subjectsAdapter = new SubjectsAdapter(this, subjectList, this);
        binding.recyclerViewSubjects.setAdapter(subjectsAdapter);
        Log.d(TAG, "setupSubjectsRecyclerView: Complete.");
    }

    private void setupRecentScoresRecyclerView() {
        binding.recyclerViewRecentScores.setLayoutManager(new LinearLayoutManager(this));
        recentScoresAdapter = new RecentScoresAdapter(this, recentScoresList);
        binding.recyclerViewRecentScores.setAdapter(recentScoresAdapter);
        binding.recyclerViewRecentScores.setNestedScrollingEnabled(false);
        Log.d(TAG, "setupRecentScoresRecyclerView: Complete.");
    }

    /**
     * Refreshes the current authenticated user's subjects from Firebase.
     * If the user is no longer authenticated (and not in guest mode), it navigates to LoginActivity.
     * This method should only be called if a user is expected to be logged in.
     */
    private void refreshCurrentUserSubjects() {
        Log.d(TAG, "refreshCurrentUserSubjects: Attempting to refresh subjects.");
        FirebaseUser freshCurrentUser = mAuth.getCurrentUser(); // Re-check current user state
        if (freshCurrentUser != null) {
            this.currentUser = freshCurrentUser; // Update local instance
            Log.d(TAG, "refreshCurrentUserSubjects: User " + this.currentUser.getUid() + " is authenticated. Loading subjects.");
            loadSubjectsFromFirebaseRefined(this.currentUser.getUid());
        } else {
            // If user becomes null and we are not in guest mode, then it's an issue.
            if (!isGuestMode) {
                Log.w(TAG, "refreshCurrentUserSubjects: User became null and not in guest mode. Navigating to login.");
                this.currentUser = null; // Clear local user
                goToLoginActivity();
            } else {
                // In guest mode, if currentUser is null, this method shouldn't ideally be the primary path
                // for subject display; loadDummySubjectsForGuest handles that.
                Log.i(TAG, "refreshCurrentUserSubjects: User is null, but in guest mode. No Firebase subjects to refresh.");
                setupUIBasedOnLoginState(); // Ensure guest UI is correctly displayed.
            }
        }
    }

    /**
     * Configures the UI based on the user's login state (authenticated or guest).
     * Loads appropriate data or placeholders.
     */
    private void setupUIBasedOnLoginState() {
        // This button seems unused based on current logic, can be removed from XML if not needed.
        binding.buttonAuthAction.setVisibility(View.GONE);

        if (currentUser != null) { // User is logged in
            Log.d(TAG, "setupUIBasedOnLoginState: User is logged in: " + currentUser.getUid() + ". Setting up UI for authenticated user.");
            isGuestMode = false; // Ensure guest mode is false if a user is actually logged in.
            binding.buttonAddSubject.setVisibility(View.VISIBLE);
            // For logged-in user, fetch profile, which in turn fetches subjects, and then fetch scores.
            loadUserProfileDataFromFirebase(currentUser.getUid());
            loadRecentScores();
        } else {
            // This 'else' block means currentUser is null.
            // If isGuestMode is true (set from Intent), then this is the intended guest path.
            // If isGuestMode is false, initial checks in onCreate/onStart/onResume should have redirected to Login.
            Log.d(TAG, "setupUIBasedOnLoginState: User is not logged in. Setting up UI for GUEST MODE.");
            binding.buttonAddSubject.setVisibility(View.VISIBLE); // Hide "Add Subject" for guests.
            loadDummySubjectsForGuest(); // Display placeholder/welcome for subjects.
            updateRecentScoresUI(new ArrayList<>()); // Clear/hide recent scores for guests.
        }
        Log.d(TAG, "setupUIBasedOnLoginState: UI setup based on login state complete. Guest mode: " + isGuestMode);
    }

    /**
     * Fetches profile data for the logged-in user. This should only be called if currentUser is not null.
     * @param userId The UID of the logged-in user.
     */
    private void loadUserProfileDataFromFirebase(String userId) {
        Log.d(TAG, "loadUserProfileDataFromFirebase: Attempting to load profile for user: " + userId);
        if (userId == null || userId.isEmpty()) { // Safeguard
            Log.e(TAG, "loadUserProfileDataFromFirebase: Called with null or empty userId.");
            if (!isGuestMode) goToLoginActivity(); // If not guest, this is an error state.
            return;
        }
        mUserProfileFetcher.fetchUserProfile(userId, new UserProfileCallback() {
            @Override
            public void onSuccess(UserProfile userProfile) {
                if (userProfile != null && userProfile.getDisplayName() != null) {
                    Log.d(TAG, "loadUserProfileDataFromFirebase: Firebase profile loaded for: " + userProfile.getDisplayName());
                } else {
                    Log.w(TAG, "loadUserProfileDataFromFirebase: Firebase profile loaded but display name is null.");
                }
                Log.d(TAG, "loadUserProfileDataFromFirebase: Successfully loaded profile, now loading subjects for user: " + userId);
                loadSubjectsFromFirebaseRefined(userId);
            }

            @Override
            public void onError(Exception e, String message) {
                Log.e(TAG, "loadUserProfileDataFromFirebase: Error loading Firebase profile: " + message, e);
                // My design choice: even if profile fetch fails, attempt to load subjects
                // as they might still be accessible or critical for app function.
                Log.d(TAG, "loadUserProfileDataFromFirebase: Error loading profile, but still attempting to load subjects for user: " + userId);
                loadSubjectsFromFirebaseRefined(userId);
            }
        });
    }

    /**
     * Loads subjects from Firestore for the given authenticated user ID.
     * This method should only be called if a user is logged in.
     * @param firebaseUserId UID of the authenticated user.
     */
    private void loadSubjectsFromFirebaseRefined(String firebaseUserId) {
        Log.i(TAG, "loadSubjectsFromFirebaseRefined: Loading subjects from Firestore for user: " + firebaseUserId);
        // This method is for authenticated users, so if firebaseUserId is invalid, it's an issue.
        if (firebaseUserId == null || firebaseUserId.isEmpty()) {
            Log.e(TAG, "loadSubjectsFromFirebaseRefined: firebaseUserId is null or empty. Cannot query for subjects.");
            mainThreadHandler.post(() -> updateSubjectsUI(new ArrayList<>()));
            if (!isGuestMode) goToLoginActivity(); // If not guest, this is unexpected.
            return;
        }
        // Consider showing loading indicator for subjects
        // mainThreadHandler.post(() -> binding.progressBarSubjects.setVisibility(View.VISIBLE));

        mDb.collection(TopicListActivity.SUBJECTS_COLLECTION)
                .whereEqualTo("userId", firebaseUserId)
                .orderBy("title", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(executorService, task -> {
                    final List<Subject> processedSubjectsList = new ArrayList<>();
                    String gmsTaskErrorMessage = null;

                    if (task.isSuccessful()) {
                        QuerySnapshot result = task.getResult();
                        if (result != null && !result.isEmpty()) {
                            Log.i(TAG, "loadSubjects (BG): Found " + result.size() + " subjects for user " + firebaseUserId);
                            for (QueryDocumentSnapshot document : result) {
                                try {
                                    Subject subject = document.toObject(Subject.class);
                                    subject.setDocumentId(document.getId());
                                    processedSubjectsList.add(subject);
                                } catch (Exception ex) {
                                    Log.e(TAG, "loadSubjects (BG): Error converting doc " + document.getId(), ex);
                                }
                            }
                        } else {
                            Log.w(TAG, "loadSubjects (BG): No subject documents found for user: " + firebaseUserId);
                        }
                    } else {
                        Log.e(TAG, "loadSubjects (BG): Error getting subject documents for user " + firebaseUserId, task.getException());
                        gmsTaskErrorMessage = "Error loading subjects.";
                    }

                    final String finalGmsTaskErrorMessage = gmsTaskErrorMessage;
                    mainThreadHandler.post(() -> {
                        // mainThreadHandler.post(() -> binding.progressBarSubjects.setVisibility(View.GONE));
                        if (finalGmsTaskErrorMessage != null) {
                            Toast.makeText(MainActivity.this, finalGmsTaskErrorMessage, Toast.LENGTH_SHORT).show();
                        }
                        updateSubjectsUI(processedSubjectsList);
                    });
                });
    }

    private void updateSubjectsUI(List<Subject> newSubjects) {
        subjectList.clear();
        if (newSubjects != null) {
            subjectList.addAll(newSubjects);
        }

        if (subjectsAdapter != null) {
            subjectsAdapter.setSubjects(subjectList);
        } else {
            Log.e(TAG, "updateSubjectsUI: subjectsAdapter is NULL!");
        }

        // Handle empty state for subjects.
        // Message differs if logged in vs. guest (guest handled by loadDummySubjectsForGuest generally).
        if (currentUser != null) { // Logged-in user specific message for no subjects.
            if (subjectList.isEmpty()) {
                binding.textViewNoSubjectsMessage.setText(getString(R.string.no_subjects_placeholder));
                binding.textViewNoSubjectsMessage.setVisibility(View.VISIBLE);
                binding.recyclerViewSubjects.setVisibility(View.GONE);
            } else {
                binding.textViewNoSubjectsMessage.setVisibility(View.GONE);
                binding.recyclerViewSubjects.setVisibility(View.VISIBLE);
            }
        } else if (isGuestMode) { // Guest user, message handled by loadDummySubjectsForGuest.
            // loadDummySubjectsForGuest calls this method with an empty list, then sets its own message.
        }
        Log.d(TAG, "UI updated with " + subjectList.size() + " subjects.");
    }


    /**
     * Sets up the UI to display a placeholder/welcome message for guests (users who skipped login).
     * This is my specific handling for the non-authenticated "guest mode" experience.
     */
    private void loadDummySubjectsForGuest() {
        Log.d(TAG, "loadDummySubjectsForGuest: Setting up UI for guest mode.");
        updateSubjectsUI(new ArrayList<>()); // Clear the adapter for subjects.

        // Display a specific message guiding guest users.
        binding.textViewNoSubjectsMessage.setText(getString(R.string.welcome_guest_no_subjects));
        binding.textViewNoSubjectsMessage.setVisibility(View.VISIBLE);
        binding.recyclerViewSubjects.setVisibility(View.GONE); // Hide the subjects list for guests.

        // Ensure scores section also reflects guest state (empty).
        updateRecentScoresUI(new ArrayList<>());
        Log.d(TAG, "loadDummySubjectsForGuest: Guest UI setup complete.");
    }

    /**
     * Loads recent test scores for the logged-in user. Clears scores if in guest mode.
     */
    private void loadRecentScores() {
        if (currentUser == null) { // This implies guest mode or user just logged out.
            Log.w(TAG, "loadRecentScores: No user logged in or in guest mode. Clearing scores list.");
            updateRecentScoresUI(new ArrayList<>()); // Ensure scores are cleared for guests.
            return;
        }
        // Proceed to load scores only if there's an authenticated user.
        String userId = currentUser.getUid();
        Log.i(TAG, "loadRecentScores: Loading recent scores for user: " + userId);
        // mainThreadHandler.post(() -> binding.progressBarScores.setVisibility(View.VISIBLE));

        mDb.collection(TestActivity.TEST_RESULTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("testMode", TestActivity.MODE_SPECIFIC_TOPIC) // My choice: only show specific topic test results here.
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10) // Show up to 10 most recent scores.
                .get()
                .addOnCompleteListener(executorService, task -> {
                    final List<TestResult> processedScores = new ArrayList<>();
                    String gmsTaskErrorMessage = null;

                    if (task.isSuccessful() && task.getResult() != null) {
                        Log.d(TAG, "loadRecentScores (BG): Fetched " + task.getResult().size() + " raw test results for user " + userId);
                        Map<String, TestResult> latestScoresPerTopicMap = new LinkedHashMap<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                TestResult result = document.toObject(TestResult.class);
                                // My design choice: display only the latest score for each unique topic in recent scores.
                                if (result.getTopicId() != null && !latestScoresPerTopicMap.containsKey(result.getTopicId())) {
                                    latestScoresPerTopicMap.put(result.getTopicId(), result);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "loadRecentScores (BG): Error converting doc to TestResult. Doc ID: " + document.getId(), e);
                            }
                        }
                        processedScores.addAll(latestScoresPerTopicMap.values());
                        Log.i(TAG, "loadRecentScores (BG): Processed into " + processedScores.size() + " unique latest topic scores for user " + userId);
                    } else {
                        Log.e(TAG, "loadRecentScores (BG): Error fetching scores for user " + userId, task.getException());
                        gmsTaskErrorMessage = "Error loading recent scores.";
                    }

                    final String finalGmsTaskErrorMessage = gmsTaskErrorMessage;
                    mainThreadHandler.post(() -> {
                        // mainThreadHandler.post(() -> binding.progressBarScores.setVisibility(View.GONE));
                        if (finalGmsTaskErrorMessage != null) {
                            Toast.makeText(MainActivity.this, finalGmsTaskErrorMessage, Toast.LENGTH_SHORT).show();
                        }
                        updateRecentScoresUI(processedScores);
                    });
                });
    }

    private void updateRecentScoresUI(List<TestResult> newScores) {
        recentScoresList.clear();
        if (newScores != null) {
            recentScoresList.addAll(newScores);
        }

        if (recentScoresAdapter != null) {
            recentScoresAdapter.setScores(recentScoresList);
        } else {
            Log.e(TAG, "updateRecentScoresUI: recentScoresAdapter is null!");
        }

        if (recentScoresList.isEmpty()) {
            binding.textViewRecentScoresHeader.setVisibility(View.GONE);
            binding.textViewNoScoresMessage.setVisibility(View.VISIBLE);
            binding.recyclerViewRecentScores.setVisibility(View.GONE);
        } else {
            binding.textViewRecentScoresHeader.setVisibility(View.VISIBLE);
            binding.textViewNoScoresMessage.setVisibility(View.GONE);
            binding.recyclerViewRecentScores.setVisibility(View.VISIBLE);
        }
        Log.d(TAG, "UI updated with " + recentScoresList.size() + " recent scores.");
    }


    private void goToLoginActivity() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onSubjectClick(Subject subject) {
        // This action is generally for logged-in users to view details of their subjects.
        // Guest users won't have subjects to click from Firebase.
        if (currentUser == null && isGuestMode) {
            Toast.makeText(this, "Please log in or sign up to manage subjects and topics.", Toast.LENGTH_LONG).show();
            return;
        } else if (currentUser == null && !isGuestMode) {
            // Should not happen if initial checks are correct
            goToLoginActivity();
            return;
        }

        Log.i(TAG, "onSubjectClick: Subject '" + subject.getTitle() + "' (ID: " + subject.getDocumentId() + ") clicked. Navigating to TopicListActivity.");
        Intent intent = new Intent(this, TopicListActivity.class);
        intent.putExtra(TopicListActivity.EXTRA_SUBJECT_ID, subject.getDocumentId());
        intent.putExtra(TopicListActivity.EXTRA_SUBJECT_TITLE, subject.getTitle());
        if (topicListLauncher != null) {
            topicListLauncher.launch(intent);
        } else {
            Log.e(TAG, "topicListLauncher is null! Cannot open subject details for result.");
            Toast.makeText(this, "Error opening subject.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Called when the activity is becoming visible.
     * My design includes re-checking authentication status and refreshing data for logged-in users
     * or ensuring guest UI is correctly displayed if in guest mode.
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: MainActivity starting/restarting. Current Guest Mode: " + isGuestMode);
        // isGuestMode flag is from the initial intent. Re-check currentUser.
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null && !isGuestMode) {
            // If not in guest mode and user becomes null (e.g., session expired), redirect.
            Log.w(TAG, "onStart: User is null AND not in guest mode. Redirecting to LoginActivity.");
            goToLoginActivity();
            return; // Important to return to prevent further execution.
        }

        // If user is logged in, refresh their data.
        // If user is null but in guest mode, setupUIBasedOnLoginState will handle guest display.
        if (currentUser != null) {
            Log.d(TAG, "onStart: User " + currentUser.getUid() + " is authenticated. Refreshing data.");
            refreshCurrentUserSubjects(); // This also re-checks currentUser internally
            loadRecentScores();
        } else if (isGuestMode) {
            Log.d(TAG, "onStart: In guest mode (currentUser is null). Ensuring guest UI is displayed via setupUIBasedOnLoginState.");
            setupUIBasedOnLoginState(); // This will call loadDummySubjectsForGuest and clear scores.
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     * Similar to onStart, I re-check authentication and refresh data for logged-in users
     * or ensure guest UI is correctly displayed. This ensures the screen is up-to-date.
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: MainActivity resuming. Current Guest Mode: " + isGuestMode);
        currentUser = mAuth.getCurrentUser(); // Re-check authentication status.

        if (currentUser == null && !isGuestMode) {
            Log.w(TAG, "onResume: User is null AND not in guest mode. Redirecting to LoginActivity.");
            goToLoginActivity();
            return;
        }

        if (currentUser != null) {
            Log.d(TAG, "onResume: User " + currentUser.getUid() + " is authenticated. Refreshing data.");
            refreshCurrentUserSubjects();
            loadRecentScores();
        } else if (isGuestMode) {
            Log.d(TAG, "onResume: In guest mode (currentUser is null). Ensuring guest UI via setupUIBasedOnLoginState.");
            setupUIBasedOnLoginState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "MainActivity: ExecutorService shutdown.");
        }
    }
}