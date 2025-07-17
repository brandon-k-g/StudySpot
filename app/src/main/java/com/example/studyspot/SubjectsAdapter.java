package com.example.studyspot; // Ensure this matches your package name

import android.content.Context;
import android.util.Log; // For logging adapter lifecycle and binding events.
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList; // Using ArrayList for the internal list of subjects.
import java.util.List;

/**
 * RecyclerView Adapter for displaying a list of {@link Subject} objects.
 * This adapter is responsible for creating view holders for each subject card,
 * binding subject data (like title, progress, streak) to the views within each card,
 * and handling user clicks on a subject card by delegating to an {@link OnSubjectClickListener}.
 * My design uses an internal list to manage the subjects displayed by the adapter.
 */
public class SubjectsAdapter extends RecyclerView.Adapter<SubjectsAdapter.SubjectViewHolder> {

    // Log tag specific to this adapter for easier debugging of RecyclerView operations.
    private static final String ADAPTER_TAG = "SubjectsAdapter";

    // Internal list to hold the subject data. My design choice to use an internal list (`subjectListInternal`)
    // provides better encapsulation and control over the data displayed by the adapter.
    private List<Subject> subjectListInternal;
    // Context is needed for layout inflation.
    private Context context;
    // Listener instance (typically the hosting Activity, e.g., MainActivity) to handle item click events.
    private OnSubjectClickListener onSubjectClickListener;

    /**
     * Interface definition for a callback to be invoked when a subject item is clicked.
     * This is my chosen mechanism to communicate click events from the adapter back to the
     * hosting Activity, allowing the Activity to respond (e.g., by navigating to a details screen).
     */
    public interface OnSubjectClickListener {
        /**
         * Called when a subject item view is clicked.
         * @param subject The {@link Subject} object that was clicked.
         */
        void onSubjectClick(Subject subject);
    }

    /**
     * Constructor for the SubjectsAdapter.
     * @param context The context of the calling activity, used for layout inflation.
     * @param initialSubjectList The initial list of subjects to display.
     * The adapter creates its own internal copy of this list.
     * @param listener The listener that will handle item click events.
     */
    public SubjectsAdapter(Context context, List<Subject> initialSubjectList, OnSubjectClickListener listener) {
        this.context = context;
        this.onSubjectClickListener = listener;
        this.subjectListInternal = new ArrayList<>(); // Initialize the adapter's own list.
        if (initialSubjectList != null) {
            // Copy items from the initial list to the internal list if provided.
            // This prevents external modifications to initialSubjectList from directly affecting the adapter's data.
            this.subjectListInternal.addAll(initialSubjectList);
        }
        Log.d(ADAPTER_TAG, "Adapter instance created. Initial internal list size: " + this.subjectListInternal.size());
    }

