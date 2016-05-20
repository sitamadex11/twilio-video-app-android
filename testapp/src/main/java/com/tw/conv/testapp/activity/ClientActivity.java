package com.tw.conv.testapp.activity;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.appyvet.rangebar.IRangeBarFormatter;
import com.appyvet.rangebar.RangeBar;
import com.tw.conv.testapp.R;
import com.tw.conv.testapp.adapter.IceServerAdapter;
import com.tw.conv.testapp.adapter.RemoteVideoTrackStatsAdapter;
import com.tw.conv.testapp.dialog.Dialog;
import com.tw.conv.testapp.model.TwilioIceResponse;
import com.tw.conv.testapp.model.TwilioIceServer;
import com.tw.conv.testapp.util.IceOptionsHelper;
import com.tw.conv.testapp.util.ParticipantParser;
import com.tw.conv.testapp.util.SimpleSignalingUtils;
import com.twilio.common.TwilioAccessManager;
import com.twilio.common.TwilioAccessManagerFactory;
import com.twilio.common.TwilioAccessManagerListener;
import com.twilio.conversations.AspectRatio;
import com.twilio.conversations.AudioOutput;
import com.twilio.conversations.AudioTrack;
import com.twilio.conversations.CameraCapturer;
import com.twilio.conversations.CapturerErrorListener;
import com.twilio.conversations.CapturerException;
import com.twilio.conversations.Conversation;
import com.twilio.conversations.ConversationCallback;
import com.twilio.conversations.TwilioConversationsClient;
import com.twilio.conversations.IceOptions;
import com.twilio.conversations.IceServer;
import com.twilio.conversations.IceTransportPolicy;
import com.twilio.conversations.IncomingInvite;
import com.twilio.conversations.LocalAudioTrackStatsRecord;
import com.twilio.conversations.LocalMedia;
import com.twilio.conversations.LocalVideoTrack;
import com.twilio.conversations.LocalVideoTrackStatsRecord;
import com.twilio.conversations.MediaTrack;
import com.twilio.conversations.MediaTrackStatsRecord;
import com.twilio.conversations.OutgoingInvite;
import com.twilio.conversations.Participant;
import com.twilio.conversations.RemoteAudioTrackStatsRecord;
import com.twilio.conversations.RemoteVideoTrackStatsRecord;
import com.twilio.conversations.StatsListener;
import com.twilio.conversations.TwilioConversationsException;
import com.twilio.conversations.VideoConstraints;
import com.twilio.conversations.VideoDimensions;
import com.twilio.conversations.VideoRenderer;
import com.twilio.conversations.VideoScaleType;
import com.twilio.conversations.VideoTrack;
import com.twilio.conversations.VideoViewRenderer;
import com.twilio.conversations.internal.ClientOptionsInternal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import timber.log.Timber;

