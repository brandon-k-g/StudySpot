package com.example.studyspot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.studyspot.databinding.ActivityAddEditFlashcardBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Activity for creating a new flashcard or editing an existing one.
 * This activity handles user input for question and answer, topic selection (if applicable),
 * and interaction with Firebase Firestore for data persistence. [cite: 102]
 * It also includes a feature to generate flashcard content using the Gemini API.
 */
public class AddEditFlashcardActivity extends AppCompatActivity {
    private static final String TAG = "AddEditFlashcardActivity";
    public static final String EXTRA_TOPIC_ID_FOR_FLASHCARD = "com.example.studyspot.EXTRA_TOPIC_ID_FOR_FLASHCARD";
    // This key provides the Subject ID, used for context and data organization.
    public static final String EXTRA_SUBJECT_ID_FOR_FLASHCARD = "com.example.studyspot.EXTRA_SUBJECT_ID_FOR_FLASHCARD";
    // This key provides the Topic Title, used for display and AI prompt context.
    public static final String EXTRA_TOPIC_TITLE_FOR_FLASHCARD = "com.example.studyspot.EXTRA_TOPIC_TITLE_FOR_FLASHCARD";
    // This key provides the Subject Title, primarily for AI generation context.
    public static final String EXTRA_SUBJECT_TITLE_FOR_FLASHCARD_CONTEXT = "com.example.studyspot.EXTRA_SUBJECT_TITLE_FOR_FLASHCARD_CONTEXT";
    public static final String EXTRA_EDIT_FLASHCARD_ID = "com.example.studyspot.EXTRA_EDIT_FLASHCARD_ID";
    public static final String EXTRA_CURRENT_QUESTION = "com.example.studyspot.EXTRA_CURRENT_QUESTION";
    public static final String EXTRA_CURRENT_ANSWER = "com.example.studyspot.EXTRA_CURRENT_ANSWER";
    public static final int RESULT_FLASHCARD_DELETED = 4;
    private ActivityAddEditFlashcardBinding binding;
    private FirebaseFirestore mDb;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private String currentTopicId;
    private String currentSubjectId;
    private String currentTopicTitle;
    private String currentSubjectTitle;
    private List<Topic> topicsForCurrentSubjectList;
    private ArrayAdapter<String> topicSpinnerAdapter;
    private List<String> topicTitlesForSpinner;
    private boolean isEditMode = false;
    private String editingFlashcardId;
    private OkHttpClient httpClient;
    private ExecutorService executorService;
    private Handler mainThreadHandler;
    private static final String GEMINI_API_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=";
    private String geminiApiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using view binding.
        binding = ActivityAddEditFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Firestore and Auth services. This is part of my data persistence strategy. [cite: 102]
        mDb = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Initialize components for Gemini API calls.
        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor(); // Using a single thread executor for sequential API calls if needed.
        mainThreadHandler = new Handler(Looper.getMainLooper()); // Handler for UI updates from background threads.

        geminiApiKey = BuildConfig.GEMINI_API_KEY;
        Toast.makeText(this, getString(R.string.ai_api_key_missing) + " Check hardcoded value.", Toast.LENGTH_LONG).show();