    /**
     * Called when RecyclerView needs a new {@link SubjectViewHolder} of the given type to represent an item.
     * My implementation inflates the `item_subject_card.xml` layout for each subject card.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new SubjectViewHolder that holds the View for each subject item.
     */
    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(ADAPTER_TAG, "onCreateViewHolder: Inflating layout for a new subject card.");
        // Inflate the custom layout (R.layout.item_subject_card) for each subject item.
        View view = LayoutInflater.from(context).inflate(R.layout.item_subject_card, parent, false);
        return new SubjectViewHolder(view); // Create and return a new ViewHolder instance.
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the {@link SubjectViewHolder#itemView} to reflect the subject
     * at the given position. My implementation sets various text fields, progress bar,
     * and manages the visibility of the streak indicator.
     * @param holder The SubjectViewHolder which should be updated.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        // Get the Subject object for the current position using the adapter's internal list.
        Subject subject = subjectListInternal.get(position);
        Log.d(ADAPTER_TAG, "onBindViewHolder: Binding data for position " + position + ", Subject: " + subject.getTitle());

        // Set the subject title, handling null for robustness.
        if (subject.getTitle() != null) {
            holder.textViewSubjectTitle.setText(subject.getTitle());
        } else {
            holder.textViewSubjectTitle.setText(""); // Default to empty if title is null.
        }

        // Set previous result, with "N/A" as a fallback if not available.
        if (subject.getPreviousResult() != null) {
            holder.textViewPreviousResultValue.setText(subject.getPreviousResult());
        } else {
            holder.textViewPreviousResultValue.setText("N/A");
        }

        // Set study time, with "0 hrs" as a fallback.
        if (subject.getStudyTime() != null) {
            holder.textViewStudyTimeValue.setText(subject.getStudyTime());
        } else {
            holder.textViewStudyTimeValue.setText("0 hrs");
        }

        // Set revision progress and flashcard progress.
        holder.progressBarRevision.setProgress(subject.getRevisionProgress());
        // Using the utility method from Subject POJO for formatted flashcard progress.
        holder.textViewFlashcardsValue.setText(subject.getFlashcardsProgressString());
        holder.textViewStreakCount.setText(String.valueOf(subject.getStreakCount()));

        // Manage visibility of the streak indicator based on streakCount.
        // This is a UI design choice to visually represent active streaks.
        if (subject.getStreakCount() > 0) {
            holder.imageViewSubjectStreak.setVisibility(View.VISIBLE);
            holder.textViewStreakCount.setVisibility(View.VISIBLE);
            // If I have a specific flame icon for streaks, I'd set it here.
            // For example: holder.imageViewSubjectStreak.setImageResource(R.drawable.ic_flame_streak_active);
            // The current R.drawable.ic_flame_placeholder needs to exist or this line should be managed.
        } else {
            holder.imageViewSubjectStreak.setVisibility(View.INVISIBLE); // Hide if no streak.
            holder.textViewStreakCount.setVisibility(View.INVISIBLE);
        }

        // Set the click listener for the entire subject card item view.
        // When an item is clicked, I delegate the event to the onSubjectClickListener.
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onSubjectClickListener != null) {
                    // Pass the clicked Subject object to the listener.
                    onSubjectClickListener.onSubjectClick(subject);
                }
            }
        });
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of subjects in {@link #subjectListInternal}.
     */
    @Override
    public int getItemCount() {
        // Use the adapter's internal list for the count.
        int count = subjectListInternal != null ? subjectListInternal.size() : 0;
        Log.d(ADAPTER_TAG, "getItemCount: Returning item count: " + count);
        return count;
    }

    /**
     * Updates the list of subjects displayed by the adapter.
     * This method clears the existing internal list, adds all items from the new list provided
     * by the Activity (e.g., MainActivity), and then notifies the RecyclerView that the
     * data set has changed, triggering a UI refresh.
     * For future enhancements with larger lists or more frequent updates, I would consider
     * using `DiffUtil` for more efficient updates.
     * @param newSubjectListFromActivity The new list of {@link Subject} objects to display.
     */
    public void setSubjects(List<Subject> newSubjectListFromActivity) {
        Log.d(ADAPTER_TAG, "setSubjects: Updating adapter with new list. New list size: " +
                (newSubjectListFromActivity != null ? newSubjectListFromActivity.size() : "null"));
        this.subjectListInternal.clear(); // Clear the current data in the adapter's internal list.
        if (newSubjectListFromActivity != null) {
            this.subjectListInternal.addAll(newSubjectListFromActivity); // Add all items from the new list.
        }
        // Notify any registered observers (like the RecyclerView) that the entire data set has changed.
        // This will cause the RecyclerView to rebind and redraw all visible items.
        notifyDataSetChanged();
        Log.d(ADAPTER_TAG, "Adapter data set changed. Current internal list size: " + this.subjectListInternal.size());
    }

    /**
     * ViewHolder class for subject items.
     * This class implements the ViewHolder pattern, which improves scrolling performance
     * by caching references to the views within each item layout (defined in `item_subject_card.xml`).
     * My item layout is designed to display various details of a subject, including title,
     * progress metrics, and a streak indicator.
     */
    public static class SubjectViewHolder extends RecyclerView.ViewHolder {
        // UI elements within each subject card.
        TextView textViewSubjectTitle, textViewSubjectSubtitle, textViewPreviousResultValue,
                textViewStudyTimeValue, textViewFlashcardsValue, textViewStreakCount;
        ImageView imageViewSubjectStreak;
        ProgressBar progressBarRevision;

        /**
         * Constructor for the SubjectViewHolder.
         * @param itemView The View inflated from `item_subject_card.xml`.
         */
        public SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find and cache references to the views by their IDs from the item layout.
            // It's crucial that these IDs match those defined in R.layout.item_subject_card.
            textViewSubjectTitle = itemView.findViewById(R.id.textViewSubjectTitle);
            // textViewSubjectSubtitle = itemView.findViewById(R.id.textViewSubjectSubtitle); // This seems to be in the declaration but not used in onBindViewHolder explicitly. If it's in XML, it should be initialized.
            textViewPreviousResultValue = itemView.findViewById(R.id.textViewPreviousResultValue);
            textViewStudyTimeValue = itemView.findViewById(R.id.textViewStudyTimeValue);
            progressBarRevision = itemView.findViewById(R.id.progressBarRevision);
            textViewFlashcardsValue = itemView.findViewById(R.id.textViewFlashcardsValue);
            imageViewSubjectStreak = itemView.findViewById(R.id.imageViewSubjectStreak);
            textViewStreakCount = itemView.findViewById(R.id.textViewStreakCount);
        }
    }
}