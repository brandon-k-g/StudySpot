package com.example.studyspot;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton; // Import for using ImageButton as an options menu trigger.
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu; // Import for creating the options popup menu.
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying a list of {@link Flashcard} objects.
 * This adapter is responsible for creating view holders, binding flashcard data to the views,
 * and handling user interactions on each item by delegating actions to an
 * {@link OnFlashcardClickListener}.
 * My design uses a custom interface to communicate events like clicks, edits, and deletes
 * back to the hosting Activity (FlashcardListActivity).
 */
public class FlashcardsAdapter extends RecyclerView.Adapter<FlashcardsAdapter.FlashcardViewHolder> {

    // Log tag specific to this adapter for easier debugging.
    private static final String ADAPTER_TAG = "FlashcardsAdapter";
    // Internal list to hold the flashcard data. Using a separate internal list
    // provides better control over data manipulation within the adapter.
    private List<Flashcard> flashcardListInternal;
    // Context is needed for layout inflation and creating PopupMenu.
    private Context context;
    // Listener instance (typically the hosting Activity) to handle item-specific actions.
    private OnFlashcardClickListener onFlashcardClickListener;

    /**
     * Interface definition for a callback to be invoked when a flashcard item is interacted with.
     * This is my chosen mechanism for decoupling the adapter's view logic from the
     * business logic handled by the Activity.
     */
    public interface OnFlashcardClickListener {
        /**
         * Called when a flashcard item view is clicked.
         * My intention for this is typically to view/flip the flashcard or initiate an edit.
         * @param flashcard The {@link Flashcard} object that was clicked.
         * @param position The position of the clicked item in the list.
         */
        void onFlashcardClick(Flashcard flashcard, int position);

        /**
         * Called when the "Edit" action is selected for a flashcard.
         * @param flashcard The {@link Flashcard} object to be edited.
         */
        void onEditFlashcard(Flashcard flashcard);

        /**
         * Called when the "Delete" action is selected for a flashcard.
         * @param flashcard The {@link Flashcard} object to be deleted.
         */
        void onDeleteFlashcard(Flashcard flashcard);
    }

    /**
     * Constructor for the FlashcardsAdapter.
     * @param context The context of the calling activity, used for layout inflation and popups.
     * @param initialFlashcardList The initial list of flashcards to display.
     * A new ArrayList is created internally to hold these.
     * @param listener The listener that will handle item click, edit, and delete events.
     */
    public FlashcardsAdapter(Context context, List<Flashcard> initialFlashcardList, OnFlashcardClickListener listener) {
        this.context = context;
        this.onFlashcardClickListener = listener;
        this.flashcardListInternal = new ArrayList<>(); // Initialize internal list.
        if (initialFlashcardList != null) {
            // Populate internal list with initial data. This copies the data,
            // which is a design choice to avoid external modification issues if the passed list is mutable.
            this.flashcardListInternal.addAll(initialFlashcardList);
        }
    }

