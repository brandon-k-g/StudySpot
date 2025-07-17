package com.example.studyspot; // Ensure this matches your package name

import android.content.Context;
import android.util.Log; // For logging adapter lifecycle and interaction events.
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton; // Import for the options menu button (buttonTopicOptions).
import android.widget.TextView;    // Ensure TextView is imported for displaying topic details.
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu; // Import for creating the options popup menu (Edit/Delete).
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList; // Using ArrayList for the internal list of topics.
import java.util.List;
import java.util.Locale; // For formatting strings, like the flashcard count.

/**
 * RecyclerView Adapter for displaying a list of {@link Topic} objects.
 * This adapter is responsible for creating view holders for each topic item,
 * binding topic data (like title and flashcard count) to the views,
 * and handling user actions such as clicking on a topic to view its flashcards,
 * or using an options menu to edit or delete the topic.
 * My design uses the {@link OnTopicActionClickListener} interface to delegate these actions
 * back to the hosting Activity (TopicListActivity).
 */
public class TopicsAdapter extends RecyclerView.Adapter<TopicsAdapter.TopicViewHolder> {

    // Log tag specific to this adapter for easier debugging.
    private static final String ADAPTER_TAG = "TopicsAdapter";
    // Internal list to hold the topic data. My design choice to use an internal list
    // provides better encapsulation and control over the data displayed by the adapter.
    private List<Topic> topicListInternal;
    // Context is needed for layout inflation and creating PopupMenu instances.
    private Context context;
    // Listener instance (typically TopicListActivity) to handle actions performed on a topic item.
    // I've named it OnTopicActionClickListener to reflect that it handles more than just clicks.
    private OnTopicActionClickListener onTopicActionClickListener;

    /**
     * Interface definition for a callback to be invoked when various actions
     * (click, edit, delete) are performed on a topic item.
     * This interface is my chosen way to decouple the adapter's view logic
     * from the business logic handled by the hosting Activity.
     */
    public interface OnTopicActionClickListener {
        /**
         * Called when a topic item view is clicked (the main item area).
         * My primary intention for this is to navigate to view the flashcards within that topic.
         * @param topic The {@link Topic} object that was clicked.
         */
        void onTopicClick(Topic topic);

        /**
         * Called when the "Edit" action is selected from the options menu for a topic.
         * @param topic The {@link Topic} object to be edited.
         */
        void onEditTopic(Topic topic);

        /**
         * Called when the "Delete" action is selected from the options menu for a topic.
         * @param topic The {@link Topic} object to be deleted.
         */
        void onDeleteTopic(Topic topic);
    }

    /**
     * Constructor for the TopicsAdapter.
     * @param context The context of the calling activity, used for various Android operations.
     * @param initialTopicList The initial list of topics to display. The adapter creates its own internal copy.
     * @param listener The listener that will handle actions performed on topic items.
     */
    public TopicsAdapter(Context context, List<Topic> initialTopicList, OnTopicActionClickListener listener) {
        this.context = context;
        this.onTopicActionClickListener = listener;
        this.topicListInternal = new ArrayList<>(); // Initialize the adapter's internal list.
        if (initialTopicList != null) {
            // Populate internal list with initial data. This copies the data,
            // which is a good practice to avoid unexpected modifications if the passed list is mutable.
            this.topicListInternal.addAll(initialTopicList);
        }
        Log.d(ADAPTER_TAG, "TopicsAdapter created. Initial internal list size: " + this.topicListInternal.size());
    }

