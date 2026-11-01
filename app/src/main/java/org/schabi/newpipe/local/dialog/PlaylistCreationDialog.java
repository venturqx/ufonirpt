package org.schabi.newpipe.local.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog.Builder;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Scheduler;

public final class PlaylistCreationDialog extends PlaylistDialog {

    /**
     * Create a new instance of {@link PlaylistCreationDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistCreationDialog}
     */
    public static PlaylistCreationDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        if (getStreamEntities() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.getRoot().getContext().setTheme(ThemeHelper.getDialogTheme(requireContext()));
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);

        final Builder dialogBuilder = new Builder(requireContext(),
                ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.create_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = dialogBinding.dialogEditText.getText().toString();
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
                    final Toast successToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT);
                    final Context appContext = requireContext().getApplicationContext();

                    subscribeDetached(playlistManager.createPlaylist(name, getStreamEntities()),
                            AndroidSchedulers.mainThread(),
                            successToast::show,
                            throwable -> ErrorUtil.createNotification(appContext,
                                    new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                            "Creating playlist: " + name)));
                });
        return dialogBuilder.create();
    }

    @SuppressLint("CheckResult")
    static <T> void subscribeDetached(final Maybe<T> source,
                                      final Scheduler scheduler,
                                      final Runnable onSuccess,
                                      final Consumer<Throwable> onError) {
        source.observeOn(scheduler)
                .subscribe(result -> onSuccess.run(), onError::accept);
    }
}