    /**
     * Called when RecyclerView needs a new {@link FlashcardViewHolder} of the given type to represent an item.
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. My implementation inflates the `item_flashcard.xml` layout.
     * @param parent The ViewGroup into which the new View will be added after it is bound to
     * an adapter position.
     * @param viewType The view type of the new View.
     * @return A new FlashcardViewHolder that holds the View for each flashcard item.
     */
    @NonNull
    @Override
    public FlashcardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom layout for each flashcard item.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flashcard, parent, false);
        return new FlashcardViewHolder(view); // Create and return a new ViewHolder instance.
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the {@link FlashcardViewHolder#itemView} to reflect the item at the
     * given position. I set the question text and configure click listeners here.
     * @param holder The FlashcardViewHolder which should be updated to represent the contents of the
     * item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull FlashcardViewHolder holder, int position) {
        // Get the Flashcard object for the current position.
        Flashcard flashcard = flashcardListInternal.get(position);
        Log.d(ADAPTER_TAG, "Binding data for position: " + position + ", Question: " + flashcard.getQuestion());

        // Set the question text. Handle null case for robustness.
        holder.textViewFlashcardQuestion.setText(flashcard.getQuestion() != null ? flashcard.getQuestion() : "");
        // Set a placeholder for the answer preview. The actual answer visibility could be toggled on click.
        holder.textViewFlashcardAnswerPreview.setText("Tap to see answer...");
        // For a flip animation, I might manage holder.textViewFlashcardAnswerPreview.setVisibility(View.GONE) initially
        // and then toggle visibility with an animation on item click.

        // Set the click listener for the entire item view.
        // This delegates the click event to the onFlashcardClickListener.
        holder.itemView.setOnClickListener(v -> {
            if (onFlashcardClickListener != null) {
                int currentPosition = holder.getAdapterPosition(); // Use getAdapterPosition() for safety.
                if (currentPosition != RecyclerView.NO_POSITION) {
                    // Pass the specific flashcard and its position to the listener.
                    onFlashcardClickListener.onFlashcardClick(flashcardListInternal.get(currentPosition), currentPosition);
                }
            }
        });

        // Set the click listener for the options button (e.g., three dots).
        // This button triggers a PopupMenu for edit/delete actions.
        if (holder.buttonFlashcardOptions != null) { // Defensive null check.
            holder.buttonFlashcardOptions.setOnClickListener(view -> {
                if (onFlashcardClickListener != null) {
                    // Show the options menu anchored to the clicked button.
                    showOptionsMenu(holder.buttonFlashcardOptions, flashcard);
                }
            });
        }
    }

    /**
     * Creates and displays a PopupMenu with "Edit" and "Delete" options for a given flashcard.
     * This is a private helper method invoked when the options button in a flashcard item is clicked.
     * @param view The View (options button) to which the popup menu should be anchored.
     * @param flashcard The {@link Flashcard} object for which the options are being shown.
     */
    private void showOptionsMenu(View view, Flashcard flashcard) {
        PopupMenu popup = new PopupMenu(context, view); // Create PopupMenu instance.
        popup.getMenuInflater().inflate(R.menu.menu_flashcard_options, popup.getMenu()); // Inflate custom menu resource.

        // Set a listener to handle clicks on the menu items.
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit_flashcard_item) {
                // If "Edit" is clicked, delegate to the onEditFlashcard method of the listener.
                if (onFlashcardClickListener != null) {
                    onFlashcardClickListener.onEditFlashcard(flashcard);
                }
                return true; // Consume the click.
            } else if (itemId == R.id.action_delete_flashcard_item) {
                // If "Delete" is clicked, delegate to the onDeleteFlashcard method of the listener.
                if (onFlashcardClickListener != null) {
                    onFlashcardClickListener.onDeleteFlashcard(flashcard);
                }
                return true; // Consume the click.
            }
            return false; // Item not handled.
        });
        popup.show(); // Display the popup menu.
    }


    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of flashcards in {@link #flashcardListInternal}.
     */
    @Override
    public int getItemCount() {
        // Return 0 if the list is null to prevent NullPointerExceptions.
        return flashcardListInternal != null ? flashcardListInternal.size() : 0;
    }

    /**
     * Updates the list of flashcards displayed by the adapter.
     * This method clears the existing internal list, adds all items from the new list,
     * and then notifies the RecyclerView that the data set has changed, triggering a refresh.
     * For improved performance on large datasets or frequent updates, I would consider using
     * `DiffUtil` in a future iteration.
     * @param newFlashcardList The new list of {@link Flashcard} objects to display.
     */
    public void setFlashcards(List<Flashcard> newFlashcardList) {
        this.flashcardListInternal.clear(); // Clear the current data.
        if (newFlashcardList != null) {
            this.flashcardListInternal.addAll(newFlashcardList); // Add new data.
        }
        // Notify any registered observers that the data set has changed.
        // RecyclerView will rebind and redraw items as necessary.
        notifyDataSetChanged();
        Log.d(ADAPTER_TAG, "Flashcard list updated in adapter. New size: " + (this.flashcardListInternal != null ? this.flashcardListInternal.size() : 0));
    }

    /**
     * ViewHolder class for flashcard items.
     * This class implements the ViewHolder pattern, which improves scrolling performance
     * by caching references to the views within each item layout.
     * My design for `item_flashcard.xml` includes TextViews for question and answer preview,
     * and an ImageButton for item-specific options.
     */
    public static class FlashcardViewHolder extends RecyclerView.ViewHolder {
        // UI elements within each flashcard item.
        TextView textViewFlashcardQuestion;
        TextView textViewFlashcardAnswerPreview;
        ImageButton buttonFlashcardOptions; // Button to trigger the options menu (edit/delete).

        /**
         * Constructor for the FlashcardViewHolder.
         * @param itemView The View inflated from `item_flashcard.xml`.
         */
        public FlashcardViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find and cache references to the views within the item layout.
            textViewFlashcardQuestion = itemView.findViewById(R.id.textViewFlashcardQuestion);
            textViewFlashcardAnswerPreview = itemView.findViewById(R.id.textViewFlashcardAnswerPreview);
            buttonFlashcardOptions = itemView.findViewById(R.id.buttonFlashcardOptions); // Initialize the options button.
        }
    }
}