    /**
     * Called when RecyclerView needs a new {@link TopicViewHolder} of the given type to represent an item.
     * My implementation inflates the `item_topic.xml` layout for each topic item.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new TopicViewHolder that holds the View for each topic item.
     */
    @NonNull
    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(ADAPTER_TAG, "onCreateViewHolder: Inflating layout for a new topic item.");
        // Inflate the custom layout (R.layout.item_topic) for each topic item.
        View view = LayoutInflater.from(context).inflate(R.layout.item_topic, parent, false);
        return new TopicViewHolder(view); // Create and return a new ViewHolder instance.
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the {@link TopicViewHolder#itemView} to reflect the topic
     * at the given position. My implementation sets the topic title, the denormalized flashcard count,
     * and configures click listeners for item interaction and an options menu.
     * @param holder The TopicViewHolder which should be updated.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {
        // Get the Topic object for the current position from the adapter's internal list.
        Topic topic = topicListInternal.get(position);
        Log.d(ADAPTER_TAG, "onBindViewHolder: Binding data for position " + position +
                ", Topic: '" + topic.getTitle() + "', Flashcards: " + topic.getFlashcardCount());

        // Set the topic title, handling null for robustness.
        if (topic.getTitle() != null) {
            holder.textViewTopicTitle.setText(topic.getTitle());
        } else {
            holder.textViewTopicTitle.setText(""); // Default to empty string if title is null.
        }

        // Set the flashcard count. This uses the denormalized 'flashcardCount' field from the Topic object,
        // which is my design choice for efficient display without needing extra queries per topic.
        holder.textViewFlashcardCount.setText(String.format(Locale.getDefault(), "Flashcards: %d", topic.getFlashcardCount()));

        // Set the click listener for the entire topic item view.
        // My intention is for this click to navigate to the list of flashcards for this topic.
        holder.itemView.setOnClickListener(v -> {
            if (onTopicActionClickListener != null) {
                Log.d(ADAPTER_TAG, "itemView clicked for topic: '" + topic.getTitle() + "'. Delegating to onTopicClick.");
                onTopicActionClickListener.onTopicClick(topic);
            }
        });

        // Set the click listener for the options button (e.g., three dots menu).
        // This button will trigger a PopupMenu with "Edit" and "Delete" actions.
        // It's important that R.id.buttonTopicOptions exists in item_topic.xml.
        if (holder.buttonTopicOptions != null) { // Defensive null check for the options button.
            holder.buttonTopicOptions.setOnClickListener(v -> {
                Log.d(ADAPTER_TAG, "Options button clicked for topic: '" + topic.getTitle() + "'. Showing PopupMenu.");
                // Create and show the PopupMenu.
                PopupMenu popup = new PopupMenu(context, holder.buttonTopicOptions);
                popup.getMenuInflater().inflate(R.menu.menu_topic_options, popup.getMenu()); // Inflate my custom menu resource.

                // Set a listener to handle clicks on the menu items.
                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_edit_topic) { // User selected "Edit".
                        if (onTopicActionClickListener != null) {
                            Log.d(ADAPTER_TAG, "'Edit' selected for topic: '" + topic.getTitle() + "'. Delegating to onEditTopic.");
                            onTopicActionClickListener.onEditTopic(topic);
                        }
                        return true; // Consume the click.
                    } else if (itemId == R.id.action_delete_topic) { // User selected "Delete".
                        if (onTopicActionClickListener != null) {
                            Log.d(ADAPTER_TAG, "'Delete' selected for topic: '" + topic.getTitle() + "'. Delegating to onDeleteTopic.");
                            onTopicActionClickListener.onDeleteTopic(topic);
                        }
                        return true; // Consume the click.
                    }
                    return false; // Item not handled.
                });
                popup.show(); // Display the popup menu.
            });
        } else {
            // Log an error if the options button is not found in the layout. This helps in debugging layout issues.
            Log.e(ADAPTER_TAG, "onBindViewHolder: buttonTopicOptions view is null. " +
                    "Please ensure an ImageButton with ID 'buttonTopicOptions' exists in your item_topic.xml layout.");
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of topics in {@link #topicListInternal}.
     */
    @Override
    public int getItemCount() {
        // Use the adapter's internal list for the count. Return 0 if null to prevent errors.
        int count = topicListInternal != null ? topicListInternal.size() : 0;
        Log.d(ADAPTER_TAG, "getItemCount: Returning item count: " + count);
        return count;
    }

    /**
     * Updates the list of topics displayed by the adapter.
     * This method clears the existing internal list, adds all items from the new list,
     * and then notifies the RecyclerView that the data set has changed, triggering a UI refresh.
     * For future optimization with larger lists or more frequent updates, I would explore `DiffUtil`.
     * @param newTopicList The new list of {@link Topic} objects to display.
     */
    public void setTopics(List<Topic> newTopicList) {
        Log.d(ADAPTER_TAG, "setTopics: Updating adapter with new topic list. New list size: " +
                (newTopicList != null ? newTopicList.size() : "null"));
        this.topicListInternal.clear(); // Clear the current data in the adapter's internal list.
        if (newTopicList != null) {
            this.topicListInternal.addAll(newTopicList); // Add all items from the new list.
        }
        // Notify any registered observers (like the RecyclerView) that the entire data set has changed.
        // This causes the RecyclerView to rebind and redraw all visible items.
        notifyDataSetChanged();
        Log.d(ADAPTER_TAG, "Adapter data set changed notification sent. Current internal list size: " + this.topicListInternal.size());
    }

    /**
     * ViewHolder class for topic items.
     * This class implements the ViewHolder pattern, which enhances scrolling performance
     * by caching references to the views within each item layout (defined in `item_topic.xml`).
     * My item layout is designed to display the topic title, flashcard count, and an options menu.
     */
    public static class TopicViewHolder extends RecyclerView.ViewHolder {
        // UI elements within each topic item.
        TextView textViewTopicTitle;
        TextView textViewFlashcardCount;
        ImageButton buttonTopicOptions; // Button to trigger the edit/delete options menu.

        /**
         * Constructor for the TopicViewHolder.
         * @param itemView The View inflated from `item_topic.xml`.
         */
        public TopicViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find and cache references to the views by their IDs from the item layout.
            // It's essential that these IDs correctly match those defined in R.layout.item_topic.
            textViewTopicTitle = itemView.findViewById(R.id.textViewTopicTitle);
            textViewFlashcardCount = itemView.findViewById(R.id.textViewFlashcardCountTopicItem); // Make sure this ID matches your XML.
            buttonTopicOptions = itemView.findViewById(R.id.buttonTopicOptions); // This ID must exist in item_topic.xml.
        }
    }
}