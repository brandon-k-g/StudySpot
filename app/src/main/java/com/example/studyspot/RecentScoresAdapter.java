package com.example.studyspot;

import android.content.Context;
import android.text.format.DateUtils; // For formatting timestamps into relative time (e.g., "5 minutes ago").
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale; // For locale-specific string formatting, ensuring scores are displayed correctly.

/**
 * RecyclerView Adapter for displaying a list of {@link TestResult} objects,
 * representing recent scores achieved by the user.
 * This adapter is responsible for creating view holders and binding test result data
 * to the views, including formatting scores and timestamps for user-friendly presentation.
 * My design focuses on clearly showing the subject/topic, the score, and when the test was taken.
 */
public class RecentScoresAdapter extends RecyclerView.Adapter<RecentScoresAdapter.ScoreViewHolder> {

    // The list of TestResult objects that this adapter will display.
    private List<TestResult> scoreList;
    // Context is needed for layout inflation and accessing string resources.
    private Context context;

    /**
     * Constructor for the RecentScoresAdapter.
     * @param context The context of the calling activity (e.g., MainActivity).
     * @param scoreList The initial list of test results to display.
     */
    public RecentScoresAdapter(Context context, List<TestResult> scoreList) {
        this.context = context;
        this.scoreList = scoreList; // Store the provided list of scores.
    }

    /**
     * Called when RecyclerView needs a new {@link ScoreViewHolder} to represent an item.
     * My implementation inflates the `item_recent_score.xml` layout for each score entry.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new ScoreViewHolder that holds the View for each recent score item.
     */
    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom layout defined in R.layout.item_recent_score.
        View view = LayoutInflater.from(context).inflate(R.layout.item_recent_score, parent, false);
        return new ScoreViewHolder(view); // Create and return a new ViewHolder instance.
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the {@link ScoreViewHolder#itemView}
     * to reflect the test result at the given position.
     * My implementation focuses on formatting the score, subject/topic titles, and timestamp
     * into a user-friendly display.
     * @param holder The ScoreViewHolder which should be updated.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        TestResult result = scoreList.get(position); // Get the TestResult for the current item.

        // Combine Subject and Topic titles for a clear display.
        // I've included null checks and use a default string resource for unknown subject names.
        String subjectTitle = result.getSubjectTitle() != null ? result.getSubjectTitle() : context.getString(R.string.unknown_subject_name);
        String topicTitle = result.getTopicTitle() != null ? result.getTopicTitle() : "Unknown Topic"; // Default if topic title is missing.
        String combinedTitle = subjectTitle + " - " + topicTitle;
        holder.combinedSubjectTopicTitle.setText(combinedTitle);

        // Format the score string to show correct answers, total attempts, and percentage.
        // Using String.format with Locale.getDefault() for proper decimal formatting.
        String scoreText = String.format(Locale.getDefault(), "Score: %d/%d (%.1f%%)",
                result.getScoreCorrect(), result.getCardsAttempted(), result.getPercentage());
        holder.scoreValue.setText(scoreText);

        // Format the date of the test result into a relative time string (e.g., "5 min. ago").
        // This is a UX design choice for a more intuitive display of when the test was taken.
        if (result.getTimestamp() != null) { // The getTimestamp() method returns a java.util.Date object.
            long timeInMillis = result.getTimestamp().getTime(); // Convert the Date object to milliseconds.
            // Use DateUtils to get a human-readable relative time span.
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    timeInMillis,                  // Time of the event.
                    System.currentTimeMillis(),    // Current time.
                    DateUtils.MINUTE_IN_MILLIS,    // Minimum resolution to display (e.g., "0 min. ago").
                    DateUtils.FORMAT_ABBREV_RELATIVE); // Abbreviated format (e.g., "min." instead of "minutes").
            holder.scoreDate.setText(relativeTime);
        } else {
            // Fallback if the timestamp is somehow null.
            holder.scoreDate.setText(context.getString(R.string.date_unknown));
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of test results in {@link #scoreList}.
     */
    @Override
    public int getItemCount() {
        // Return 0 if the list is null to prevent potential NullPointerExceptions.
        return scoreList != null ? scoreList.size() : 0;
    }

    /**
     * Updates the list of test results displayed by the adapter.
     * This method replaces the existing internal list with the new list and
     * then notifies the RecyclerView that the data set has changed, prompting a UI refresh.
     * For future optimization, especially with frequently changing or large lists,
     * I would implement `DiffUtil` to calculate and apply more granular updates.
     * @param newScores The new list of {@link TestResult} objects to display.
     */
    public void setScores(List<TestResult> newScores) {
        this.scoreList = newScores; // Replace the internal list with the new one.
        // Notify any registered observers (like the RecyclerView) that the data set has changed.
        // This will trigger a re-layout and re-binding of items.
        notifyDataSetChanged();
    }

    /**
     * ViewHolder class for recent score items.
     * This class implements the ViewHolder pattern, caching references to the views
     * within each item layout (defined in `item_recent_score.xml`) for efficient scrolling.
     * My item layout is designed to show the combined subject/topic, the score value, and the date.
     */
    static class ScoreViewHolder extends RecyclerView.ViewHolder {
        // UI elements within each recent score item.
        TextView combinedSubjectTopicTitle;
        TextView scoreValue;
        TextView scoreDate;

        /**
         * Constructor for the ScoreViewHolder.
         * @param itemView The View inflated from `item_recent_score.xml`.
         */
        public ScoreViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find and cache references to the views by their IDs from the item layout.
            // It's crucial that these IDs match those defined in R.layout.item_recent_score.
            combinedSubjectTopicTitle = itemView.findViewById(R.id.textViewCombinedSubjectTopicTitle);
            scoreValue = itemView.findViewById(R.id.textViewRecentScoreValue);
            scoreDate = itemView.findViewById(R.id.textViewRecentScoreDate);
        }
    }
}