public class ClientActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_REJECT_INCOMING_CALL = 1000;
    private static final int REQUEST_CODE_ACCEPT_INCOMING_CALL = 1001;
    private static final int INCOMING_CALL_NOTIFICATION_ID = 1002;
    private static final String ACTION_REJECT_INCOMING_CALL =
            "com.tw.conv.testapp.action.REJECT_INCOMING_CALL";
    private static final String ACTION_ACCEPT_INCOMING_CALL =
            "com.tw.conv.testapp.action.ACCEPT_INCOMING_CALL";

    public static final String OPTION_PREFER_H264_KEY = "enable-h264";
    public static final String OPTION_AUTO_ACCEPT_KEY = "auto-accept";
    public static final String OPTION_USE_HEADSET_KEY = "use-headset";
    public static final String OPTION_LOGOUT_WHEN_CONV_ENDS_KEY = "logout-when-conv-ends";
    private static final String OPTION_DEV_REGISTRAR = "endpoint.dev.twilio.com";
    private static final String OPTION_DEV_STATS_URL = "https://eventgw.dev.twilio.com";
    private static final String OPTION_STAGE_REGISTRAR = "endpoint.stage.twilio.com";
    private static final String OPTION_STAGE_STATS_URL = "https://eventgw.stage.twilio.com";
    private static final String OPTION_REGISTRAR_KEY = "registrar";
    private static final String OPTION_STATS_KEY = "stats-server-url";

    private TwilioConversationsClient twilioConversationsClient;
    private OutgoingInvite outgoingInvite;
    private LocalMedia localMedia;
    private boolean wasPreviewing = false;
    private boolean wasLive = false;
    private boolean inBackground = false;
    private boolean loggingOut = false;
    private String username;
    private String realm;
    private CheckBox statsCheckBox;
    private LinearLayout statsLayout;
    private TextView localVideoTrackStatsTextView;
    private RemoteVideoTrackStatsAdapter remoteVideoTrackStatsAdapter;
    private LinkedHashMap<String, RemoteVideoTrackStatsRecord>
            remoteVideoTrackStatsRecordMap = new LinkedHashMap<>();
    private RecyclerView remoteStatsRecyclerView;
    private boolean preferH264;
    private boolean autoAccept;
    private String selectedTwilioIceServersJson;
    private String iceTransportPolicy;
    private String twilioIceServersJson;
    private boolean useHeadset;
    private boolean logoutWhenConvEnds;

    private enum AudioState {
        ENABLED,
        DISABLED,
    }

    private enum VideoState {
        ENABLED,
        DISABLED,
    }

    // We will default to front facing for now but this could easily be a preference
    private CameraCapturer.CameraSource currentCameraSource =
            CameraCapturer.CameraSource.CAMERA_SOURCE_FRONT_CAMERA;
    private CameraCapturer cameraCapturer;
    private EditText participantEditText;
    private AlertDialog  alertDialog;
    private TextView conversationsClientStatusTextView;
    private TextView conversationStatusTextView;
    private FloatingActionButton callActionFab;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton pauseActionFab;
    private FloatingActionButton audioActionFab;
    private FloatingActionButton muteActionFab;
    private FloatingActionButton addParticipantActionFab;
    private FloatingActionButton speakerActionFab;
    private IncomingInvite incomingInvite;
    private Conversation conversation;
    private FrameLayout previewFrameLayout;
    private LocalData localData;
    private ViewGroup mainVideoContainer;
    private boolean mirrorLocalRenderer = true;

    private class LocalData {
        public ViewGroup container;
        public VideoViewRenderer renderer;
        public LocalVideoTrack localVideoTrack;
    }

    private class ParticipantData {
        public final ViewGroup container;
        public final VideoViewRenderer renderer;


        public ParticipantData(ViewGroup container, VideoViewRenderer renderer) {
            this.container = container;
            this.renderer = renderer;
        }


    }
    private Map<Participant, ParticipantData> participantDataMap;

    private LinearLayout videoLinearLayout;
    private VideoState videoState;
    private AudioState audioState;
    private String capabilityToken;
    private TwilioAccessManager accessManager;

    private VideoConstraints videoConstraints;

    private int minFps = 0;
    private int maxFps = 0;
    private VideoDimensions minVideoDimensions = null;
    private VideoDimensions maxVideoDimensions = null;

    private AspectRatio aspectRatio = new AspectRatio(0, 0);

    private Spinner iceTransPolicySpinner;
    private ListView twilioIceServersListView;
    private RelativeLayout iceOptionsLayout;
    private CheckBox enableIceCheckbox;
    private CheckBox preferH264Checkbox;
    private CheckBox logoutWhenConvEndsCheckbox;
    private Spinner aspectRatioSpinner;

    private static final Map<Integer, VideoDimensions> videoDimensionsMap;
    static {
        Map<Integer, VideoDimensions> vdMap = new HashMap<>();
        vdMap.put(0, new VideoDimensions(0,0));
        vdMap.put(1, VideoDimensions.CIF_VIDEO_DIMENSIONS);
        vdMap.put(2, VideoDimensions.VGA_VIDEO_DIMENSIONS);
        vdMap.put(3, VideoDimensions.WVGA_VIDEO_DIMENSIONS);
        vdMap.put(4, VideoDimensions.HD_540P_VIDEO_DIMENSIONS);
        vdMap.put(5, VideoDimensions.HD_720P_VIDEO_DIMENSIONS);
        vdMap.put(6, VideoDimensions.HD_960P_VIDEO_DIMENSIONS);
        vdMap.put(7, VideoDimensions.HD_S1080P_VIDEO_DIMENSIONS);
        vdMap.put(8, VideoDimensions.HD_1080P_VIDEO_DIMENSIONS);
        videoDimensionsMap = Collections.unmodifiableMap(vdMap);
    }

    /**
     * FIXME
     * This is a result of not being able to use explicit intents with dynamically registered
     * receivers. So what we do is have this receiver rebroadcast via the LocalBroadcastManager
     * so that that explicit intent can reach our dynamically registered receiver that
     * performs the rejection. This is pretty bad and would be avoidable if the IncomingInvite
     * was parcelable. If this were true we could just pass the invite in a bundle.
     *
     * For documentation on this hack see...
     *
     * http://streamingcon.blogspot.com/2014/04/dynamic-broadcastreceiver-registration.html
     */
    public static class Rebroadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.d("Received broadcast from notification");
            LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);
            if (manager == null)
                return;
            Intent modifiedIntent = new Intent(intent);
            modifiedIntent.setAction(ACTION_REJECT_INCOMING_CALL);
            modifiedIntent.setComponent(null);
            manager.sendBroadcast(modifiedIntent);
        }
    }

    /**
     * Here is the actual receiver that performs the reject when the app is in the background
     */
    public class RejectIncomingCallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (incomingInvite != null) {
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                rejectInvite(incomingInvite);
                notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
            }
        }
    }
    private final RejectIncomingCallReceiver rejectIncomingInviteReceiver =
            new RejectIncomingCallReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // So calls can be answered when screen is locked
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_client);

        previewFrameLayout = (FrameLayout) findViewById(R.id.previewFrameLayout);
        localData = new LocalData();
        localData.container = createParticipantContainer();
        mainVideoContainer = (ViewGroup)findViewById(R.id.mainVideoContainer);
        videoLinearLayout = (LinearLayout)findViewById(R.id.videoLinearLayout);
        videoLinearLayout.removeAllViews();


        participantDataMap = new HashMap<>();

        callActionFab = (FloatingActionButton)findViewById(R.id.call_action_fab);
        callActionFab.hide();
        conversationsClientStatusTextView = (TextView) findViewById(R.id.conversations_client_status_textview);
        conversationStatusTextView = (TextView) findViewById(R.id.conversation_status_textview);
        switchCameraActionFab = (FloatingActionButton)findViewById(R.id.switch_camera_action_fab);
        localVideoActionFab = (FloatingActionButton)findViewById(R.id.local_video_action_fab);
        pauseActionFab = (FloatingActionButton)findViewById(R.id.local_video_pause_fab);
        audioActionFab = (FloatingActionButton)findViewById(R.id.audio_action_fab);
        muteActionFab = (FloatingActionButton)findViewById(R.id.local_audio_mute_fab);

        addParticipantActionFab = (FloatingActionButton)findViewById(R.id.add_participant_action_fab);
        speakerActionFab = (FloatingActionButton)findViewById(R.id.speaker_action_fab);

        statsCheckBox = (CheckBox)findViewById(R.id.enable_stats_checkbox);
        statsLayout = (LinearLayout)findViewById(R.id.stats_layout);
        statsLayout.setVisibility(View.INVISIBLE);

        remoteStatsRecyclerView = (RecyclerView) findViewById(R.id.stats_recycler_view);
        remoteVideoTrackStatsAdapter = new RemoteVideoTrackStatsAdapter(remoteVideoTrackStatsRecordMap);
        remoteStatsRecyclerView.setAdapter(remoteVideoTrackStatsAdapter);
        remoteStatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        remoteStatsRecyclerView.setItemAnimator(new DefaultItemAnimator());

        localVideoTrackStatsTextView = (TextView)findViewById(R.id.local_video_track_stats_textview);

        statsCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(conversation != null) {
                    if(isChecked) {
                        enableStats();
                    } else {
                        disableStats();
                    }
                }
            }
        });

        DrawerLayout drawerLayout = (DrawerLayout)findViewById(R.id.navigation_drawer);

        final RangeBar fpsRangeBar = (RangeBar)findViewById(R.id.fps_rangebar);
        final RangeBar videoDimensionsRangeBar = (RangeBar)findViewById(R.id.video_dimensions_rangebar);

        videoDimensionsRangeBar.setTickStart(1);
        videoDimensionsRangeBar.setTickEnd(videoDimensionsMap.size());

        drawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                minFps = Integer.valueOf(fpsRangeBar.getLeftPinValue());
                maxFps = Integer.valueOf(fpsRangeBar.getRightPinValue());
                minVideoDimensions = videoDimensionsMap.get(videoDimensionsRangeBar.getLeftIndex());
                maxVideoDimensions = videoDimensionsMap.get(videoDimensionsRangeBar.getRightIndex());
                aspectRatio = getAspectRatio();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                // Update video constraints
                try {
                    aspectRatio = getAspectRatio();
                    videoConstraints = new VideoConstraints.Builder()
                            .minFps(minFps)
                            .maxFps(maxFps)
                            .minVideoDimensions(minVideoDimensions)
                            .maxVideoDimensions(maxVideoDimensions)
                            .aspectRatio(aspectRatio)
                            .build();
                } catch(Exception e) {
                    Snackbar.make(
                            conversationStatusTextView,
                            e.getMessage(),
                            Snackbar.LENGTH_LONG)
                            .setAction("Action", null)
                            .show();
                    videoConstraints = null;
                }

                Timber.i("Video Constraints Fps " + minFps + " " + maxFps);
                Timber.i("Video Constraints MinVD " +
                        minVideoDimensions.width + " " + minVideoDimensions.height);
                Timber.i("Video Constraints MaxVD " +
                        maxVideoDimensions.width + " " + maxVideoDimensions.height);
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });

        fpsRangeBar.setOnRangeBarChangeListener(new RangeBar.OnRangeBarChangeListener() {
            @Override
            public void onRangeChangeListener(RangeBar rangeBar, int leftPinIndex,
                                              int rightPinIndex, String leftPinValue,
                                              String rightPinValue) {
                minFps = Integer.valueOf(leftPinValue);
                maxFps = Integer.valueOf(rightPinValue);
            }
        });

        videoDimensionsRangeBar.setOnRangeBarChangeListener(
                new RangeBar.OnRangeBarChangeListener() {
                    @Override
                    public void onRangeChangeListener(RangeBar rangeBar, int leftPinIndex,
                                                      int rightPinIndex, String leftPinValue,
                                                      String rightPinValue) {
                        minVideoDimensions = videoDimensionsMap.get(leftPinIndex);
                        maxVideoDimensions = videoDimensionsMap.get(rightPinIndex);
                    }
                });

        videoDimensionsRangeBar.setFormatter(new IRangeBarFormatter() {
            @Override
            public String format(String value) {
                int position = Integer.decode(value) - 1;
                VideoDimensions videoDimensions = videoDimensionsMap.get(position);
                return String.valueOf(videoDimensions.width) + ":"  +
                        String.valueOf(videoDimensions.height);
            }
        });

        if (savedInstanceState != null) {
            Timber.d("Restoring client activity state");
            username = savedInstanceState.getString(SimpleSignalingUtils.USERNAME);
            realm = savedInstanceState.getString(SimpleSignalingUtils.REALM);
            preferH264 = savedInstanceState.getBoolean(OPTION_PREFER_H264_KEY);
            autoAccept = savedInstanceState.getBoolean(OPTION_AUTO_ACCEPT_KEY);
            useHeadset = savedInstanceState.getBoolean(OPTION_USE_HEADSET_KEY);
            logoutWhenConvEnds = savedInstanceState.getBoolean(OPTION_LOGOUT_WHEN_CONV_ENDS_KEY);
            capabilityToken = savedInstanceState.getString(SimpleSignalingUtils.CAPABILITY_TOKEN);
            selectedTwilioIceServersJson = savedInstanceState
                    .getString(TwilioIceResponse.ICE_SELECTED_SERVERS);
            iceTransportPolicy = savedInstanceState
                    .getString(TwilioIceResponse.ICE_TRANSPORT_POLICY);
            twilioIceServersJson = savedInstanceState.getString(TwilioIceResponse.ICE_SERVERS);
        } else {
            Bundle extras = getIntent().getExtras();

            username = extras.getString(SimpleSignalingUtils.USERNAME);
            realm = extras.getString(SimpleSignalingUtils.REALM);
            preferH264 = extras.getBoolean(OPTION_PREFER_H264_KEY);
            autoAccept = extras.getBoolean(OPTION_AUTO_ACCEPT_KEY);
            useHeadset = extras.getBoolean(OPTION_USE_HEADSET_KEY);
            logoutWhenConvEnds = extras.getBoolean(OPTION_LOGOUT_WHEN_CONV_ENDS_KEY);
            capabilityToken = extras.getString(SimpleSignalingUtils.CAPABILITY_TOKEN);
            selectedTwilioIceServersJson = extras.getString(TwilioIceResponse.ICE_SELECTED_SERVERS);
            iceTransportPolicy = extras.getString(TwilioIceResponse.ICE_TRANSPORT_POLICY);
            twilioIceServersJson = extras.getString(TwilioIceResponse.ICE_SERVERS);
        }

        Map<String, String> privateOptions = createPrivateOptions(realm);
        IceOptions iceOptions = retrieveIceOptions();
        ClientOptionsInternal options = new ClientOptionsInternal(iceOptions, privateOptions);
        setIceOptionsViews();
        getSupportActionBar().setTitle(username);
        iceOptionsLayout = (RelativeLayout) findViewById(R.id.ice_options_layout);
        iceOptionsLayout.setVisibility(View.GONE);
        enableIceCheckbox = (CheckBox)findViewById(R.id.enable_ice_checkbox);
        enableIceCheckbox.setChecked(false);
        enableIceCheckbox.setOnCheckedChangeListener(enableIceCheckedChangeListener());
        preferH264Checkbox = (CheckBox) findViewById(R.id.prefer_h264_checkbox);
        preferH264Checkbox.setChecked(preferH264);
        logoutWhenConvEndsCheckbox = (CheckBox) findViewById(R.id.logout_when_conv_ends_checkbox);
        logoutWhenConvEndsCheckbox.setChecked(logoutWhenConvEnds);

        aspectRatioSpinner = (Spinner)findViewById(R.id.aspect_ratio_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.aspect_ratio_array, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aspectRatioSpinner.setAdapter(spinnerAdapter);

        accessManager = TwilioAccessManagerFactory.createAccessManager(this, capabilityToken,
                accessManagerListener());

        twilioConversationsClient = TwilioConversationsClient.create(accessManager, options,
                conversationsClientListener());


        cameraCapturer = CameraCapturer.create(ClientActivity.this, currentCameraSource,
                capturerErrorListener());

        switchCameraActionFab.setOnClickListener(switchCameraClickListener());

        audioState = AudioState.ENABLED;
        setAudioStateIcon();
        videoState = VideoState.ENABLED;
        setVideoStateIcon();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        setSpeakerphoneOn(!useHeadset);
        setCallAction();
        startPreview();
        registerRejectReceiver();
    }


    @Override
    public void onBackPressed() {
        /**
         * FIXME
         * Again another hack for the fact that there is so much state in this
         * activity. Need to have invites be parcelable so that activities and services
         * can use bundles to pass data around
         */
        moveTaskToBack(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        NotificationManager notificationManager  =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        /**
         * FIXME
         * This will only occur when new invite has been accepted in the background.
         * However, this is a little bit of a hack. We need to make the IncomingInvite
         * Parcelable so the developer does not have to maintain so much state
         */
        localMedia = createLocalMedia();
        acceptInvite(incomingInvite);
        setHangupAction();
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.client_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_log_out:
                // Will continue logout once the conversation has ended
                loggingOut = true;

                // End any current call
                if (isConversationOngoing()) {
                    hangup();
                } else {
                    logout();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        Timber.d("Saving client activity state");
        bundle.putString(SimpleSignalingUtils.USERNAME, username);
        bundle.putString(SimpleSignalingUtils.CAPABILITY_TOKEN, capabilityToken);
        bundle.putString(SimpleSignalingUtils.REALM, realm);
        bundle.putBoolean(OPTION_PREFER_H264_KEY, preferH264);
        bundle.putBoolean(OPTION_AUTO_ACCEPT_KEY, autoAccept);
        bundle.putBoolean(OPTION_USE_HEADSET_KEY, useHeadset);
        bundle.putBoolean(OPTION_LOGOUT_WHEN_CONV_ENDS_KEY, logoutWhenConvEnds);
        bundle.putString(TwilioIceResponse.ICE_SELECTED_SERVERS, selectedTwilioIceServersJson);
        bundle.putString(TwilioIceResponse.ICE_TRANSPORT_POLICY, iceTransportPolicy);
        bundle.putString(TwilioIceResponse.ICE_SERVERS, twilioIceServersJson);
    }

    @Override
    protected void onStart() {
        super.onStart();

        inBackground = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (twilioConversationsClient != null && !twilioConversationsClient.isListening()) {
            twilioConversationsClient.listen();
        }
        if(cameraCapturer != null && wasPreviewing) {
            wasPreviewing = false;
            startPreview();
        } else if(isConversationOngoing()) {
            LocalVideoTrack localVideoTrack = localMedia.getLocalVideoTracks().get(0);
            if(localVideoTrack != null && wasLive) {
                localVideoTrack.enable(true);
                wasLive = false;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraCapturer != null && cameraCapturer.isPreviewing()) {
            wasPreviewing = true;
            stopPreview();
        } else if(isConversationOngoing() && !localMedia.getLocalVideoTracks().isEmpty()) {
            LocalVideoTrack localVideoTrack = localMedia.getLocalVideoTracks().get(0);
            if(localVideoTrack != null && localVideoTrack.isEnabled()) {
                localVideoTrack.enable(false);
                wasLive = true;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        inBackground = true;
    }

    @Override
    protected void onDestroy() {
        unregisterRejectReceiver();
        super.onDestroy();
    }

    private ViewGroup createParticipantContainer() {
        int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90,
                getResources().getDisplayMetrics());
        int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90,
                getResources().getDisplayMetrics());
        RelativeLayout.LayoutParams layoutParams =  new RelativeLayout.LayoutParams(width, height);
        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(layoutParams);
        layout.setClickable(true);
        layout.setOnClickListener(participantContainerClickListener());
        return layout;
    }

    private Map<String, String> createPrivateOptions(String realm) {
        Map<String, String> options = new HashMap<>();
        if (realm.equalsIgnoreCase("dev")) {
            options.put(OPTION_REGISTRAR_KEY, OPTION_DEV_REGISTRAR);
            options.put(OPTION_STATS_KEY, OPTION_DEV_STATS_URL);
        } else if (realm.equalsIgnoreCase("stage")) {
            options.put(OPTION_REGISTRAR_KEY, OPTION_STAGE_REGISTRAR);
            options.put(OPTION_STATS_KEY, OPTION_STAGE_STATS_URL);
        }
        options.put(OPTION_PREFER_H264_KEY, preferH264 ? "true" : "false");
        return options;
    }

    private void startPreview() {
        if (cameraCapturer != null) {
            cameraCapturer.startPreview(previewFrameLayout);
        }
    }

    private void stopPreview() {
        if(cameraCapturer != null && cameraCapturer.isPreviewing()) {
            cameraCapturer.stopPreview();
        }
    }

    private boolean isConversationOngoing() {
        return conversation != null ||
                outgoingInvite != null;
    }

    private void logout() {
        // Teardown preview
        if (cameraCapturer != null && cameraCapturer.isPreviewing()) {
            stopPreview();
            cameraCapturer = null;
        }

        // Teardown our conversation, client, and sdk instance
        disposeConversation();

        // Lets unlisten first otherwise complete logout
        if (twilioConversationsClient != null && twilioConversationsClient.isListening()) {
            twilioConversationsClient.unlisten();
        } else {
            completeLogout();
        }
    }

    private void completeLogout() {
        disposeConversationsClient();
        destroyConversationsSdk();
        disposeAccessManager();
        returnToRegistration();
        loggingOut = false;
    }


    private void disposeConversation() {
        if (conversation != null) {
            conversation = null;
        }
    }

    private void disposeConversationsClient() {
        if (twilioConversationsClient != null) {
            twilioConversationsClient = null;
        }
    }

    private void destroyConversationsSdk() {
        TwilioConversationsClient.destroy();
    }

    private void disposeAccessManager() {
        if (accessManager != null) {
            accessManager.dispose();
            accessManager = null;
        }
    }

    private void returnToRegistration() {
        Intent registrationIntent = new Intent(ClientActivity.this, RegistrationActivity.class);
        registrationIntent.putExtra(RegistrationActivity.OPTION_LOGGED_OUT_KEY, true);
        startActivity(registrationIntent);
        finish();
    }

    private void setCallAction() {
        callActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_white_24px));
        callActionFab.show();
        callActionFab.setOnClickListener(callClickListener());
        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener());
        audioActionFab.show();
        audioActionFab.setOnClickListener(audioClickListener());
        addParticipantActionFab.hide();
        speakerActionFab.hide();
    }

    private void setHangupAction() {
        callActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        callActionFab.show();
        callActionFab.setOnClickListener(hangupClickListener());
        addParticipantActionFab.show();
        addParticipantActionFab.setOnClickListener(addClickListener());
        speakerActionFab.show();
        speakerActionFab.setOnClickListener(speakerClickListener());
    }

    private void hideAction() {
        callActionFab.hide();
        speakerActionFab.hide();
    }

    private IceOptions retrieveIceOptions() {
        //Transform twilio ice servers from json to Set<IceServer>
        List<TwilioIceServer> selectedIceServers =
                IceOptionsHelper.convertToTwilioIceServerList(selectedTwilioIceServersJson);
        Set<IceServer> iceServers = IceOptionsHelper.convertToIceServersSet(selectedIceServers);
        IceTransportPolicy transPolicy  = IceTransportPolicy.ICE_TRANSPORT_POLICY_ALL;

        if (iceTransportPolicy.equalsIgnoreCase("relay") ) {
            transPolicy = IceTransportPolicy.ICE_TRANSPORT_POLICY_RELAY;
        }
        if (iceServers.size() > 0) {
            return new IceOptions(transPolicy, iceServers);
        }

        return new IceOptions(transPolicy);
    }

    private void setIceOptionsViews(){
        iceTransPolicySpinner = (Spinner)findViewById(R.id.ice_trans_policy_spinner);
        ArrayAdapter<CharSequence> iceTransPolicyArrayAdapter = ArrayAdapter.createFromResource(
                this, R.array.ice_trans_policy_array, android.R.layout.simple_spinner_item);
        iceTransPolicyArrayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        iceTransPolicySpinner.setAdapter(iceTransPolicyArrayAdapter);
        List<TwilioIceServer> twilioIceServers =
                IceOptionsHelper.convertToTwilioIceServerList(twilioIceServersJson);
        twilioIceServersListView = (ListView)findViewById(R.id.ice_servers_list_view);

        if (twilioIceServers.size() > 0) {
            IceServerAdapter iceServerAdapter =
                    new IceServerAdapter(this, twilioIceServers);
            twilioIceServersListView.setAdapter(iceServerAdapter);
        } else {
            // We are going to obtain list of servers anyway
            SimpleSignalingUtils.getIceServers(realm, new Callback<TwilioIceResponse>() {
                @Override
                public void success(TwilioIceResponse twilioIceResponse, Response response) {
                    IceServerAdapter iceServerAdapter =
                            new IceServerAdapter(ClientActivity.this,
                                    twilioIceResponse.getIceServers());
                    twilioIceServersListView.setAdapter(iceServerAdapter);
                }

                @Override
                public void failure(RetrofitError error) {
                    Timber.w(error.getMessage());
                }
            });
        }
    }

    private IceOptions createIceOptions() {
        if (!enableIceCheckbox.isChecked()) {
            return null;
        }
        List<TwilioIceServer> selectedTwIceServers =
                IceOptionsHelper.getSelectedServersFromListView(twilioIceServersListView);
        IceTransportPolicy policy = IceOptionsHelper.convertToIceTransportPolicy(
                iceTransPolicySpinner.getSelectedItem().toString());
        if (selectedTwIceServers.size() > 0) {
            Set<IceServer> iceServers =
                    IceOptionsHelper.convertToIceServersSet(selectedTwIceServers);
            return new IceOptions(policy, iceServers);
        }
        return new IceOptions(policy);
    }

    private CompoundButton.OnCheckedChangeListener enableIceCheckedChangeListener() {
        return new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    iceOptionsLayout.setVisibility(View.VISIBLE);
                } else {
                    iceOptionsLayout.setVisibility(View.GONE);
                }
            }
        };
    }

    private TwilioConversationsClient.Listener conversationsClientListener() {
        return new TwilioConversationsClient.Listener() {
            @Override
            public void onStartListeningForInvites(TwilioConversationsClient twilioConversationsClient) {
                conversationsClientStatusTextView.setText("onStartListeningForInvites");
            }

            @Override
            public void onStopListeningForInvites(TwilioConversationsClient twilioConversationsClient) {
                conversationsClientStatusTextView.setText("onStopListeningForInvites");
                // If we are logging out let us finish the teardown process
                if (loggingOut || logoutWhenConvEnds) {
                    completeLogout();
                }
            }

            @Override
            public void onFailedToStartListening(TwilioConversationsClient twilioConversationsClient,
                                                 TwilioConversationsException e) {
                Timber.e(e.getMessage());
                conversationsClientStatusTextView
                        .setText("onFailedToStartListening: " + e.getMessage());
            }

            @Override
            public void onIncomingInvite(TwilioConversationsClient twilioConversationsClient,
                                         IncomingInvite incomingInvite) {
                ClientActivity.this.incomingInvite = incomingInvite;
                if (!inBackground) {
                    conversationsClientStatusTextView
                            .setText("onIncomingInvite" + incomingInvite.getInviter());
                    if(autoAccept) {
                        showAutoAcceptInviteDialog(incomingInvite);
                    } else {
                        showInviteDialog(incomingInvite);
                    }
                } else {
                    NotificationManager notificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                    /*
                     * Pending intents are often reused and this results in some not being
                     * triggered correctly so we explicitly cancel any existing intents first.
                     * This is a known bug and workaround seen at
                     *
                     * https://code.google.com/p/android/issues/detail?id=61850
                     */
                    getRejectPendingIntent().cancel();
                    getAcceptPendingIntent().cancel();
                    PendingIntent rejectPendingIntent = getRejectPendingIntent();

                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(ClientActivity.this)
                                    .setSmallIcon(R.drawable.ic_videocam_green_24px)
                                    .setDeleteIntent(rejectPendingIntent)
                                    .setContentTitle(incomingInvite.getInviter())
                                    .setPriority(NotificationCompat.PRIORITY_MAX)
                                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                                    .setCategory(NotificationCompat.CATEGORY_CALL)
                                    .setShowWhen(true)
                                    .addAction(0, "Decline", rejectPendingIntent)
                                    .addAction(0, "Accept", getAcceptPendingIntent())
                                    .setContentText(getString(R.string.incoming_call));


                    notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID,
                            mBuilder.build());
                }
            }

            @Override
            public void onIncomingInviteCancelled(TwilioConversationsClient twilioConversationsClient,
                                                  IncomingInvite incomingInvite) {
                ClientActivity.this.incomingInvite = null;
                if (!inBackground) {
                    alertDialog.dismiss();
                    Snackbar.make(conversationStatusTextView, "Invite from " +
                            incomingInvite.getInviter() + " terminated", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    NotificationManager notificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
                }
            }
        };
    }

    private TwilioAccessManagerListener accessManagerListener() {
        return new TwilioAccessManagerListener() {
            /*
             *  The token expiration event notifies the developer 3 minutes before
             *  token actually expires to allow the developer to request a new token
             */
            @Override
            public void onTokenExpired(TwilioAccessManager twilioAccessManager) {
                Timber.d("onAccessManagerTokenExpire");
                conversationsClientStatusTextView.setText("onAccessManagerTokenExpire");
                obtainCapabilityToken();
            }

            @Override
            public void onTokenUpdated(TwilioAccessManager twilioAccessManager) {
                conversationsClientStatusTextView.setText("onAccessManagerTokenUpdated");
            }

            @Override
            public void onError(TwilioAccessManager twilioAccessManager, String s) {
                Timber.e(s);
                conversationsClientStatusTextView.setText("onAccessManagerTokenError: " + s);
            }
        };
    }

    private View.OnClickListener callClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideAction();
                showCallDialog();
            }
        };
    }

    private View.OnClickListener hangupClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hangup();
                reset();
            }
        };
    }

    private View.OnClickListener switchCameraClickListener() {
        return new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if(cameraCapturer != null) {
                    boolean cameraSwitchSucceeded = cameraCapturer.switchCamera();

                    if (cameraSwitchSucceeded) {
                        // Update the camera source
                        currentCameraSource =
                                (currentCameraSource ==
                                        CameraCapturer.CameraSource.CAMERA_SOURCE_FRONT_CAMERA) ?
                                        (CameraCapturer.CameraSource.CAMERA_SOURCE_BACK_CAMERA) :
                                        (CameraCapturer.CameraSource.CAMERA_SOURCE_FRONT_CAMERA);

                        // Update our local renderer to mirror or not
                        mirrorLocalRenderer = (currentCameraSource ==
                                CameraCapturer.CameraSource.CAMERA_SOURCE_FRONT_CAMERA);

                        // Determine if our renderer is mirroring now
                        if (localData.renderer != null) {
                            localData.renderer.setMirror(mirrorLocalRenderer);
                        }
                    }
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (videoState == VideoState.DISABLED) {
                    cameraCapturer.startPreview(previewFrameLayout);
                    if (localMedia != null) {
                        localData.container = createParticipantContainer();
                        LocalVideoTrack videoTrack = createLocalVideoTrack(cameraCapturer);
                        localMedia.addLocalVideoTrack(videoTrack);
                        localData.localVideoTrack = videoTrack;
                        switchCameraActionFab.hide();
                    } else {
                        videoState = VideoState.ENABLED;
                    }
                } else if (videoState == VideoState.ENABLED) {
                    cameraCapturer.stopPreview();
                    if (localMedia != null) {
                        if (localMedia.getLocalVideoTracks().size() > 0) {
                            localMedia.removeLocalVideoTrack(localMedia
                                    .getLocalVideoTracks().get(0));
                            pauseActionFab.hide();
                        }
                    } else {
                        videoState = VideoState.DISABLED;
                    }
                }
                setVideoStateIcon();
            }
        };
    }

    public void pauseVideo() {
        List<LocalVideoTrack> videoTracks =
                localMedia.getLocalVideoTracks();
        if (videoTracks.size() > 0) {
            LocalVideoTrack videoTrack = videoTracks.get(0);
            boolean enable = !videoTrack.isEnabled();
            boolean set = videoTrack.enable(enable);
            if(set) {
                switchCameraActionFab.setEnabled(videoTrack.isEnabled());
                if (videoTrack.isEnabled()) {
                    pauseActionFab.setImageDrawable(
                            ContextCompat.getDrawable(ClientActivity.this,
                                    R.drawable.ic_pause_green_24px));
                } else {
                    pauseActionFab.setImageDrawable(
                            ContextCompat.getDrawable(ClientActivity.this,
                                    R.drawable.ic_pause_red_24px));
                }
            } else {
                Snackbar.make(conversationStatusTextView,
                        "Pause action failed",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        } else {
            Timber.w("Camera is not present. Unable to pause");
        }
    }

    private void setVideoStateIcon() {
        if (videoState == VideoState.ENABLED) {
            switchCameraActionFab.show();
            localVideoActionFab.setImageDrawable(
                    ContextCompat.getDrawable(ClientActivity.this,
                            R.drawable.ic_videocam_white_24px));
        } else {
            switchCameraActionFab.hide();
            localVideoActionFab.setImageDrawable(
                    ContextCompat.getDrawable(ClientActivity.this,
                            R.drawable.ic_videocam_off_gray_24px));
        }
        if(localMedia != null && localMedia.getLocalVideoTracks().size() > 0 ) {
            if(localMedia.getLocalVideoTracks().get(0).isEnabled()) {
                pauseActionFab.setImageDrawable(
                        ContextCompat.getDrawable(ClientActivity.this,
                                R.drawable.ic_pause_green_24px));
            } else {
                pauseActionFab.setImageDrawable(
                        ContextCompat.getDrawable(ClientActivity.this,
                                R.drawable.ic_pause_red_24px));
            }
            pauseActionFab.show();
        } else {
            pauseActionFab.hide();
        }
    }

    private View.OnClickListener audioClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioState == AudioState.DISABLED)  {
                    if (localMedia != null) {
                        boolean microphoneAdded = localMedia.addMicrophone();
                        if(microphoneAdded) {
                            audioState = AudioState.ENABLED;
                        } else {
                            Snackbar.make(conversationStatusTextView, "Adding microphone failed",
                                    Snackbar.LENGTH_LONG).setAction("Action", null).show();
                        }
                    } else {
                        audioState = AudioState.ENABLED;
                    }
                } else {
                    if (localMedia != null) {
                        boolean microphoneRemoved = localMedia.removeMicrophone();
                        if(microphoneRemoved) {
                            audioState = AudioState.DISABLED;
                        } else {
                            Snackbar.make(conversationStatusTextView, "Removing microphone failed",
                                    Snackbar.LENGTH_LONG).setAction("Action", null).show();
                        }
                    } else {
                        audioState = AudioState.DISABLED;
                    }
                }
                setAudioStateIcon();
            }
        };
    }

    private void setAudioStateIcon() {
        if (audioState == AudioState.ENABLED) {
            audioActionFab.setImageDrawable(
                    ContextCompat.getDrawable(ClientActivity.this,
                            R.drawable.ic_mic_white_24px));
        } else if (audioState == AudioState.DISABLED) {
            audioActionFab.setImageDrawable(
                    ContextCompat.getDrawable(ClientActivity.this,
                            R.drawable.ic_mic_off_gray_24px));
        }
        if(audioState == AudioState.ENABLED && localMedia != null &&
                localMedia.isMicrophoneAdded()) {
            muteActionFab.show();
        } else {
            muteActionFab.hide();
        }
    }

    private void muteAudio() {
        boolean enable = !localMedia.isMuted();
        boolean set = localMedia.mute(enable);
        if(set) {
            if (enable) {
                muteActionFab.setImageDrawable(
                        ContextCompat.getDrawable(ClientActivity.this,
                                R.drawable.ic_mic_red_24px));
            } else {
                muteActionFab.setImageDrawable(
                        ContextCompat.getDrawable(ClientActivity.this,
                                R.drawable.ic_mic_green_24px));
            }
        } else {
            Snackbar.make(conversationStatusTextView, "Mute action failed", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }

    private View.OnClickListener addClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddParticipantsDialog();
            }
        };
    }

    private void showAddParticipantsDialog() {
        participantEditText = new EditText(this);
        alertDialog = Dialog.createAddParticipantsDialog(participantEditText,
                addParticipantsClickListener(participantEditText),
                cancelAddParticipantsClickListener(),
                this);
        alertDialog.show();
    }

    private void showCallDialog() {
        participantEditText = new EditText(this);
        alertDialog = Dialog.createCallParticipantsDialog(participantEditText,
                callParticipantClickListener(participantEditText),
                cancelCallClickListener(),
                this);
        alertDialog.show();
    }

    private void hangup() {
        if(conversation != null) {
            conversation.disconnect();
        } else if(outgoingInvite != null){
            outgoingInvite.cancel();
        }
    }

    private DialogInterface.OnClickListener callParticipantClickListener(final EditText participantEditText) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                stopPreview();
                Set<String> participants = ParticipantParser.getParticipants(participantEditText.getText().toString());
                if(participants.size() > 0) {

                    localMedia = createLocalMedia();

                    IceOptions iceOptions = createIceOptions();
                    outgoingInvite = twilioConversationsClient.sendConversationInvite(participants,
                            localMedia, iceOptions, new ConversationCallback() {
                                @Override
                                public void onConversation(Conversation conversation, TwilioConversationsException e) {
                                    Timber.e("sendConversationInvite onConversation");
                                    if (e == null) {
                                        Timber.i("Conversation SID " + conversation.getSid());
                                        ClientActivity.this.conversation = conversation;
                                        conversation.setConversationListener(conversationListener());
                                        if(statsCheckBox.isChecked()) {
                                            enableStats();
                                        }
                                    } else {
                                        if (e.getErrorCode() == TwilioConversationsClient.CONVERSATION_REJECTED) {
                                            Snackbar.make(conversationStatusTextView,
                                                    "Invite rejected", Snackbar.LENGTH_LONG)
                                                    .setAction("Action", null).show();
                                        } else if (e.getErrorCode() == TwilioConversationsClient.CONVERSATION_IGNORED) {
                                            Snackbar.make(conversationStatusTextView,
                                                    "Invite ignored", Snackbar.LENGTH_LONG)
                                                    .setAction("Action", null).show();
                                        } else  {
                                            Snackbar.make(conversationStatusTextView,
                                                    e.getMessage(), Snackbar.LENGTH_LONG)
                                                    .setAction("Action", null).show();
                                        }

                                        if (!loggingOut) {
                                            hangup();
                                            reset();
                                        } else {
                                            logout();
                                        }
                                    }
                                }
                            });
                    if (outgoingInvite != null) {
                        setHangupAction();
                    }

                } else {
                    participantEditText.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }
        };
    }

    private void enableStats() {
        if(conversation != null) {
            conversation.setStatsListener(statsListener());
        }
    }

    private void disableStats() {
        if(conversation != null) {
            conversation.setStatsListener(null);
        }
        if(remoteVideoTrackStatsRecordMap != null) {
            remoteVideoTrackStatsRecordMap.clear();
        }
        statsLayout.setVisibility(View.GONE);
        remoteStatsRecyclerView.setVisibility(View.GONE);
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                setCallAction();
                alertDialog.dismiss();
            }
        };
    }

    private DialogInterface.OnClickListener addParticipantsClickListener(final EditText participantEditText) {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (conversation == null) {
                    return;
                }
                Set<String> participants = ParticipantParser.getParticipants(participantEditText.getText().toString());
                if(participants.size() > 0) {
                    conversation.invite(participants);
                } else {
                    participantEditText.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }
        };
    }

    private DialogInterface.OnClickListener cancelAddParticipantsClickListener() {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                alertDialog.dismiss();
            }
        };
    }

    private void showInviteDialog(final IncomingInvite incomingInvite) {
        alertDialog = Dialog.createInviteDialog(
                incomingInvite.getInviter(),
                incomingInvite.getConversationSid(),
                acceptCallClickListener(incomingInvite),
                rejectCallClickListener(incomingInvite),
                this);
        alertDialog.show();
    }

    private void showAutoAcceptInviteDialog(final IncomingInvite incomingInvite) {
        alertDialog = Dialog.createAutoAcceptInviteDialog(
        incomingInvite.getInviter(),
                incomingInvite.getConversationSid(),
                this);
        alertDialog.show();

        // Show the dialog and then automatically accept the call
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                alertDialog.dismiss();
                acceptCall();
            }
        }, 3000);
    }

    private DialogInterface.OnClickListener acceptCallClickListener(
            final IncomingInvite invite) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, int i) {
                acceptCall();
            }
        };
    }

    private void acceptCall() {
        localMedia = createLocalMedia();
        acceptInvite(incomingInvite);
        setHangupAction();
    }

    private void acceptInvite(IncomingInvite incomingInvite) {
        IceOptions iceOptions = createIceOptions();
        incomingInvite.accept(localMedia, iceOptions, new ConversationCallback() {
            @Override
            public void onConversation(Conversation conversation, TwilioConversationsException e) {
                Timber.i("onConversation");
                if (e == null) {
                    Timber.i("Conversation SID " + conversation.getSid());
                    ClientActivity.this.conversation = conversation;
                    conversation.setConversationListener(conversationListener());
                    if(statsCheckBox.isChecked()) {
                        enableStats();
                    }
                } else if (e.getErrorCode() == TwilioConversationsClient.TOO_MANY_ACTIVE_CONVERSATIONS) {
                    Timber.w(e.getMessage());
                    conversationsClientStatusTextView
                            .setText("Unable to accept call. Too many active conversations.");
                } else {
                    hangup();
                    reset();
                }
            }
        });
    }

    private DialogInterface.OnClickListener rejectCallClickListener(
            final IncomingInvite incomingInvite) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                rejectInvite(incomingInvite);
            }
        };
    }

    private void rejectInvite(IncomingInvite incomingInvite) {
        incomingInvite.reject();
        if (!isConversationOngoing()) {
            this.incomingInvite = null;
            setCallAction();
        }
    }

    private CapturerErrorListener capturerErrorListener() {
        return new CapturerErrorListener() {
            @Override
            public void onError(CapturerException e) {
                Timber.e(e.getMessage());
            }
        };
    }

    private void setSpeakerphoneOn(boolean on) {
        if (twilioConversationsClient == null) {
            Timber.e("Unable to set audio output, conversation client is null");
            return;
        }
        twilioConversationsClient.setAudioOutput(on ? AudioOutput.SPEAKERPHONE :
                AudioOutput.HEADSET);

        if (on == true) {
            Drawable drawable = ContextCompat.getDrawable(ClientActivity.this,
                    R.drawable.ic_volume_down_white_24px);
            speakerActionFab.setImageDrawable(drawable);
        } else {
            // route back to headset
            Drawable drawable = ContextCompat.getDrawable(ClientActivity.this,
                    R.drawable.ic_volume_down_gray_24px);
            speakerActionFab.setImageDrawable(drawable);
        }
    }

    private View.OnClickListener speakerClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (twilioConversationsClient == null) {
                    Timber.e("Unable to set audio output, conversation client is null");
                    return;
                }
                boolean speakerOn =
                        !(twilioConversationsClient.getAudioOutput() ==  AudioOutput.SPEAKERPHONE) ?  true : false;
                setSpeakerphoneOn(speakerOn);
            }
        };
    }

    private View.OnClickListener pauseClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseVideo();
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muteAudio();
            }
        };
    }

    private Conversation.Listener conversationListener() {
        return new Conversation.Listener() {
            @Override
            public void onParticipantConnected(Conversation conversation,
                                               Participant participant) {
                conversationStatusTextView.setText("onParticipantConnected " +
                        participant.getIdentity());
                videoLinearLayout.invalidate();

                participant.setParticipantListener(participantListener());

            }

            @Override
            public void onFailedToConnectParticipant(Conversation conversation,
                                                     Participant participant,
                                                     TwilioConversationsException e) {
                Timber.e(e.getMessage());
                conversationStatusTextView.setText("onFailedToConnectParticipant " +
                        participant.getIdentity());
            }

            @Override
            public void onParticipantDisconnected(Conversation conversation,
                                                  Participant participant) {
                conversationStatusTextView.setText("onParticipantDisconnected " +
                        participant.getIdentity());
            }

            @Override
            public void onConversationEnded(Conversation conversation,
                                            TwilioConversationsException e) {
                String status = "onConversationEnded";
                if (e != null) {
                    status += " " + e.getMessage();
                    if(e.getErrorCode() == TwilioConversationsClient.CONVERSATION_FAILED) {
                        Snackbar.make(conversationStatusTextView, "Invite failed " +
                                conversation.getSid(), Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    } else if(e.getErrorCode() == TwilioConversationsClient.CONVERSATION_REJECTED) {
                        Snackbar.make(conversationStatusTextView, "Invite was rejected " +
                                conversation.getSid(), Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                }
                conversationStatusTextView.setText(status);
                disableStats();

                // If user is logging out we need to finish that process otherwise we just reset
                if (loggingOut || logoutWhenConvEnds) {
                    logout();
                } else {
                    reset();
                }
            }

        };
    }

    private StatsListener statsListener() {
        return new StatsListener() {
            @Override
            public void onMediaTrackStatsRecord(Conversation conversation, Participant participant, MediaTrackStatsRecord stats) {
                StringBuilder strBld = new StringBuilder();
                strBld.append(
                        String.format("Receiving stats for sid: %s, trackId: %s, direction: %s ",
                                stats.getParticipantSid(), stats.getTrackId(),
                                stats.getDirection()));
                if (stats instanceof LocalAudioTrackStatsRecord) {
                    strBld.append(
                            String.format("media type: audio, bytes sent %d",
                                    ((LocalAudioTrackStatsRecord) stats).getBytesSent()));
                } else if (stats instanceof LocalVideoTrackStatsRecord) {
                    strBld.append(
                            String.format("media type: video, bytes sent %d",
                                    ((LocalVideoTrackStatsRecord) stats).getBytesSent()));
                } else if (stats instanceof RemoteAudioTrackStatsRecord) {
                    strBld.append(
                            String.format("media type: audio, bytes received %d",
                                    ((RemoteAudioTrackStatsRecord) stats).getBytesReceived()));
                } else if (stats instanceof RemoteVideoTrackStatsRecord) {
                    strBld.append(
                            String.format("media type: video, bytes received %d",
                                    ((RemoteVideoTrackStatsRecord) stats).getBytesReceived()));
                } else {
                    strBld.append("Unknown media type");
                }
                Timber.i(strBld.toString());

                if(stats instanceof LocalVideoTrackStatsRecord) {
                    if(statsLayout.getVisibility() != View.VISIBLE) {
                        statsLayout.setVisibility(View.VISIBLE);
                    }
                    if(conversation.getLocalMedia().getLocalVideoTracks().size() > 0) {
                        showLocalVideoTrackStats((LocalVideoTrackStatsRecord) stats);
                    } else {
                        /*
                         * Latent stats callbacks can be triggered even after a local track is
                         * removed.
                         */
                        statsLayout.setVisibility(View.GONE);
                    }
                } else if(stats instanceof RemoteVideoTrackStatsRecord) {
                    if(remoteStatsRecyclerView.getVisibility() != View.VISIBLE) {
                        remoteStatsRecyclerView.setVisibility(View.VISIBLE);
                    }
                    if(participant.getMedia().getVideoTracks().size() > 0) {
                        showRemoteVideoTrackStats(conversation, (RemoteVideoTrackStatsRecord)stats);
                    } else {
                        /*
                         * Latent stats callbacks can be triggered even after a remote track is
                         * removed.
                         */
                        remoteVideoTrackStatsRecordMap.clear();
                        remoteVideoTrackStatsAdapter.notifyDataSetChanged();
                    }
                }
            }
        };
    }

    private void showLocalVideoTrackStats(LocalVideoTrackStatsRecord localVideoTrackStatsRecord) {
        String localVideoStats =
                String.format("<b>SID</b> %s<br/>",
                        localVideoTrackStatsRecord.getParticipantSid()) + '\n' +
                        String.format("<b>Codec</b> %s<br/>",
                                localVideoTrackStatsRecord.getCodecName()) + '\n' +
                        String.format("<b>Capture Dimensions</b> %s<br/>",
                                localVideoTrackStatsRecord.getCaptureDimensions().toString()) +
                        '\n' + String.format("<b>Sent Dimensions</b> %s<br/>",
                                localVideoTrackStatsRecord.getSentDimensions().toString()) + '\n' +
                        String.format("<b>Fps</b> %d", localVideoTrackStatsRecord.getFrameRate());

        localVideoTrackStatsTextView.setText(Html.fromHtml(localVideoStats));
    }

    private void showRemoteVideoTrackStats(Conversation conversation,
                                           RemoteVideoTrackStatsRecord remoteVideoTrackStatsRecord) {
        for(Participant participant: conversation.getParticipants()) {
            if(participant.getSid().equals(remoteVideoTrackStatsRecord.getParticipantSid())) {
                remoteVideoTrackStatsRecordMap.put(participant.getIdentity(),
                        remoteVideoTrackStatsRecord);
                remoteVideoTrackStatsAdapter.notifyDataSetChanged();
                break;
            }
        }
    }

    private VideoViewRenderer createRendererForContainer(ViewGroup container,
                                                         VideoTrack videoTrack) {
        VideoViewRenderer renderer = new VideoViewRenderer(this, container);
        renderer.setVideoScaleType(VideoScaleType.ASPECT_FILL);
        renderer.setObserver(new VideoRenderer.Observer() {
            @Override
            public void onFirstFrame() {
                Timber.i("Participant onFirstFrame");
            }

            @Override
            public void onFrameDimensionsChanged(int width, int height, int rotation) {
                Timber.i("Participant onFrameDimensionsChanged [ width: " + width +
                        ", height: " + height +
                        ", rotation: " + rotation +
                        " ]");
            }
        });
        if (container == mainVideoContainer) {
            renderer.applyZOrder(false);
        } else {
            renderer.applyZOrder(true);
            videoLinearLayout.addView(container);
        }
        videoTrack.addRenderer(renderer);
        return renderer;
    }

    private void addParticipantToContainer(Participant participant, ViewGroup container) {
        VideoTrack videoTrack = participant.getMedia().getVideoTracks().get(0);
        VideoViewRenderer renderer = createRendererForContainer(container, videoTrack);
        participantDataMap.put(participant, new ParticipantData(container, renderer));
    }

    private void removeParticipantContainer(Participant participant) {
        // take out renderer and container for that particiant
        ParticipantData data = participantDataMap.remove(participant);
        // remove small container from linear layout
        if (data.container != mainVideoContainer) {
            videoLinearLayout.removeView(data.container);
        }
        data.container.removeAllViews();
        // remove renderer from participant video track
        if ((data.renderer != null) &&
                (participant.getMedia().getVideoTracks().size() > 0)) {
            VideoTrack vt = participant.getMedia().getVideoTracks().get(0);
            vt.removeRenderer(data.renderer);
        }
        data.renderer.release();
    }

    private Participant getParticipantFromContainer(ViewGroup container) {
        for (Map.Entry<Participant, ParticipantData> entry : participantDataMap.entrySet()) {
            if (entry.getValue().container == container) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void switchContainers(ViewGroup srcContainer, ViewGroup dstContainer) {
        Participant sourceParticipant = getParticipantFromContainer(srcContainer);
        Participant targetParticipant = getParticipantFromContainer(dstContainer);
        if (sourceParticipant != null && targetParticipant != null) {
            removeParticipantContainer(sourceParticipant);
            removeParticipantContainer(targetParticipant);
            addParticipantToContainer(sourceParticipant, dstContainer);
            addParticipantToContainer(targetParticipant, srcContainer);
        }
    }

    private void switchLocalContainer(ViewGroup participantContainer) {
        if (participantContainer != mainVideoContainer) {
            videoLinearLayout.removeView(participantContainer);
        }

        // Remove participant from main container
        Participant participant = getParticipantFromContainer(participantContainer);
        removeParticipantContainer(participant);

        // Remove local container from layout
        if (localData.container != mainVideoContainer) {
            videoLinearLayout.removeView(localData.container);
        }
        localData.localVideoTrack.removeRenderer(localData.renderer);
        localData.renderer.release();

        // Create local renderer and add it to participant container
        ViewGroup tmpContainer = localData.container;
        tmpContainer.removeAllViews();
        localData.renderer = createRendererForContainer(participantContainer, localData.localVideoTrack);
        localData.renderer.setMirror(mirrorLocalRenderer);
        localData.container = participantContainer;

        // Create participant container
        addParticipantToContainer(participant, tmpContainer);
    }

    private View.OnClickListener participantContainerClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewGroup view = (ViewGroup)v;
                if (view != mainVideoContainer) {
                    if (mainVideoContainer == localData.container) {
                        switchLocalContainer(view);
                    } else if (view == localData.container) {
                        switchLocalContainer(mainVideoContainer);
                    } else {
                        switchContainers(view, mainVideoContainer);
                    }
                }
            }
        };
    }

    private Participant.Listener participantListener() {
        return new Participant.Listener() {
            @Override
            public void onVideoTrackAdded(Conversation conversation,
                                          Participant participant,
                                          VideoTrack videoTrack) {
                Timber.i("onVideoTrackAdded " + participant.getIdentity());
                conversationStatusTextView.setText("onVideoTrackAdded " + participant.getIdentity());
                ViewGroup participantContainer = null;
                if (participantDataMap.isEmpty()) {
                    mainVideoContainer = (ViewGroup)findViewById(R.id.mainVideoContainer);
                    participantContainer = mainVideoContainer;
                } else {
                    participantContainer = createParticipantContainer();
                }
                addParticipantToContainer(participant, participantContainer);
            }

            @Override
            public void onVideoTrackRemoved(Conversation conversation,
                                            Participant participant,
                                            VideoTrack videoTrack) {
                Timber.i("onVideoTrackRemoved " + participant.getIdentity());
                conversationStatusTextView.setText("onVideoTrackRemoved " +
                        participant.getIdentity());
                ParticipantData data = participantDataMap.get(participant);
                if (data != null) {
                    if (data.container != mainVideoContainer) {
                        removeParticipantContainer(participant);
                    } else {
                        removeParticipantContainer(participant);
                        if (!participantDataMap.isEmpty()) {
                            // Take first available container
                            Map.Entry<Participant, ParticipantData> entry =
                                    participantDataMap.entrySet().iterator().next();
                            // grab participant associated with that container
                            Participant part = entry.getKey();
                            removeParticipantContainer(part);
                            mainVideoContainer = (ViewGroup) findViewById(R.id.mainVideoContainer);
                            addParticipantToContainer(part, mainVideoContainer);
                        }
                    }
                }

                remoteVideoTrackStatsRecordMap.remove(participant.getIdentity());
            }

            @Override
            public void onAudioTrackAdded(Conversation conversation,
                                          Participant participant,
                                          AudioTrack audioTrack) {
                Timber.i("onAudioTrackAdded " + participant.getIdentity());
            }

            @Override
            public void onAudioTrackRemoved(Conversation conversation,
                                            Participant participant,
                                            AudioTrack audioTrack) {
                Timber.i("onAudioTrackRemoved " + participant.getIdentity());
            }

            @Override
            public void onTrackEnabled(Conversation conversation,
                                       Participant participant,
                                       MediaTrack mediaTrack) {
                Timber.i("onTrackEnabled " + participant.getIdentity());

                for(VideoTrack videoTrack : participant.getMedia().getVideoTracks()) {
                    if(videoTrack.getTrackId().equals(mediaTrack.getTrackId())) {
                        ParticipantData data = participantDataMap.get(participant);
                        List<View> trackStatusViews = getViewsByTag(data.container,
                                mediaTrack.getTrackId());
                        for(View trackStatusView: trackStatusViews) {
                            data.container.removeView(trackStatusView);
                        }
                        break;
                    }
                }

                for(AudioTrack audioTrack: participant.getMedia().getAudioTracks()) {
                    if(audioTrack.getTrackId().equals(mediaTrack.getTrackId())) {
                        ParticipantData data = participantDataMap.get(participant);
                        /*
                         * If the conversation does not have a video track,
                         * there is no participant container available
                         */
                        if(data.container != null) {
                            List<View> trackStatusViews = getViewsByTag(data.container,
                                    mediaTrack.getTrackId());
                            for (View trackStatusView : trackStatusViews) {
                                data.container.removeView(trackStatusView);
                            }
                            break;
                        }
                    }
                }
            }

            private ArrayList<View> getViewsByTag(ViewGroup root, String tag){
                ArrayList<View> views = new ArrayList<>();
                final int childCount = root.getChildCount();
                for (int i = 0 ; i < childCount ; i++) {
                    final View child = root.getChildAt(i);
                    if (child instanceof ViewGroup) {
                        views.addAll(getViewsByTag((ViewGroup) child, tag));
                    }

                    final Object tagObj = child.getTag();
                    if (tagObj != null && tagObj.equals(tag)) {
                        views.add(child);
                    }

                }
                return views;
            }

            @Override
            public void onTrackDisabled(Conversation conversation,
                                        Participant participant,
                                        MediaTrack mediaTrack) {
                Timber.i("onTrackDisabled " + participant.getIdentity());

                for(VideoTrack videoTrack : participant.getMedia().getVideoTracks()) {
                    if(videoTrack.getTrackId().equals(mediaTrack.getTrackId())) {
                        ParticipantData data = participantDataMap.get(participant);
                        ImageView disabledView = new ImageView(ClientActivity.this);
                        disabledView.setTag(mediaTrack.getTrackId());
                        disabledView.setBackgroundResource(R.drawable.ic_videocam_off_red_24px);
                        RelativeLayout.LayoutParams layoutParams = new RelativeLayout
                                .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                        disabledView.setLayoutParams(layoutParams);
                        data.container.addView(disabledView);
                        break;
                    }
                }

                for(AudioTrack audioTrack : participant.getMedia().getAudioTracks()) {
                    if(audioTrack.getTrackId().equals(mediaTrack.getTrackId())) {
                        ParticipantData data = participantDataMap.get(participant);
                        /*
                         * If the conversation does not have a video track,
                         * there is no participant container available
                         */
                        if(data.container != null) {
                            ImageView disabledView = new ImageView(ClientActivity.this);
                            disabledView.setTag(mediaTrack.getTrackId());
                            disabledView.setBackgroundResource(R.drawable.ic_mic_off_red_24px);
                            RelativeLayout.LayoutParams layoutParams = new RelativeLayout
                                    .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                            disabledView.setLayoutParams(layoutParams);
                            data.container.addView(disabledView);
                        }
                        break;
                    }
                }

            }
        };
    }

    private LocalMedia.Listener localMediaListener() {
        return new LocalMedia.Listener() {
            @Override
            public void onLocalVideoTrackAdded(LocalMedia localMedia,
                                               LocalVideoTrack localVideoTrack) {
                videoState = VideoState.ENABLED;
                conversationStatusTextView.setText("onLocalVideoTrackAdded");
                ViewGroup localContainer = createParticipantContainer();
                VideoViewRenderer localRenderer = new VideoViewRenderer(ClientActivity.this,
                        localContainer);
                localRenderer.setVideoScaleType(VideoScaleType.ASPECT_FILL);
                localRenderer.setMirror(mirrorLocalRenderer);
                localVideoTrack.addRenderer(localRenderer);
                localData.container = localContainer;
                localData.renderer = localRenderer;
                localData.localVideoTrack = localVideoTrack;
                videoLinearLayout.addView(localContainer);
                setVideoStateIcon();
                pauseActionFab.setOnClickListener(pauseClickListener());
                setAudioStateIcon();
                muteActionFab.setOnClickListener(muteClickListener());
            }

            @Override
            public void onLocalVideoTrackRemoved(LocalMedia localMedia,
                                                 LocalVideoTrack localVideoTrack) {
                videoState = VideoState.DISABLED;
                conversationStatusTextView.setText("onLocalVideoTrackRemoved");
                localData.container.removeAllViews();
                videoLinearLayout.removeView(localData.container);
                if (localData.renderer != null) {
                    localData.renderer.release();
                }
                setVideoStateIcon();
                setAudioStateIcon();
                statsLayout.setVisibility(View.GONE);
            }

            @Override
            public void onLocalVideoTrackError(LocalMedia localMedia,
                                               LocalVideoTrack localVideoTrack,
                                               TwilioConversationsException e) {
                setVideoStateIcon();
                Snackbar.make(conversationStatusTextView, e.getMessage(), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }

        };
    }

    private void reset() {
        for (ParticipantData data : participantDataMap.values()) {
            data.renderer.release();
            data.container.removeAllViews();
        }
        participantDataMap.clear();

        localData.container.removeAllViews();

        localData.container = createParticipantContainer();
        videoLinearLayout.removeAllViews();

        disposeConversation();
        localMedia = null;
        outgoingInvite = null;

        audioState = AudioState.ENABLED;
        setAudioStateIcon();
        videoState = VideoState.ENABLED;
        setVideoStateIcon();
        pauseActionFab.setImageDrawable(
                ContextCompat.getDrawable(ClientActivity.this, R.drawable.ic_pause_green_24px));
        muteActionFab.setImageDrawable(
                ContextCompat.getDrawable(ClientActivity.this, R.drawable.ic_mic_green_24px));

        setSpeakerphoneOn(true);

        setCallAction();
        startPreview();
    }

    private void obtainCapabilityToken() {
        if (accessManager == null) {
            Timber.e("AccessManager is null");
            return;
        }
        SimpleSignalingUtils.getAccessToken(
                accessManager.getIdentity(), realm, new Callback<String>() {

                    @Override
                    public void success(final String capabilityToken, Response response) {
                        if (accessManager != null) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    accessManager.updateToken(capabilityToken);
                                }
                            }).start();
                        } else {
                            Timber.e("AccessManager is null");
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        Timber.e("Error fetching new capability token: " +
                                error.getLocalizedMessage());
                        conversationsClientStatusTextView
                                .setText("failure to obtain capability token");
                    }
                });

    }

    private LocalMedia createLocalMedia() {
        LocalMedia localMedia = new LocalMedia(localMediaListener());
        if (videoState != VideoState.DISABLED) {
            LocalVideoTrack videoTrack = createLocalVideoTrack(cameraCapturer);
            localMedia.addLocalVideoTrack(videoTrack);
        }
        if (audioState == AudioState.ENABLED) {
            localMedia.addMicrophone();
        } else {
            localMedia.removeMicrophone();
        }

        return localMedia;
    }

    private LocalVideoTrack createLocalVideoTrack(CameraCapturer cameraCapturer) {
        if(videoConstraints == null) {
            return new LocalVideoTrack(cameraCapturer);
        } else {
            return new LocalVideoTrack(cameraCapturer, videoConstraints);
        }
    }
    private PendingIntent getRejectPendingIntent() {
        Intent incomingCallRejectIntent = new Intent();
        incomingCallRejectIntent.setClass(this, Rebroadcaster.class);

        return PendingIntent.getBroadcast(this,
                REQUEST_CODE_REJECT_INCOMING_CALL,
                incomingCallRejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getAcceptPendingIntent() {
        Intent incomingCallAcceptIntent = new Intent(this, ClientActivity.class);
        incomingCallAcceptIntent.setAction(ACTION_ACCEPT_INCOMING_CALL);
        incomingCallAcceptIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(this,
                REQUEST_CODE_ACCEPT_INCOMING_CALL,
                incomingCallAcceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void registerRejectReceiver() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);

        if (manager != null) {
            manager.registerReceiver(rejectIncomingInviteReceiver,
                    new IntentFilter(ACTION_REJECT_INCOMING_CALL));
        }
    }

    private void unregisterRejectReceiver() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);

        if (manager != null) {
            manager.unregisterReceiver(rejectIncomingInviteReceiver);
        }
    }

    private AspectRatio getAspectRatio() {
        switch ((int)aspectRatioSpinner.getSelectedItemId()) {
            case 0:
                return new AspectRatio(0, 0);
            case 1:
                return VideoConstraints.ASPECT_RATIO_4_3;
            case 2:
                return VideoConstraints.ASPECT_RATIO_16_9;
        }
        return new AspectRatio(0, 0);
    }
}
