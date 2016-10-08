package com.twilio.video.app.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.twilio.video.ScreenCapturer;
import com.twilio.video.app.R;
import com.twilio.video.app.dialog.Dialog;
import com.twilio.video.app.util.AccessManagerHelper;
import com.twilio.video.app.util.SimpleSignalingUtils;
import com.twilio.common.AccessManager;
import com.twilio.video.AudioTrack;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Media;
import com.twilio.video.Participant;
import com.twilio.video.Room;
import com.twilio.video.RoomState;
import com.twilio.video.VideoClient;
import com.twilio.video.VideoException;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class RoomActivity extends AppCompatActivity {
    private static final int REQUEST_MEDIA_PROJECTION = 100;
    private static final int THUMBNAIL_DIMENSION = 96;

    @BindView(R.id.connect_image_button) ImageButton connectImageButton;
    @BindView(R.id.media_status_textview) TextView mediaStatusTextview;
    @BindView(R.id.room_status_textview) TextView roomStatusTextview;
    @BindView(R.id.primary_video) VideoView primaryVideoView;
    @BindView(R.id.video_thumbnails_container) RelativeLayout videoThumbnailRelativeLayout;
    @BindView(R.id.local_video_thumbnail) VideoView localThumbnailVideoView;
    @BindView(R.id.remote_video_thumbnails) LinearLayout thumbnailLinearLayout;
    @BindView(R.id.local_video_image_button) ImageButton localVideoImageButton;
    @BindView(R.id.local_audio_image_button) ImageButton localAudioImageButton;
    @BindView(R.id.speaker_image_button) ImageButton speakerImageButton;
    @BindView(R.id.video_container) FrameLayout frameLayout;

    private MenuItem switchCameraMenuItem;
    private MenuItem pauseVideoMenuItem;
    private MenuItem pauseAudioMenuItem;
    private MenuItem screenCaptureMenuItem;

    private String username;
    private String capabilityToken;
    private String realm;
    private AccessManager accessManager;
    private VideoClient videoClient;
    private Room room;
    private String roomName;
    private LocalMedia localMedia;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack cameraVideoTrack;
    private LocalVideoTrack screenVideoTrack;
    private VideoTrack primaryVideoTrack;
    private CameraCapturer cameraCapturer;
    private AlertDialog alertDialog;
    boolean loggingOut;
    private ScreenCapturer screenCapturer;
    private final ScreenCapturer.Listener screenCapturerListener = new ScreenCapturer.Listener() {
        @Override
        public void onScreenCaptureError(String errorDescription) {
            Timber.e("Screen capturer error: " + errorDescription);
            stopScreenCapture();
            Toast.makeText(RoomActivity.this, R.string.screen_capture_error,
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onFirstFrameAvailable() {
            Timber.d("First frame from screen capturer available");
        }
    };

    private final Multimap<Participant, VideoView> participantVideoViewMultimap =
            HashMultimap.create();
    private final BiMap<VideoTrack, VideoView> videoTrackVideoViewBiMap = HashBiMap.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // So calls can be answered when screen is locked
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // Grab views
        setContentView(R.layout.activity_room);
        ButterKnife.bind(this);

        // Setup activity
        processActivityIntent(getIntent().getExtras());
        updateUi(RoomState.DISCONNECTED);
        loggingOut = false;

        // Setup local media and video client
        localMedia = LocalMedia.create(this);
        localAudioTrack = localMedia.addAudioTrack(true);
        cameraCapturer = new CameraCapturer(this,
                CameraCapturer.CameraSource.FRONT_CAMERA, null);
        cameraVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
        primaryVideoView.setMirror(true);
        cameraVideoTrack.addRenderer(primaryVideoView);

        // Create our video client
        accessManager = AccessManagerHelper.createAccessManager(this, capabilityToken);
        videoClient = new VideoClient(this, accessManager);
    }

    @Override
    protected void onDestroy() {
        if (localMedia != null) {
            localMedia.removeVideoTrack(cameraVideoTrack);
            localMedia.removeAudioTrack(localAudioTrack);
            localMedia.release();
            localMedia = null;
        }
        if (accessManager != null) {
            accessManager.dispose();
            accessManager = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.room_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Grab menu items for updating later
        switchCameraMenuItem = menu.findItem(R.id.switch_camera_menu_item);
        pauseVideoMenuItem = menu.findItem(R.id.pause_video_menu_item);
        pauseAudioMenuItem = menu.findItem(R.id.pause_audio_menu_item);
        screenCaptureMenuItem = menu.findItem(R.id.share_screen_menu_item);

        // Screen sharing only available on lollipop and up
        screenCaptureMenuItem.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.log_out_menu_item:
                logout();
                return true;
            case R.id.switch_camera_menu_item:
                switchCamera();
                return true;
            case R.id.share_screen_menu_item:
                String shareScreen = getString(R.string.share_screen);

                if (item.getTitle().equals(shareScreen)) {
                    if (screenCapturer == null) {
                        requestScreenCapturePermission();
                    } else {
                        startScreenCapture();
                    }
                } else {
                    stopScreenCapture();
                }

                return true;
            case R.id.pause_audio_menu_item:
                toggleLocalAudioTrackState();
                return true;
            case R.id.pause_video_menu_item:
                toggleLocalVideoTrackState();
                return true;
            case R.id.settings_menu_item:
                // TODO: Implement settings
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, R.string.screen_capture_permission_not_granted,
                        Toast.LENGTH_LONG).show();

                return;
            }
            screenCapturer = new ScreenCapturer(this, resultCode, data, screenCapturerListener);
            startScreenCapture();
        }
    }

    @OnClick(R.id.connect_image_button)
    void connect() {
        if (room != null) {
            Timber.i("Exiting room");
            room.disconnect();
        } else {
            EditText connectEditText = new EditText(this);
            alertDialog = Dialog.createConnectDialog(connectEditText,
                    connectClickListener(connectEditText),
                    cancelRoomClickListener(),
                    this);
            alertDialog.show();
        }
    }

    @OnClick(R.id.local_audio_image_button)
    void toggleLocalAudio() {
        int icon = 0;
        if (localAudioTrack == null) {
            localAudioTrack = localMedia.addAudioTrack(true);
            icon = R.drawable.ic_mic_white_24px;
            pauseAudioMenuItem.setVisible(true);
            pauseAudioMenuItem.setTitle(localAudioTrack.isEnabled() ?
                    R.string.pause_audio : R.string.resume_audio);
        } else {
            if (!localMedia.removeAudioTrack(localAudioTrack)) {
                Snackbar.make(roomStatusTextview,
                        "Audio track remove action failed",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
            localAudioTrack = null;
            icon = R.drawable.ic_mic_off_gray_24px;
            pauseAudioMenuItem.setVisible(false);
        }
        localAudioImageButton.setImageDrawable(ContextCompat.getDrawable(RoomActivity.this, icon));
    }

    @OnClick(R.id.local_video_image_button)
    void toggleLocalVideo() {
        int icon = 0;
        if (cameraVideoTrack == null) {
            // Add back local video from camera capturer
            Timber.d("Adding local video");
            cameraVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);

            // If participants have video tracks we render in thumbnial
            if (room != null && !videoTrackVideoViewBiMap.isEmpty()) {
                Timber.d("Participant video tracks are being rendered. Rendering local video in " +
                        "thumbnail");
                localThumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                        CameraCapturer.CameraSource.FRONT_CAMERA);
                cameraVideoTrack.addRenderer(localThumbnailVideoView);
            } else {
                // No remote tracks are being rendered so we render in primary view
                Timber.d("No remote video is being rendered. Rendering local video in primary " +
                        "view");
                primaryVideoView.setVisibility(View.VISIBLE);
                cameraVideoTrack.addRenderer(primaryVideoView);
            }
            // Set and icon and menu items
            icon = R.drawable.ic_videocam_white_24px;
            switchCameraMenuItem.setVisible(cameraVideoTrack.isEnabled());
            pauseVideoMenuItem.setTitle(cameraVideoTrack.isEnabled() ?
                    R.string.pause_video : R.string.resume_video);
            pauseVideoMenuItem.setVisible(true);
        } else {
            Timber.d("Removing local video");
            if (primaryVideoTrack == null) {
                // TODO: Add UI for no video state in primary view
                primaryVideoView.setVisibility(View.GONE);
            } else {
                // TODO: Add UI for no video in thumbnail view
                localThumbnailVideoView.setVisibility(View.GONE);
            }

            // Remove renderer and track
            cameraVideoTrack.removeRenderer(localThumbnailVideoView);
            if (!localMedia.removeVideoTrack(cameraVideoTrack)) {
                Snackbar.make(roomStatusTextview,
                        "Video track remove action failed",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }

            // Cleanup and set menu items accordingly
            cameraVideoTrack = null;
            icon = R.drawable.ic_videocam_off_gray_24px;
            switchCameraMenuItem.setVisible(false);
            pauseVideoMenuItem.setVisible(false);
        }
        localVideoImageButton.setImageDrawable(ContextCompat.getDrawable(RoomActivity.this, icon));
    }

    private void processActivityIntent(Bundle extras) {
        username = extras.getString(SimpleSignalingUtils.USERNAME);
        capabilityToken = extras.getString(SimpleSignalingUtils.CAPABILITY_TOKEN);
        realm = extras.getString(SimpleSignalingUtils.REALM);
    }

    private void updateUi(RoomState roomState) {
        int joinIcon = 0;
        if (roomState == RoomState.CONNECTING) {
            joinIcon = R.drawable.ic_call_end_white_24px;
        } else if (roomState == RoomState.CONNECTED) {
            getSupportActionBar().setTitle(room.getName());
            joinIcon = R.drawable.ic_call_end_white_24px;
        } else {
            getSupportActionBar().setTitle(username);
            joinIcon = R.drawable.ic_add_circle_white_24px;
        }
        connectImageButton.setImageDrawable(
                ContextCompat.getDrawable(RoomActivity.this, joinIcon));
    }

    private void logout() {
        // Will logout after disconnecting from the room
        loggingOut = true;
        // Disconnect from the current room
        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            room.disconnect();
        } else {
            returnToVideoClientLogin();
        }
    }

    private void returnToVideoClientLogin(){
        Intent registrationIntent = new Intent(RoomActivity.this, LoginActivity.class);
        startActivity(registrationIntent);
        finish();
    }

    private void switchCamera() {
        if (cameraCapturer != null) {
            Timber.d("Switching camera");
            cameraCapturer.switchCamera();
            localThumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                    CameraCapturer.CameraSource.FRONT_CAMERA);
        }
    }

    @TargetApi(21)
    private void requestScreenCapturePermission() {
        Timber.d("Requesting permission to capture screen");
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // This initiates a prompt dialog for the user to confirm screen projection.
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    private void startScreenCapture() {
        screenVideoTrack = localMedia.addVideoTrack(true, screenCapturer);
        screenCaptureMenuItem.setIcon(R.drawable.ic_stop_screen_share_white_24dp);
        screenCaptureMenuItem.setTitle(R.string.stop_screen_share);
    }

    private void stopScreenCapture() {
        localMedia.removeVideoTrack(screenVideoTrack);
        screenCaptureMenuItem.setIcon(R.drawable.ic_screen_share_white_24dp);
        screenCaptureMenuItem.setTitle(R.string.share_screen);
    }

    private void toggleLocalAudioTrackState() {
        if (localAudioTrack != null) {
            boolean enable = !localAudioTrack.isEnabled();
            localAudioTrack.enable(enable);
            pauseAudioMenuItem.setTitle(localAudioTrack.isEnabled() ?
                    R.string.pause_audio : R.string.resume_audio);
        }
    }

    private void toggleLocalVideoTrackState() {
        if (cameraVideoTrack != null) {
            boolean enable = !cameraVideoTrack.isEnabled();
            cameraVideoTrack.enable(enable);
            pauseVideoMenuItem.setTitle(cameraVideoTrack.isEnabled() ?
                    R.string.pause_video : R.string.resume_video);
        }
    }

    private DialogInterface.OnClickListener connectClickListener(final EditText connectEditText) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                connectToRoom(connectEditText.getText().toString());
            }
        };
    }

    private DialogInterface.OnClickListener cancelRoomClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // set proper action
                alertDialog.dismiss();
            }
        };
    }

    private void connectToRoom(String roomName) {
        roomStatusTextview.setText("Connecting to room " + roomName);
        this.roomName = roomName;
        ConnectOptions connectOptions = new ConnectOptions.Builder()
                .roomName(roomName)
                .localMedia(localMedia)
                .build();

        room = videoClient.connect(connectOptions, roomListener());
        updateUi(RoomState.CONNECTING);
    }

    private void addParticipant(Participant participant) {
        // Set listener
        participant.getMedia().setListener(new ParticipantMediaListener(participant));

        // Render each participant video track
        for (VideoTrack videoTrack : participant.getMedia().getVideoTracks()) {
            VideoView videoView = addParticipantVideo(videoTrack);

            // Maintain a relationship between a participant and its main rendered videos
            participantVideoViewMultimap.put(participant, videoView);
        }
    }

    private VideoView addParticipantVideo(VideoTrack videoTrack) {
        if (primaryVideoTrack == null) {
            Timber.d("Rendering participant video in primary view");
            moveLocalVideoToThumbnail();
            primaryVideoView.setMirror(false);
            primaryVideoView.setVisibility(View.VISIBLE);
            videoTrack.addRenderer(primaryVideoView);
            videoTrackVideoViewBiMap.put(videoTrack, primaryVideoView);
            primaryVideoTrack = videoTrack;

            return primaryVideoView;
        } else {
            Timber.d("Rendering participant video in thumbnail view");
            VideoView videoView = new VideoView(this);
            videoView.setMirror(false);
            videoView.applyZOrder(true);
            thumbnailLinearLayout.addView(videoView);
            videoView.getLayoutParams().width = (int)
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            THUMBNAIL_DIMENSION, getResources().getDisplayMetrics());
            videoView.getLayoutParams().height = (int)
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            THUMBNAIL_DIMENSION, getResources().getDisplayMetrics());
            videoTrack.addRenderer(videoView);
            videoTrackVideoViewBiMap.put(videoTrack, videoView);
            videoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Timber.d("Swapping clicked video with primary view");
                    VideoView clickedVideoView = (VideoView) v;
                    VideoTrack clickedVideoTrack = videoTrackVideoViewBiMap
                            .inverse().get(clickedVideoView);

                    // Swap track renderers
                    clickedVideoTrack.removeRenderer(clickedVideoView);
                    primaryVideoTrack.removeRenderer(primaryVideoView);
                    clickedVideoTrack.addRenderer(primaryVideoView);
                    primaryVideoTrack.addRenderer(clickedVideoView);

                    // Update bimap
                    videoTrackVideoViewBiMap.forcePut(clickedVideoTrack, primaryVideoView);
                    videoTrackVideoViewBiMap.forcePut(primaryVideoTrack, clickedVideoView);

                    // Swap references
                    primaryVideoTrack = clickedVideoTrack;
                }
            });

            return videoView;
        }
    }

    private void removeAllParticipants() {
        Timber.d("Cleaning out all participants");
        thumbnailLinearLayout.removeAllViews();
        videoTrackVideoViewBiMap.clear();
        participantVideoViewMultimap.clear();
        primaryVideoTrack = null;
        moveLocalVideoToPrimary();
    }

    private void removeParticipant(Participant participant) {
        roomStatusTextview.setText("Participant " + participant.getIdentity() + " left.");
        for (VideoView videoView : participantVideoViewMultimap.removeAll(participant)) {
            VideoTrack videoTrack = videoTrackVideoViewBiMap.inverse().get(videoView);
            if (videoTrack != null) {
                removeParticipantVideo(videoTrackVideoViewBiMap.inverse().get(videoView));
            }
        }
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        if (videoTrack == primaryVideoTrack) {
            Timber.d("Removing participant video from primary view");
            primaryVideoTrack.removeRenderer(primaryVideoView);
            videoTrackVideoViewBiMap.remove(videoTrack);
            ViewGroup remoteVideoThumbnails = thumbnailLinearLayout;
            VideoView videoView = (VideoView) remoteVideoThumbnails.getChildAt(0);

            if (videoView != null) {
                Timber.d("Moving first remote thumbnail to primary view");
                VideoTrack newPrimaryTrack = videoTrackVideoViewBiMap.inverse().get(videoView);
                newPrimaryTrack.removeRenderer(videoView);
                newPrimaryTrack.addRenderer(primaryVideoView);
                videoTrackVideoViewBiMap.forcePut(newPrimaryTrack, primaryVideoView);
                primaryVideoTrack = newPrimaryTrack;
                remoteVideoThumbnails.removeView(videoView);
            } else {
                Timber.d("No remote thumbnail found.");
                moveLocalVideoToPrimary();
                primaryVideoTrack = null;
            }
        } else {
            Timber.d("Removing participant video from thumbnail");
            VideoView videoView = videoTrackVideoViewBiMap.remove(videoTrack);
            thumbnailLinearLayout.removeView(videoView);
        }
    }

    private void moveLocalVideoToThumbnail() {

        if (cameraVideoTrack != null) {
            boolean renderingToThumbnail = cameraVideoTrack.getRenderers().get(0) ==
                    localThumbnailVideoView;
            if (!renderingToThumbnail) {
                Timber.d("Moving camera video to thumbnail");
                cameraVideoTrack.removeRenderer(primaryVideoView);
                localThumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                        CameraCapturer.CameraSource.FRONT_CAMERA);
                videoThumbnailRelativeLayout.setVisibility(View.VISIBLE);
                localThumbnailVideoView.setVisibility(View.VISIBLE);
                cameraVideoTrack.addRenderer(localThumbnailVideoView);
            }
        } else {
            // TODO: Create thumbnail with name and icon in place of video
        }
    }

    private void moveLocalVideoToPrimary() {
        if(cameraVideoTrack != null) {
            boolean renderingToPrimary = cameraVideoTrack.getRenderers().get(0) == primaryVideoView;
            if (!renderingToPrimary) {
                Timber.d("Moving camera video to primary view");
                cameraVideoTrack.removeRenderer(localThumbnailVideoView);
                primaryVideoView.setVisibility(View.VISIBLE);
                cameraVideoTrack.addRenderer(primaryVideoView);
                primaryVideoView.setMirror(cameraCapturer.getCameraSource() ==
                        CameraCapturer.CameraSource.FRONT_CAMERA);
            }
        } else {
            // TODO: Show icon and name in place of video
            primaryVideoView.setVisibility(View.GONE);
        }
        localThumbnailVideoView.setVisibility(View.GONE);
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                Timber.i("onConnected: " + room.getName() + " sid:" +
                        room.getSid() + " state:" + room.getState());
                roomStatusTextview.setText("Connected to " + room.getName());
                updateUi(RoomState.CONNECTED);

                for (Map.Entry<String, Participant> entry : room.getParticipants().entrySet()) {
                    addParticipant(entry.getValue());
                }
            }

            @Override
            public void onConnectFailure(Room room, VideoException error) {
                Timber.i("onConnectFailure");
                roomStatusTextview.setText("Failed to connect to " + roomName);
                RoomActivity.this.room = null;
                updateUi(RoomState.DISCONNECTED);
            }

            @Override
            public void onDisconnected(Room room, VideoException error) {
                Timber.i("onDisconnected");
                roomStatusTextview.setText("Disconnected from " + roomName);
                removeAllParticipants();
                updateUi(RoomState.DISCONNECTED);
                RoomActivity.this.room = null;
                if (loggingOut) {
                    returnToVideoClientLogin();
                }
            }

            @Override
            public void onParticipantConnected(Room room, Participant participant) {
                Timber.i("onParticipantConnected: " + participant.getIdentity());
                addParticipant(participant);
            }

            @Override
            public void onParticipantDisconnected(Room room, Participant participant) {
                Timber.i("onParticipantDisconnected " + participant.getIdentity());
                removeParticipant(participant);
            }
        };
    }

    private class ParticipantMediaListener implements Media.Listener {
        private final Participant participant;

        ParticipantMediaListener(Participant participant) {
            this.participant = participant;
        }

        @Override
        public void onAudioTrackAdded(Media media, AudioTrack audioTrack) {
            Timber.i("onAudioTrackAdded");
            mediaStatusTextview.setText(participant.getIdentity() + ": onAudioTrackAdded");
        }

        @Override
        public void onAudioTrackRemoved(Media media, AudioTrack audioTrack) {
            Timber.i(participant.getIdentity() + ": onAudioTrackRemoved for ");
        }

        @Override
        public void onVideoTrackAdded(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackAdded");
            participantVideoViewMultimap.put(participant, addParticipantVideo(videoTrack));
        }

        @Override
        public void onVideoTrackRemoved(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackRemoved");
            removeParticipantVideo(videoTrack);
        }

        @Override
        public void onAudioTrackEnabled(Media media, AudioTrack audioTrack) {
            Timber.i(participant.getIdentity() + ": onAudioTrackEnabled");
        }

        @Override
        public void onAudioTrackDisabled(Media media, AudioTrack audioTrack) {
            Timber.i(participant.getIdentity() + ": onAudioTrackDisabled");
        }

        @Override
        public void onVideoTrackEnabled(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackEnabled");
        }

        @Override
        public void onVideoTrackDisabled(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackDisabled");
        }
    }
}