        if (currentUser == null) {
            Log.e(TAG, "User not logged in. Finishing activity as user context is essential.");
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_LONG).show();
            finish(); // Close the activity.
            return; // Stop further execution in onCreate.
        }

        // Initialize lists for the topic spinner.
        topicsForCurrentSubjectList = new ArrayList<>();
        topicTitlesForSpinner = new ArrayList<>();
        // Initialize the ArrayAdapter for the topic spinner.
        topicSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, topicTitlesForSpinner);

        // Setup toolbar, spinner, and click listeners for UI elements.
        setupToolbar();
        setupTopicSpinner(); // Sets up the adapter and item click listener for topic selection.
        setupClickListeners(); // Assigns actions to buttons like save, cancel, AI generate.

        // Retrieve data passed via Intent.
        Intent intent = getIntent();
        currentSubjectId = intent.getStringExtra(EXTRA_SUBJECT_ID_FOR_FLASHCARD);
        currentSubjectTitle = intent.getStringExtra(EXTRA_SUBJECT_TITLE_FOR_FLASHCARD_CONTEXT);


        // Subject ID is critical for associating the flashcard correctly.
        if (currentSubjectId == null || currentSubjectId.isEmpty()){
            Log.e(TAG, "Critical: Subject ID not passed to AddEditFlashcardActivity. Finishing.");
            Toast.makeText(this, "Error: Subject context missing. Cannot proceed.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Check if the activity is launched in "Edit Mode".
        // This design choice allows reusing the same activity for both adding and editing. [cite: 131]
        if (intent.hasExtra(EXTRA_EDIT_FLASHCARD_ID)) {
            isEditMode = true;
            editingFlashcardId = intent.getStringExtra(EXTRA_EDIT_FLASHCARD_ID);
            // Topic ID and Title are also expected when editing.
            currentTopicId = intent.getStringExtra(EXTRA_TOPIC_ID_FOR_FLASHCARD);
            currentTopicTitle = intent.getStringExtra(EXTRA_TOPIC_TITLE_FOR_FLASHCARD);

            // Pre-fill the input fields with existing flashcard data.
            binding.editTextFlashcardQuestion.setText(intent.getStringExtra(EXTRA_CURRENT_QUESTION));
            binding.editTextFlashcardAnswer.setText(intent.getStringExtra(EXTRA_CURRENT_ANSWER));
            // Change button text and activity title to reflect "Edit Mode".
            binding.buttonCreateCard.setText(getString(R.string.update_card_button));
            if (binding.toolbarAddFlashcard != null) {
                binding.toolbarAddFlashcard.setTitle(getString(R.string.title_activity_edit_flashcard));
            }
            // Make the delete button visible and set its click listener.
            binding.buttonDeleteFlashcard.setVisibility(View.VISIBLE);
            binding.buttonDeleteFlashcard.setOnClickListener(v -> confirmDeleteFlashcard());

            // In edit mode, the topic cannot be changed, so disable the spinner.
            binding.textInputLayoutTopicSpinner.setEnabled(false);
            binding.autoCompleteTextViewTopic.setEnabled(false);
            if (currentTopicTitle != null) {
                // Display the current topic title.
                binding.autoCompleteTextViewTopic.setText(currentTopicTitle, false);
            }
        } else { // This is "Add New Flashcard" mode.
            isEditMode = false;
            // Retrieve Topic ID and Title if passed (e.g., adding from a specific topic).
            currentTopicId = intent.getStringExtra(EXTRA_TOPIC_ID_FOR_FLASHCARD);
            currentTopicTitle = intent.getStringExtra(EXTRA_TOPIC_TITLE_FOR_FLASHCARD);

            // Set button text and activity title for "Add Mode".
            binding.buttonCreateCard.setText(getString(R.string.create_card_button));
            if (binding.toolbarAddFlashcard != null) {
                binding.toolbarAddFlashcard.setTitle(getString(R.string.title_activity_add_flashcard));
            }
            // Hide the delete button as it's not applicable for new flashcards.
            binding.buttonDeleteFlashcard.setVisibility(View.GONE);

            // If a topic is pre-selected (e.g., "Add flashcard" from a specific topic's screen),
            // display it and disable the spinner.
            if (currentTopicId != null && currentTopicTitle != null) {
                binding.autoCompleteTextViewTopic.setText(currentTopicTitle, false);
                binding.textInputLayoutTopicSpinner.setEnabled(false);
                binding.autoCompleteTextViewTopic.setEnabled(false);
            } else {
                // If no topic is pre-selected, enable the spinner and load available topics.
                binding.textInputLayoutTopicSpinner.setEnabled(true);
                binding.autoCompleteTextViewTopic.setEnabled(true);
                loadTopicsForSpinner(); // Fetch topics for the current subject.
            }
        }
        // Log initial state for debugging and understanding flow.
        Log.d(TAG, "onCreateEnd: EditMode=" + isEditMode + ", FlashcardID=" + editingFlashcardId +
                ", SubjectID=" + currentSubjectId + ", TopicID=" + currentTopicId + ", TopicTitle=" + currentTopicTitle +
                ", SubjectTitleForContext=" + currentSubjectTitle);
    }

    /**
     * Sets up the toolbar for the activity, including the title and back navigation.
     * This is part of my standard UI setup for activities. [cite: 131]
     */
    private void setupToolbar() {
        setSupportActionBar(binding.toolbarAddFlashcard);
        // Enable the Up button for navigation.
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    /**
     * Initializes click listeners for various buttons in the activity.
     * This centralizes UI interaction logic. [cite: 131]
     */
    private void setupClickListeners() {
        // Listener for the "Create" or "Update" button.
        binding.buttonCreateCard.setOnClickListener(v -> saveOrUpdateFlashcard());
        // Listener for the "Cancel" button.
        binding.buttonCancelFlashcard.setOnClickListener(v -> {
            Log.d(TAG, "Cancel button clicked. Returning to previous screen.");
            setResult(Activity.RESULT_CANCELED); // Indicate cancellation to the calling activity.
            finish(); // Close this activity.
        });
        // Listener for the AI Generate button.
        binding.buttonAIGenerate.setOnClickListener(v -> showAiGenerateDialog());
        // Placeholder listeners for image icons (feature not implemented yet).
        // I've included these as potential future enhancements.
        binding.textInputLayoutFlashcardQuestion.setEndIconOnClickListener(v -> Toast.makeText(this, "Add image to question (Future Feature)", Toast.LENGTH_SHORT).show());
        binding.textInputLayoutFlashcardAnswer.setEndIconOnClickListener(v -> Toast.makeText(this, "Add image to answer (Future Feature)", Toast.LENGTH_SHORT).show());
    }

    /**
     * Displays a dialog for the user to provide additional context for AI flashcard generation.
     * This method handles the UI setup for the AI generation feature.
     */
    private void showAiGenerateDialog() {
        // Verify API key before proceeding with AI generation.
        if (geminiApiKey == null || geminiApiKey.isEmpty() || "YOUR_ACTUAL_GEMINI_API_KEY_HERE".equals(geminiApiKey)) {
            Toast.makeText(this, getString(R.string.ai_api_key_missing) + " Please set it correctly in the application code.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "showAiGenerateDialog: Gemini API Key is not configured. AI generation aborted.");
            return;
        }

        // Inflate the custom dialog layout.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_generate_flashcards, null);
        builder.setView(dialogView);
        builder.setTitle(getString(R.string.ai_generate_dialog_title)); // Set dialog title.

        // Get references to views within the dialog.
        EditText editTextAiContext = dialogView.findViewById(R.id.editTextAiContext);
        ProgressBar progressBarAiGenerate = dialogView.findViewById(R.id.progressBarAiGenerate);

        // Set dialog buttons. The positive button's listener is overridden later for validation.
        builder.setPositiveButton(getString(R.string.ai_generate_button_text), null);
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        // Override the positive button's click listener to handle context validation and API call.
        // This approach allows preventing dialog dismissal if validation fails.
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String additionalContext = editTextAiContext.getText().toString().trim();
            String effectiveTopicTitle = currentTopicTitle; // Use the existing topic title if available.

            // Require either a pre-set topic or user-provided context.
            if (TextUtils.isEmpty(additionalContext) && (effectiveTopicTitle == null || effectiveTopicTitle.isEmpty())) {
                Toast.makeText(AddEditFlashcardActivity.this, getString(R.string.ai_context_or_topic_needed), Toast.LENGTH_LONG).show();
                return; // Prevent API call if no context is available.
            }

            // Show progress bar and disable buttons during API call.
            progressBarAiGenerate.setVisibility(View.VISIBLE);
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            int numberOfCards = 1; // Currently, I'm designing it to generate one card at a time via this dialog.
            generateFlashcardsWithGemini(additionalContext, numberOfCards, progressBarAiGenerate, alertDialog, effectiveTopicTitle);
        });
    }


    /**
     * Calls the Gemini API to generate flashcard content based on provided context.
     * This method constructs the prompt, makes the network request, and handles the response.
     * This is a key feature for quick flashcard creation. [cite: 102]
     * @param userContext Additional context provided by the user.
     * @param numberOfCards The number of flashcards to request (currently fixed at 1 from dialog).
     * @param progressBar The ProgressBar to show loading state in the dialog.
     * @param dialog The AlertDialog to interact with (e.g., re-enable buttons, dismiss).
     * @param topicForPrompt The primary topic to use for the generation prompt.
     */
    private void generateFlashcardsWithGemini(String userContext, int numberOfCards, ProgressBar progressBar, AlertDialog dialog, String topicForPrompt) {
        // Determine the main context for the prompt, prioritizing subject and topic titles.
        String contextForPrompt = (topicForPrompt != null && !topicForPrompt.isEmpty()) ? topicForPrompt : "General Knowledge";
        if (currentSubjectTitle != null && !currentSubjectTitle.isEmpty()) {
            contextForPrompt = currentSubjectTitle + " - " + contextForPrompt;
        }

        // Construct the detailed prompt for the Gemini API.
        // My prompt engineering aims to get structured Question/Answer pairs.
        String constructedPrompt = "You are a helpful assistant that creates educational flashcards.\n" +
                "Generate exactly " + numberOfCards + " flashcard(s) for the topic or subject: \"" + contextForPrompt + "\".\n";
        if (!userContext.isEmpty()) {
            constructedPrompt += "Consider this additional context or specific terms: \"" + userContext + "\".\n";
        }
        constructedPrompt += "For each flashcard, provide a clear 'Question:' and a concise 'Answer:'.\n" +
                "Format each flashcard strictly as:\n" +
                "Question: [Your Question Here]\n" +
                "Answer: [Your Answer Here]\n" +
                // This separator is crucial for when I might want to generate multiple cards in one go in the future.
                "If generating multiple flashcards, ensure each flashcard (Question and Answer pair) is separated by exactly '---FLASHCARD_SEPARATOR---'. Do not use this separator anywhere else within a single flashcard's question or answer.";

        final String finalPrompt = constructedPrompt;
        Log.d(TAG, "Gemini Prompt to be sent: " + finalPrompt);

        // Execute the network request on a background thread.
        executorService.execute(() -> {
            try {
                // Prepare the JSON request body as per Gemini API specifications.
                JSONObject jsonBody = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray partsArray = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", finalPrompt); // The main prompt text.
                partsArray.put(part);
                content.put("parts", partsArray);
                contentsArray.put(content);
                jsonBody.put("contents", contentsArray);

                // Create the HTTP request body.
                RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
                // Build the HTTP POST request.
                Request request = new Request.Builder()
                        .url(GEMINI_API_ENDPOINT_BASE + geminiApiKey) // Append my API key to the base URL.
                        .post(body)
                        .addHeader("Content-Type", "application/json") // Set content type header.
                        .build();

                // Enqueue the asynchronous API call.
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Gemini API call failed: ", e);
                        // Post UI updates back to the main thread.
                        mainThreadHandler.post(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (dialog.isShowing()) { // Re-enable dialog buttons on failure.
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                            }
                            Toast.makeText(AddEditFlashcardActivity.this, getString(R.string.ai_generation_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        final String responseBody = response.body() != null ? response.body().string() : null;
                        Log.d(TAG, "Gemini API Response Code: " + response.code());
                        Log.d(TAG, "Raw Gemini API Response Body: " + responseBody);

                        // Post UI updates and response processing back to the main thread.
                        mainThreadHandler.post(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (dialog.isShowing()) { // Re-enable dialog buttons.
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                            }

                            if (response.isSuccessful() && responseBody != null) {
                                try {
                                    // Parse the JSON response from Gemini.
                                    JSONObject jsonResponse = new JSONObject(responseBody);
                                    // Navigate through the JSON structure to find the generated text.
                                    // This structure is specific to the Gemini API's response format.
                                    JSONArray candidates = jsonResponse.optJSONArray("candidates");
                                    if (candidates != null && candidates.length() > 0) {
                                        JSONObject firstCandidate = candidates.getJSONObject(0);
                                        JSONObject content = firstCandidate.optJSONObject("content");
                                        if (content != null) {
                                            JSONArray parts = content.optJSONArray("parts");
                                            if (parts != null && parts.length() > 0) {
                                                String generatedText = parts.getJSONObject(0).optString("text", "");
                                                // Process the successfully extracted text.
                                                parseAndDisplayGeneratedFlashcards(generatedText, dialog);
                                                return; // Successfully handled.
                                            }
                                        }
                                    }
                                    // If the expected structure isn't found or content is missing.
                                    Log.e(TAG, "Unexpected Gemini response structure or empty content. Response: " + responseBody);
                                    Toast.makeText(AddEditFlashcardActivity.this, getString(R.string.ai_empty_response), Toast.LENGTH_LONG).show();

                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing Gemini response JSON: ", e);
                                    Toast.makeText(AddEditFlashcardActivity.this, getString(R.string.ai_error_processing), Toast.LENGTH_LONG).show();
                                }
                            } else {
                                // Handle API errors (non-successful HTTP status codes).
                                Log.e(TAG, "Gemini API returned an error: " + response.code() + " - Body: " + responseBody);
                                String errorMsg = getString(R.string.ai_generation_failed);
                                // Attempt to parse a more specific error message from the API response.
                                if (responseBody != null) {
                                    try {
                                        JSONObject errorJson = new JSONObject(responseBody);
                                        JSONObject error = errorJson.optJSONObject("error");
                                        if (error != null) {
                                            errorMsg += " " + error.optString("message", "Unknown error details from API.");
                                        }
                                    } catch (Exception ignored) {
                                        // If error parsing fails, use the generic message.
                                        Log.w(TAG, "Could not parse error message from API error response body.");
                                    }
                                }
                                Toast.makeText(AddEditFlashcardActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });

            } catch (Exception e) {
                // Handle exceptions during request preparation (e.g., JSONException).
                Log.e(TAG, "Error preparing Gemini request: ", e);
                mainThreadHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (dialog.isShowing()) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    }
                    Toast.makeText(AddEditFlashcardActivity.this, "Error setting up AI request. Please check logs.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Parses the text generated by the Gemini API and populates the question/answer fields.
     * Assumes the generated text follows the "Question: ... Answer: ..." format.
     * @param generatedText The raw text output from the Gemini API.
     * @param dialogToDismiss The AI generation dialog, to be dismissed upon successful parsing.
     */
    private void parseAndDisplayGeneratedFlashcards(String generatedText, AlertDialog dialogToDismiss) {
        if (TextUtils.isEmpty(generatedText)) {
            Toast.makeText(this, getString(R.string.ai_empty_response), Toast.LENGTH_SHORT).show();
            return;
        }

        // This logic is designed to handle one or more flashcards if the API returns them with the separator.
        // Currently, I only use the first one from the dialog context.
        String[] flashcardStrings = generatedText.split("---FLASHCARD_SEPARATOR---");
        ArrayList<Map<String, String>> parsedFlashcards = new ArrayList<>();

        for (String cardStr : flashcardStrings) {
            cardStr = cardStr.trim();
            if (cardStr.isEmpty()) continue; // Skip empty segments.

            String question = null;
            String answer = null;

            // Split the card segment by lines to find "Question:" and "Answer:" prefixes.
            // This parsing is based on the prompt I designed.
            String[] lines = cardStr.split("\\r?\\n"); // Handles both Windows and Unix line endings.
            for (String line : lines) {
                line = line.trim();
                if (line.toLowerCase().startsWith("question:")) {
                    question = line.substring("question:".length()).trim();
                } else if (line.toLowerCase().startsWith("answer:")) {
                    answer = line.substring("answer:".length()).trim();
                }
            }

            // If both question and answer were successfully parsed, add to list.
            if (question != null && !question.isEmpty() && answer != null && !answer.isEmpty()) {
                Map<String, String> flashcard = new HashMap<>();
                flashcard.put("question", question);
                flashcard.put("answer", answer);
                parsedFlashcards.add(flashcard);
            } else {
                Log.w(TAG, "Could not parse Question/Answer from AI-generated segment: '" + cardStr + "'");
            }
        }

        if (!parsedFlashcards.isEmpty()) {
            // For this implementation, I'm taking the first successfully parsed flashcard.
            Map<String, String> firstFlashcard = parsedFlashcards.get(0);
            binding.editTextFlashcardQuestion.setText(firstFlashcard.get("question"));
            binding.editTextFlashcardAnswer.setText(firstFlashcard.get("answer"));
            Toast.makeText(this, getString(R.string.ai_generated_cards_loaded_toast, parsedFlashcards.size()), Toast.LENGTH_LONG).show();

            // Dismiss the AI generation dialog.
            if (dialogToDismiss != null && dialogToDismiss.isShowing()) {
                dialogToDismiss.dismiss();
            }
        } else {
            // If no flashcards could be parsed from the AI's response.
            Toast.makeText(this, getString(R.string.ai_parse_fail), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Failed to parse any flashcards from AI response: " + generatedText);
        }
    }

    /**
     * Inflates the options menu for the activity (e.g., the Save action).
     * Standard Android pattern for adding actions to the toolbar. [cite: 131]
     * @param menu The options menu in which items are placed.
     * @return You must return true for the menu to be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_save, menu); // My save menu resource.
        return true;
    }

    /**
     * Handles action bar item clicks.
     * Specifically, manages the "home" (back) button and "save" action.
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to proceed,
     * true to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // Handle the back arrow click in the toolbar.
            setResult(Activity.RESULT_CANCELED); // Indicate no changes were saved explicitly via save action.
            finish(); // Close the current activity.
            return true;
        } else if (itemId == R.id.action_save) {
            // Handle the save action from the toolbar.
            saveOrUpdateFlashcard();
            return true;
        }
        return super.onOptionsItemSelected(item); // Default handling for other items.
    }

    /**
     * Manages the visibility of the loading indicator and enables/disables UI elements.
     * This provides visual feedback to the user during long operations like saving or loading. [cite: 131]
     * @param isLoading True if loading is in progress, false otherwise.
     */
    private void showLoading(boolean isLoading) {
        binding.progressBarAddFlashcard.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // Disable interactive elements during loading to prevent unintended actions.
        binding.buttonCreateCard.setEnabled(!isLoading);
        binding.buttonCancelFlashcard.setEnabled(!isLoading);
        binding.buttonAIGenerate.setEnabled(!isLoading);
        binding.editTextFlashcardQuestion.setEnabled(!isLoading);
        binding.editTextFlashcardAnswer.setEnabled(!isLoading);
        // Only toggle enabled state if the spinner itself is meant to be enabled (not in edit mode or pre-selected topic).
        if (binding.autoCompleteTextViewTopic.isEnabled()) {
            binding.autoCompleteTextViewTopic.setEnabled(!isLoading);
        }
        if (isEditMode) { // Delete button is only relevant in edit mode.
            binding.buttonDeleteFlashcard.setEnabled(!isLoading);
        }
        invalidateOptionsMenu(); // To potentially disable save action in menu while loading.
    }

    /**
     * Sets up the topic selection spinner (AutoCompleteTextView).
     * This includes setting its adapter and an item click listener to capture the selected topic.
     * This is part of the dynamic UI for selecting the flashcard's category. [cite: 131]
     */
    private void setupTopicSpinner() {
        binding.autoCompleteTextViewTopic.setAdapter(topicSpinnerAdapter);
        // This listener is only active if the spinner is enabled (i.e., not in edit mode with a fixed topic).
        if (binding.autoCompleteTextViewTopic.isEnabled()) {
            binding.autoCompleteTextViewTopic.setOnItemClickListener((parent, view, position, id) -> {
                // Ensure selection is valid.
                if (position >= 0 && position < topicsForCurrentSubjectList.size()) {
                    Topic selectedTopic = topicsForCurrentSubjectList.get(position);
                    this.currentTopicId = selectedTopic.getDocumentId(); // Store the ID of the selected topic.
                    this.currentTopicTitle = selectedTopic.getTitle(); // Store the title for display/context.
                    Log.d(TAG, "Spinner: Topic selected by user: " + this.currentTopicTitle + " (ID: " + this.currentTopicId + ")");
                }
            });
        }
    }

    /**
     * Loads topics from Firestore for the current subject to populate the spinner.
     * This involves an asynchronous Firestore query. Data persistence handled here. [cite: 102]
     * This method is called when the user needs to select a topic (e.g., in "add mode" without a pre-selected topic).
     */
    private void loadTopicsForSpinner() {
        // Basic checks before attempting to load.
        if (currentSubjectId == null || currentSubjectId.isEmpty() || currentUser == null) {
            Log.w(TAG, "Cannot load topics for spinner: Subject ID or user is null.");
            processLoadedTopicsForSpinner(new ArrayList<>()); // Process with an empty list to clear spinner.
            return;
        }
        showLoading(true); // Show loading indicator.
        // Query Firestore for topics belonging to the current subject, ordered by creation time.
        // This is part of my data retrieval strategy for populating UI elements. [cite: 131]
        mDb.collection(TopicListActivity.TOPICS_COLLECTION)
                .whereEqualTo("subjectId", currentSubjectId) // Filter by subject.
                .orderBy("timestamp", Query.Direction.ASCENDING) // Order by timestamp.
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false); // Hide loading indicator.
                    List<Topic> topics = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Topic topic = document.toObject(Topic.class); // Convert Firestore document to Topic object.
                                topic.setDocumentId(document.getId()); // Manually set the document ID.
                                topics.add(topic);
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting Firestore document to Topic object: " + document.getId(), e);
                            }
                        }
                        Log.d(TAG, "Successfully loaded " + topics.size() + " topics for subject " + currentSubjectId);
                    } else {
                        Log.e(TAG, "Error loading topics for spinner from Firestore: ", task.getException());
                        Toast.makeText(this, "Error loading topics.", Toast.LENGTH_SHORT).show();
                    }
                    processLoadedTopicsForSpinner(topics); // Update the spinner with loaded topics.
                });
    }

    /**
     * Processes the list of topics loaded from Firestore and updates the spinner adapter.
     * Also handles pre-selection of a topic if currentTopicId is set.
     * @param loadedTopics The list of Topic objects fetched from Firestore.
     */
    private void processLoadedTopicsForSpinner(List<Topic> loadedTopics) {
        // Clear existing topic data.
        topicsForCurrentSubjectList.clear();
        topicTitlesForSpinner.clear();

        if (loadedTopics != null) {
            topicsForCurrentSubjectList.addAll(loadedTopics);
            for(Topic topic : loadedTopics){
                // Add topic titles to the list used by the adapter.
                topicTitlesForSpinner.add(topic.getTitle() != null ? topic.getTitle() : "Unnamed Topic");
            }
        }
        // Notify the adapter that the data set has changed.
        if (topicSpinnerAdapter != null) topicSpinnerAdapter.notifyDataSetChanged();

        boolean preselected = false;
        // If a currentTopicId exists (e.g., passed via intent or previously selected), try to pre-select it.
        if (currentTopicId != null && !topicsForCurrentSubjectList.isEmpty()) {
            for (int i = 0; i < topicsForCurrentSubjectList.size(); i++) {
                if (topicsForCurrentSubjectList.get(i).getDocumentId().equals(currentTopicId)) {
                    String titleToSet = currentTopicTitle != null ? currentTopicTitle : topicTitlesForSpinner.get(i);
                    binding.autoCompleteTextViewTopic.setText(titleToSet, false); // Set text without filtering.
                    Log.d(TAG, "Pre-selected topic in spinner: " + titleToSet);
                    preselected = true;
                    break;
                }
            }
        }

        // If not in edit mode, spinner is enabled, no topic was pre-selected, and topics exist,
        // then I choose to auto-select the first topic as a default. This is a design choice for usability.
        if (!isEditMode && binding.autoCompleteTextViewTopic.isEnabled() && !preselected && !topicsForCurrentSubjectList.isEmpty()) {
            Topic firstTopic = topicsForCurrentSubjectList.get(0);
            this.currentTopicId = firstTopic.getDocumentId();
            this.currentTopicTitle = firstTopic.getTitle();
            binding.autoCompleteTextViewTopic.setText(this.currentTopicTitle, false);
            Log.d(TAG, "Auto-selected first topic in spinner: " + this.currentTopicTitle);
        } else if (isEditMode && currentTopicTitle != null && !preselected) {
            // Fallback for edit mode if pre-selection by ID failed but title is known (should ideally not happen if data is consistent).
            binding.autoCompleteTextViewTopic.setText(currentTopicTitle, false);
        } else if (topicsForCurrentSubjectList.isEmpty() && !isEditMode && binding.autoCompleteTextViewTopic.isEnabled()) {
            // If no topics are available, clear the selection.
            binding.autoCompleteTextViewTopic.setText("", false);
            this.currentTopicId = null;
            this.currentTopicTitle = null;
            Log.d(TAG, "No topics available for spinner, selection cleared.");
        }
    }

    /**
     * Saves a new flashcard or updates an existing one in Firebase Firestore.
     * This method handles input validation, data preparation, and the Firestore operation.
     * This is the core logic for data persistence of flashcards. [cite: 102, 131]
     */
    private void saveOrUpdateFlashcard() {
        // Ensure user is authenticated.
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to save a flashcard.", Toast.LENGTH_SHORT).show(); return;
        }
        // Validate that a topic is selected. This is a requirement I've set.
        if (currentTopicId == null || currentTopicId.isEmpty()) {
            Toast.makeText(this, "Please select a topic for the flashcard.", Toast.LENGTH_SHORT).show();
            if (binding.autoCompleteTextViewTopic.isEnabled()) binding.textInputLayoutTopicSpinner.setError("Topic is required");
            return;
        } else { binding.textInputLayoutTopicSpinner.setError(null); } // Clear error if topic is selected.

        // Validate that subject context is available.
        if (currentSubjectId == null || currentSubjectId.isEmpty()) {
            Toast.makeText(this, "Error: Subject context is missing. Cannot save.", Toast.LENGTH_SHORT).show(); return;
        }

        // Get question and answer text from input fields.
        String question = Objects.requireNonNull(binding.editTextFlashcardQuestion.getText()).toString().trim();
        String answer = Objects.requireNonNull(binding.editTextFlashcardAnswer.getText()).toString().trim();

        // Validate that question is not empty.
        if (TextUtils.isEmpty(question)) {
            binding.textInputLayoutFlashcardQuestion.setError(getString(R.string.flashcard_question_required));
            binding.editTextFlashcardQuestion.requestFocus(); return;
        } else { binding.textInputLayoutFlashcardQuestion.setError(null); }

        // Validate that answer is not empty.
        if (TextUtils.isEmpty(answer)) {
            binding.textInputLayoutFlashcardAnswer.setError(getString(R.string.flashcard_answer_required));
            binding.editTextFlashcardAnswer.requestFocus(); return;
        } else { binding.textInputLayoutFlashcardAnswer.setError(null); }

        showLoading(true); // Show loading indicator.

        // Prepare flashcard data as a Map for Firestore.
        // This structure defines my flashcard data model in Firestore. [cite: 32, 38]
        Map<String, Object> flashcardData = new HashMap<>();
        flashcardData.put("question", question);
        flashcardData.put("answer", answer);
        flashcardData.put("topicId", currentTopicId);
        flashcardData.put("subjectId", currentSubjectId); // Storing subjectId for broader queries if needed.
        flashcardData.put("timestamp", FieldValue.serverTimestamp()); // For ordering and tracking.
        // flashcardData.put("userId", currentUser.getUid()); // Consider adding userId for ownership if not implicitly handled by Firestore rules.

        if (isEditMode && editingFlashcardId != null) {
            // Update existing flashcard. Using SetOptions.merge() to only update provided fields.
            Log.d(TAG, "Attempting to update flashcard ID: " + editingFlashcardId);
            mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION).document(editingFlashcardId)
                    .set(flashcardData, SetOptions.merge()) // Using merge to avoid overwriting fields not included in flashcardData, if any.
                    .addOnSuccessListener(aVoid -> handleSaveSuccess(true, "Flashcard updated successfully"))
                    .addOnFailureListener(e -> handleSaveFailure(e, "Error updating flashcard"));
        } else {
            // Add new flashcard to the "flashcards" collection.
            Log.d(TAG, "Attempting to create new flashcard for topic ID: " + currentTopicId);
            mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION).add(flashcardData)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "New flashcard added successfully with ID: " + documentReference.getId());
                        // This new flashcard ID could be passed back if needed, but not currently.
                        handleSaveSuccess(false, "Flashcard created successfully");
                    })
                    .addOnFailureListener(e -> handleSaveFailure(e, "Error creating flashcard"));
        }
    }

    /**
     * Handles successful save or update operations.
     * It hides loading, shows a success message, updates topic flashcard count (for new cards),
     * sets the result for the calling activity, and finishes this activity.
     * @param isUpdate True if it was an update operation, false for a new creation.
     * @param message The success message to display.
     */
    private void handleSaveSuccess(boolean isUpdate, String message) {
        showLoading(false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // If a new flashcard was created, I increment the flashcard count on its parent topic.
        // This denormalization helps in quickly displaying counts without extra queries. [cite: 131]
        if (!isUpdate && currentTopicId != null && !currentTopicId.isEmpty()) {
            mDb.collection(TopicListActivity.TOPICS_COLLECTION).document(currentTopicId)
                    .update("flashcardCount", FieldValue.increment(1)) // Atomic increment.
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Successfully incremented flashcard count for topic: " + currentTopicId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to increment flashcard count for topic: " + currentTopicId + ". This might lead to inconsistent counts.", e));
        }
        setResult(Activity.RESULT_OK); // Indicate success to the calling activity.
        finish(); // Close this activity.
    }

    /**
     * Handles failures during save or update operations.
     * It hides loading, logs the error, and shows an error message to the user.
     * @param e The exception that occurred.
     * @param logMessage The message to log for this error.
     */
    private void handleSaveFailure(Exception e, String logMessage) {
        showLoading(false);
        Log.e(TAG, logMessage + ": ", e); // Log detailed error.
        Toast.makeText(this, logMessage + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    /**
     * Displays a confirmation dialog before deleting a flashcard.
     * This is a standard UX practice to prevent accidental deletions. [cite: 131]
     */
    private void confirmDeleteFlashcard() {
        // Ensure that we are in edit mode and have a valid flashcard ID.
        if (!isEditMode || editingFlashcardId == null || editingFlashcardId.isEmpty()) {
            Toast.makeText(this, "No flashcard selected for deletion or not in edit mode.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Use AlertDialog for confirmation.
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_flashcard_title))
                .setMessage(getString(R.string.confirm_delete_flashcard_message))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> deleteFlashcard()) // Proceed with delete.
                .setNegativeButton(getString(R.string.cancel), null) // Do nothing on cancel.
                .setIcon(android.R.drawable.ic_dialog_alert) // Standard alert icon.
                .show();
    }

    /**
     * Deletes the currently selected flashcard from Firestore.
     * Also updates the flashcard count for the associated topic.
     * This handles the data persistence aspect of deletion. [cite: 102]
     */
    private void deleteFlashcard() {
        // Double-check conditions before proceeding with deletion.
        if (editingFlashcardId == null || editingFlashcardId.isEmpty() || currentUser == null) {
            Log.e(TAG, "Cannot delete flashcard: Flashcard ID is missing or user is not authenticated.");
            if(currentUser == null) Toast.makeText(this, "Authentication error. Cannot delete.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Topic ID is needed to decrement the count. If it's missing, log a warning but proceed with flashcard deletion.
        if (currentTopicId == null || currentTopicId.isEmpty()) {
            Log.w(TAG, "Cannot update flashcard count: currentTopicId is missing for the flashcard being deleted (ID: " + editingFlashcardId + "). Flashcard will be deleted, but count won't be decremented.");
        }

        showLoading(true); // Show loading indicator.
        Log.i(TAG, "Attempting to delete flashcard: " + editingFlashcardId + " from topic: " + (currentTopicId != null ? currentTopicId : "UNKNOWN"));
        // Perform the delete operation on Firestore.
        mDb.collection(FlashcardListActivity.FLASHCARDS_COLLECTION).document(editingFlashcardId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "Flashcard deleted successfully from Firestore: " + editingFlashcardId);

                    // If topic ID is available, decrement its flashcard count.
                    // This is another part of my data consistency strategy (denormalization). [cite: 131]
                    if (currentTopicId != null && !currentTopicId.isEmpty()) {
                        mDb.collection(TopicListActivity.TOPICS_COLLECTION).document(currentTopicId)
                                .update("flashcardCount", FieldValue.increment(-1)) // Atomic decrement.
                                .addOnSuccessListener(aVoid_ -> Log.i(TAG, "Successfully decremented flashcard count for topic: " + currentTopicId))
                                .addOnFailureListener(e_ -> Log.e(TAG, "Failed to decrement flashcard count for topic: " + currentTopicId + ". This might lead to inconsistent counts.", e_));
                    } else {
                        // Log if count couldn't be decremented.
                        Log.w(TAG, "Could not decrement flashcard count as currentTopicId was null/empty during deletion of flashcard: " + editingFlashcardId);
                    }
                    showLoading(false);
                    Toast.makeText(AddEditFlashcardActivity.this, getString(R.string.flashcard_deleted_successfully), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_FLASHCARD_DELETED); // Use specific result code for deletion.
                    finish(); // Close activity.
                })
                .addOnFailureListener(e -> {
                    // Handle deletion failure.
                    showLoading(false);
                    Log.e(TAG, "Error deleting flashcard from Firestore: " + editingFlashcardId, e);
                    Toast.makeText(AddEditFlashcardActivity.this, getString(R.string.error_deleting_flashcard) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Called when the activity is being destroyed.
     * Used here to shut down the ExecutorService for the Gemini API calls to free up resources.
     * This is important for preventing resource leaks. [cite: 131]
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the executor service to prevent potential leaks and stop background tasks.
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown(); // Initiates an orderly shutdown.
            Log.d(TAG, "ExecutorService for Gemini API calls has been shut down.");
        }
        // View binding instances are typically nulled out here too, though modern Android handles it well.
        binding = null;
    }
}